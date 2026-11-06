package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.cache.SeckillVoucherListLocalCache;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class AdminManagementService {
    @Resource private IShopService shopService;
    @Resource private IShopTypeService shopTypeService;
    @Resource private IVoucherService voucherService;
    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private VoucherOrderMapper orderMapper;
    @Resource private StringRedisTemplate redis;
    @Resource private SeckillVoucherListLocalCache seckillVoucherListLocalCache;

    public Result updateShop(Long id, Shop input) {
        Shop old = id == null || id <= 0 ? null : shopService.getById(id);
        if (old == null) return Result.fail("店铺不存在");
        if (input.getName() == null || input.getName().trim().isEmpty()
                || input.getAddress() == null || input.getAddress().trim().isEmpty()
                || input.getImages() == null || input.getImages().trim().isEmpty()
                || input.getTypeId() == null || input.getTypeId() <= 0
                || input.getX() == null || input.getY() == null
                || input.getX() < 0 || input.getY() < 0
                || input.getAvgPrice() == null || input.getAvgPrice() < 0) {
            return Result.fail("店铺名称、分类、图片、地址、坐标或人均价格无效");
        }
        if (shopTypeService.getById(input.getTypeId()) == null) return Result.fail("店铺分类不存在");
        Shop update = new Shop();
        update.setId(id);
        update.setName(input.getName().trim());
        update.setAddress(input.getAddress().trim());
        update.setArea(input.getArea());
        update.setImages(input.getImages());
        update.setOpenHours(input.getOpenHours());
        update.setAvgPrice(input.getAvgPrice());
        update.setTypeId(input.getTypeId());
        update.setX(input.getX());
        update.setY(input.getY());
        Result result = shopService.update(update);
        if (Boolean.TRUE.equals(result.getSuccess())) {
            redis.delete(RedisConstants.SHOP_GEO_KEY + old.getTypeId());
            redis.delete(RedisConstants.SHOP_GEO_KEY + update.getTypeId());
        }
        return result;
    }

    @Transactional
    public Result addVoucher(Long shopId, Voucher voucher) {
        if (shopId == null || shopService.getById(shopId) == null) return Result.fail("店铺不存在");
        if (voucher.getTitle() == null || voucher.getTitle().trim().isEmpty()
                || voucher.getPayValue() == null || voucher.getPayValue() < 0
                || voucher.getActualValue() == null || voucher.getActualValue() <= 0
                || voucher.getPayValue() >= voucher.getActualValue()) {
            return Result.fail("优惠券标题或金额无效");
        }
        if (voucher.getType() == null || (voucher.getType() != 0 && voucher.getType() != 1)) {
            return Result.fail("优惠券类型无效");
        }
        voucher.setId(null);
        voucher.setShopId(shopId);
        voucher.setStatus(1);
        voucher.setTitle(voucher.getTitle().trim());
        if (voucher.getType() == 1) {
            if (voucher.getStock() == null || voucher.getStock() <= 0
                    || voucher.getBeginTime() == null || voucher.getEndTime() == null
                    || !voucher.getEndTime().isAfter(voucher.getBeginTime())
                    || !voucher.getEndTime().isAfter(LocalDateTime.now())) {
                return Result.fail("秒杀库存或有效时间无效");
            }
            voucherService.addSeckillVoucher(voucher);
        } else {
            voucherService.save(voucher);
        }
        return Result.ok(voucher.getId());
    }

    @Transactional
    public Result deleteVoucher(Long shopId, Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || !shopId.equals(voucher.getShopId())) return Result.fail("优惠券不存在");
        if (orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hmdp.entity.VoucherOrder>()
                .eq("voucher_id", voucherId)) > 0) return Result.fail("优惠券已有订单，不能删除");
        if (voucher.getType() != null && voucher.getType() == 1) {
            seckillVoucherService.removeById(voucherId);
        }
        voucherService.removeById(voucherId);
        if (voucher.getType() != null && voucher.getType() == 1) {
            redis.delete(RedisConstants.SECKILL_STOCK_KEY + voucherId);
            // 数据库提交成功后再清理本机秒杀列表缓存。
            seckillVoucherListLocalCache.invalidateAfterCommit(shopId, voucherId);
        }
        return Result.ok();
    }
}
