package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.hmdp.dto.Result;

import com.hmdp.dto.UserDTO;

import com.hmdp.entity.SeckillVoucher;

import com.hmdp.entity.VoucherOrder;

import com.hmdp.mapper.VoucherOrderMapper;

import com.hmdp.service.ISeckillVoucherService;

import com.hmdp.service.IVoucherOrderService;

import com.hmdp.service.MessageProducerService;

import com.hmdp.utils.RedisIdWorker;

import com.hmdp.utils.UserHolder;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;

import org.springframework.core.io.ClassPathResource;

import org.springframework.data.redis.connection.stream.*;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

import javax.annotation.Resource;

import java.time.Duration;

import java.time.LocalDateTime;

import com.hmdp.config.RabbitMQConfig;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.transaction.support.TransactionSynchronization;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;

import com.hmdp.entity.Voucher;

import com.hmdp.mapper.VoucherMapper;

@Service

@Slf4j

public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource

    private ISeckillVoucherService seckillVoucherService;

    @Resource

    private RedisIdWorker redisIdWorker;

    @Resource

    private RedissonClient redissonClient;

    @Resource

    private StringRedisTemplate stringRedisTemplate;

    @Resource

    private MessageProducerService messageProducerService;

    /**

     * 自己注入自己为了获取代理对象 @Lazy 延迟注入 避免形成循环依赖

     */

    @Resource

    @Lazy

    private IVoucherOrderService voucherOrderService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    @Resource

    private VoucherMapper voucherMapper;

    @Resource

    private RabbitTemplate rabbitTemplate;

    //加载lua脚本

    static {

        SECKILL_SCRIPT = new DefaultRedisScript<>();

        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));

        SECKILL_SCRIPT.setResultType(Long.class);

    }

    @Override

    public Result seckillVoucher(Long voucherId) {

        try {

            //获取用户

            UserDTO user = UserHolder.getUser();

            if (user == null) {

                return Result.fail("用户未登录");

            }

         //获取订单id

            Long orderId = redisIdWorker.nextId("order");

            System.out.println("🎯 [秒杀] 开始处理秒杀请求: voucherId=" + voucherId + ", userId=" + user.getId() + ", orderId=" + orderId);

            //执行lua脚本 - 传递3个参数（voucherId, userId, orderId）
            Long res = stringRedisTemplate.execute(

                    SECKILL_SCRIPT

                    , Collections.emptyList()

                    , voucherId.toString()      // ARGV[1] - voucherId

                    , user.getId().toString()   // ARGV[2] - userId

                    , orderId.toString());      // ARGV[3] - orderId

            if (res == null) {

                System.out.println("❌ [秒杀] Lua脚本执行返回null");

                return Result.fail("系统繁忙，请稍后重试");

            }

            //判断结果是否为0

            int r = res.intValue();

            System.out.println("📊 [秒杀] Lua脚本执行结果: " + r);

            if (r != 0) {

                //不为0 没有购买资格

                String errorMsg;

                switch (r) {

                    case 1:

                        errorMsg = "库存不足";

                        break;

                    case 2:

                        errorMsg = "禁止重复下单";

                        break;

                    case 3:

                        errorMsg = "秒杀活动未开始或已结束";

                        break;

                    case 4:

                        errorMsg = "秒杀券不存在或库存未初始化";

                        break;

                    default:

                        errorMsg = "秒杀失败，未知错误";

                }

                System.out.println("❌ [秒杀] 秒杀失败: " + errorMsg);

                return Result.fail(errorMsg);

            }

            //为0有购买资格，创建订单并发送到RabbitMQ

            VoucherOrder voucherOrder = new VoucherOrder();

            voucherOrder.setVoucherId(voucherId);

            voucherOrder.setUserId(user.getId());

            voucherOrder.setId(orderId);

            System.out.println("✅ [秒杀] 秒杀资格验证通过，准备发送消息到RabbitMQ");

            //发送消息到RabbitMQ

            messageProducerService.sendSeckillOrderMessage(voucherOrder);

            System.out.println("🎉 [秒杀] 秒杀请求处理完成: orderId=" + orderId);

            //返回订单id

            return Result.ok(orderId.toString());

        } catch (Exception e) {

            System.out.println("❌ [秒杀] 秒杀处理异常: " + e.getMessage());

            e.printStackTrace();

            return Result.fail("系统异常，请稍后重试");

        }

    }

    @Override

    @Transactional(rollbackFor = Exception.class)

    //创建优惠券订单

    public void createVoucherOrder(VoucherOrder voucherOrder) {

        voucherOrder.setStatus(1);

        voucherOrder.setCreateTime(LocalDateTime.now());

        this.save(voucherOrder);

        sendTimeoutAfterCommit(voucherOrder.getId());

    }

    @Override

    @Transactional(rollbackFor = Exception.class)

    public Result buyOrdinaryVoucher(Long voucherId) {

        UserDTO user = UserHolder.getUser();

        if (user == null) {

            return Result.fail("用户未登录");

        }

        if (voucherId == null || voucherId <= 0) {

            return Result.fail("优惠券ID不合法");

        }

        // 根据数据库判断券类型和上架状态。

        Voucher voucher = voucherMapper.selectById(voucherId);

        if (voucher == null) {

            return Result.fail("优惠券不存在");

        }

        if (!Integer.valueOf(0).equals(voucher.getType())) {

            return Result.fail("该优惠券不是普通券");

        }

        if (!Integer.valueOf(1).equals(voucher.getStatus())) {

            return Result.fail("优惠券已下架");

        }

        long orderId = redisIdWorker.nextId("order");

        VoucherOrder order = new VoucherOrder();

        order.setId(orderId);

        order.setUserId(user.getId());

        order.setVoucherId(voucherId);

        order.setStatus(1); // 待支付，不代表付款成功。

        order.setCreateTime(LocalDateTime.now());

        if (!save(order)) {

            throw new IllegalStateException("创建普通券订单失败");

        }

        sendTimeoutAfterCommit(orderId);

        return Result.ok(String.valueOf(orderId));

    }

    private void sendTimeoutAfterCommit(Long orderId) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override

            public void afterCommit() {

                rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_EXCHANGE, "order.wait.1m", orderId);

            }

        });

    }

    @Override

    @Transactional(rollbackFor = Exception.class)
    public Result payOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null || !UserHolder.getUser().getId().equals(order.getUserId())) {
            return Result.fail("订单不存在");
        }
        Voucher voucher = voucherMapper.selectById(order.getVoucherId());
        if (voucher == null) return Result.fail("优惠券不存在");

        // 模拟支付：状态更新和秒杀券数据库扣库存使用同一个事务。
        boolean paid = update(new UpdateWrapper<VoucherOrder>()
                .eq("id", orderId)
                .eq("user_id", UserHolder.getUser().getId())
                .eq("status", 1)
                .gt("create_time", LocalDateTime.now().minusMinutes(1))
                .set("status", 2)
                .set("pay_time", LocalDateTime.now()));
        if (!paid) return Result.fail("订单已过期或不是待支付状态");

        if (Integer.valueOf(1).equals(voucher.getType())) {
            boolean deducted = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", order.getVoucherId()).gt("stock", 0)
                    .update();
            if (!deducted) {
                // 抛出异常回滚前面的支付状态，不能提交一笔未扣到库存的已支付订单。
                throw new IllegalStateException("库存不足，支付失败，voucherId=" + order.getVoucherId());
            }
        }
        return Result.ok();
    }

    @Override

    @Transactional(rollbackFor = Exception.class)

    public Result cancelOrder(Long orderId) {

        VoucherOrder order = getById(orderId);

        if (order == null || !UserHolder.getUser().getId().equals(order.getUserId())) {

            return Result.fail("订单不存在");

        }

        return cancelPending(order) ? Result.ok() : Result.fail("订单不是待支付状态");

    }

    @Override

    @Transactional(rollbackFor = Exception.class)

    public void closeUnpaidOrder(Long orderId) {

        VoucherOrder order = getById(orderId);

        if (order != null) cancelPending(order);

    }

    private boolean cancelPending(VoucherOrder order) {

        // 只有待支付变为取消成功时才释放 Redis 预占；数据库库存仅在支付时扣减。

        boolean cancelled = update(new UpdateWrapper<VoucherOrder>()

                .eq("id", order.getId())

                .eq("status", 1)

                .set("status", 4));

        if (!cancelled) return false;

        Voucher voucher = voucherMapper.selectById(order.getVoucherId());

        if (voucher != null && Integer.valueOf(1).equals(voucher.getType())) {

            stringRedisTemplate.opsForValue().increment("seckill:stock:" + order.getVoucherId());

            stringRedisTemplate.opsForSet().remove("seckill:order:" + order.getVoucherId(), order.getUserId().toString());

        }

        return true;

    }

}
