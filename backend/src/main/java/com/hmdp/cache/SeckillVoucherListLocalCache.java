package com.hmdp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.hmdp.dto.SeckillVoucherDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.Arrays;

import static com.hmdp.utils.RedisConstants.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.hmdp.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** 店铺秒杀券列表的本地缓存，以及写入后的 Redis、本地缓存失效处理。 */
@Slf4j
@Component
public class SeckillVoucherListLocalCache {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private final Cache<Long, List<SeckillVoucherDTO>> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfter(new Expiry<Long, List<SeckillVoucherDTO>>() {
                @Override
                public long expireAfterCreate(Long key, List<SeckillVoucherDTO> value, long currentTime) {
                    return remainingNanos(value);
                }

                @Override
                public long expireAfterUpdate(Long key, List<SeckillVoucherDTO> value,
                                              long currentTime, long currentDuration) {
                    return remainingNanos(value);
                }

                @Override
                public long expireAfterRead(Long key, List<SeckillVoucherDTO> value,
                                            long currentTime, long currentDuration) {
                    return currentDuration;
                }
            }).build();

    public List<SeckillVoucherDTO> get(Long shopId,
                                      Function<Long, List<SeckillVoucherDTO>> loader) {
        Objects.requireNonNull(shopId, "shopId不能为空");
        Objects.requireNonNull(loader, "loader不能为空");
        return cache.get(shopId, loader);
    }

    public void invalidate(Long shopId) {
        cache.invalidate(Objects.requireNonNull(shopId, "shopId不能为空"));
    }

    public void invalidateAfterCommit(Long shopId, Long voucherId) {
        Objects.requireNonNull(shopId, "shopId不能为空");
        Objects.requireNonNull(voucherId, "voucherId不能为空");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateNow(shopId, voucherId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateNow(shopId, voucherId);
            }
        });
    }
    /**
     * 【实际发送消息的位置】
     * 消息体：voucherId；消息头：shopId。
     */
    private void publishInvalidation(Long shopId, Long voucherId) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_CACHE_EXCHANGE,
                    "",
                    voucherId,
                    message -> {
                        message.getMessageProperties()
                                .setHeader("shopId", shopId);
                        return message;
                    }
            );
        } catch (RuntimeException e) {
            log.error(
                    "缓存失效广播发送失败，shopId={}，voucherId={}",
                    shopId,
                    voucherId,
                    e
            );
            throw e;
        }
    }

    /**
     * 实际清理：
     * 1. Redis 店铺券 ID 索引。
     * 2. Redis 对应券的详情。
     * 3. 当前 JVM 的 Caffeine 列表。
     *
     * 消费者调用此方法，不会再次广播。
     */

    public void invalidateNow(Long shopId, Long voucherId) {
        // 与列表重建使用同一把店铺锁，避免旧查询在删除后重新写回 Redis。
        RLock lock = redissonClient.getLock(LOCK_SECKILL_VOUCHER_LIST_KEY + shopId);
        lock.lock();
        try {
            stringRedisTemplate.delete(Arrays.asList(
                    SECKILL_VOUCHER_LIST_KEY + shopId,
                    SECKILL_VOUCHER_KEY + voucherId));
        } finally {
            // 先释放分布式锁再清本地，避免与 Caffeine 的加载计算形成锁顺序冲突。
            try {
                lock.unlock();
            } finally {
                invalidate(shopId);
            }
        }
    }

    private static long remainingNanos(List<SeckillVoucherDTO> vouchers) {
        if (vouchers.isEmpty()) {
            return TimeUnit.SECONDS.toNanos(60);
        }
        LocalDateTime earliestEnd = vouchers.stream()
                .map(SeckillVoucherDTO::getEndTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        if (earliestEnd == null) {
            return 0L;
        }
        Duration remaining = Duration.between(LocalDateTime.now(), earliestEnd);
        if (remaining.isZero() || remaining.isNegative()) {
            return 0L;
        }
        try {
            return remaining.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}