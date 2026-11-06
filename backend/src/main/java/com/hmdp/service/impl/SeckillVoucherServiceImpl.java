package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.cache.SeckillVoucherListLocalCache;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class SeckillVoucherServiceImpl
        extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
        implements ISeckillVoucherService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private SeckillVoucherListLocalCache listLocalCache;

    @Override
    public List<SeckillVoucherDTO> querySeckillVouchersOfShop(Long shopId) {
        if (shopId == null || shopId <= 0) {
            throw new IllegalArgumentException("店铺ID不合法");
        }
        List<SeckillVoucherDTO> cached = listLocalCache.get(shopId, this::loadVoucherList);
        LocalDateTime now = LocalDateTime.now();
        // 返回副本，调用方不能改到 Caffeine 内的共享 DTO。
        return cached.stream()
                .filter(dto -> dto.getEndTime() != null && dto.getEndTime().isAfter(now))
                .map(dto -> {
                    SeckillVoucherDTO copy = new SeckillVoucherDTO();
                    BeanUtils.copyProperties(dto, copy);
                    return copy;
                }).collect(Collectors.toList());
    }

    private List<SeckillVoucherDTO> loadVoucherList(Long shopId) {
        // 仅本机一级缓存未命中才进入这里。按店铺串行组装/重建二级缓存。
        RLock lock = redissonClient.getLock(LOCK_SECKILL_VOUCHER_LIST_KEY + shopId);
        lock.lock();
        try {
            String indexKey = SECKILL_VOUCHER_LIST_KEY + shopId;
            String indexJson = stringRedisTemplate.opsForValue().get(indexKey);
            if (indexJson != null && !indexJson.trim().isEmpty()) {
                List<Long> voucherIds = parseIndex(indexJson, shopId);
                if (voucherIds != null) {
                    if (voucherIds.isEmpty()) {
                        log.debug("命中 Redis 空列表，shopId={}", shopId);
                        return Collections.emptyList();
                    }
                    List<String> detailKeys = voucherIds.stream()
                            .map(id -> SECKILL_VOUCHER_KEY + id)
                            .collect(Collectors.toList());
                    List<String> detailJsons = stringRedisTemplate.opsForValue().multiGet(detailKeys);
                    List<SeckillVoucherDTO> vouchers = decodeDetails(shopId, voucherIds, detailJsons);
                    if (vouchers != null) {
                        log.debug("命中 Redis 索引与券信息，shopId={}", shopId);
                        return Collections.unmodifiableList(vouchers);
                    }
                }
            }

            // 索引缺失，或任一券详情缺失/过期/格式旧：一次联表查询重建完整列表。
            log.debug("秒杀列表回源数据库，shopId={}", shopId);
            LocalDateTime now = LocalDateTime.now();
            List<Voucher> databaseVouchers = voucherMapper.querySeckillVouchersOfShop(shopId);
            List<SeckillVoucherDTO> vouchers = databaseVouchers.stream()
                    .filter(voucher -> voucher.getEndTime() != null && voucher.getEndTime().isAfter(now))
                    .map(voucher -> {
                        SeckillVoucherDTO dto = new SeckillVoucherDTO();
                        BeanUtils.copyProperties(voucher, dto);
                        dto.setVoucherId(voucher.getId());
                        return dto;
                    }).collect(Collectors.toList());

            // 每张券独立写入完整 JSON，TTL 分别计算到自己的截止时间。
            for (SeckillVoucherDTO voucher : vouchers) {
                long ttlMillis = Duration.between(LocalDateTime.now(), voucher.getEndTime()).toMillis();
                if (ttlMillis > 0) {
                    stringRedisTemplate.opsForValue().set(
                            SECKILL_VOUCHER_KEY + voucher.getVoucherId(),
                            JSONUtil.toJsonStr(voucher), ttlMillis, TimeUnit.MILLISECONDS);
                }
            }

            // 先写详情，最后发布索引。索引保存 ID，最长保留到最后一张券截止。
            List<Long> voucherIds = vouchers.stream()
                    .map(SeckillVoucherDTO::getVoucherId).collect(Collectors.toList());
            LocalDateTime latestEnd = vouchers.stream().map(SeckillVoucherDTO::getEndTime)
                    .max(LocalDateTime::compareTo).orElse(null);
            long indexTtlMillis = latestEnd == null
                    ? 60_000L
                    : Math.max(1L, Duration.between(LocalDateTime.now(), latestEnd).toMillis());
            stringRedisTemplate.opsForValue().set(
                    indexKey, JSONUtil.toJsonStr(voucherIds), indexTtlMillis, TimeUnit.MILLISECONDS);
            return Collections.unmodifiableList(vouchers);
        } finally {
            lock.unlock();
        }
    }

    private List<Long> parseIndex(String json, Long shopId) {
        try {
            List<Long> ids = JSONUtil.toList(JSONUtil.parseArray(json), Long.class);
            if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
                return null;
            }
            return ids;
        } catch (RuntimeException e) {
            log.warn("Redis 秒杀券索引格式异常，将回源重建，shopId={}", shopId);
            return null;
        }
    }

    private List<SeckillVoucherDTO> decodeDetails(
            Long shopId, List<Long> ids, List<String> jsons) {
        if (jsons == null || jsons.size() != ids.size()) {
            return null;
        }
        List<SeckillVoucherDTO> vouchers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        try {
            for (int i = 0; i < ids.size(); i++) {
                String json = jsons.get(i);
                if (json == null || json.trim().isEmpty()) {
                    return null;
                }
                SeckillVoucherDTO dto = JSONUtil.toBean(json, SeckillVoucherDTO.class);
                // 拒绝旧版只包含 ID/时间的 DTO，必须重新查询完整信息。
                if (dto == null || !ids.get(i).equals(dto.getId())
                        || !ids.get(i).equals(dto.getVoucherId())
                        || !shopId.equals(dto.getShopId())
                        || dto.getTitle() == null
                        || dto.getPayValue() == null || dto.getActualValue() == null
                        || !Integer.valueOf(1).equals(dto.getType())
                        || !Integer.valueOf(1).equals(dto.getStatus())
                        || dto.getBeginTime() == null || dto.getEndTime() == null
                        || !dto.getEndTime().isAfter(dto.getBeginTime())
                        || !dto.getEndTime().isAfter(now)) {
                    return null;
                }
                vouchers.add(dto);
            }
            return vouchers;
        } catch (RuntimeException e) {
            log.warn("Redis 秒杀券详情格式异常，将回源重建，shopId={}", shopId);
            return null;
        }
    }
}