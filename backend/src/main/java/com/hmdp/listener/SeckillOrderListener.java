package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.MessageProducerService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单消息监听器（消费者）
 */
@Slf4j
@Component
public class SeckillOrderListener {

    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(VoucherOrder voucherOrder, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (isMessageAlreadyProcessed(voucherOrder.getId())) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            handleVoucherOrder(voucherOrder);

            markMessageAsProcessed(voucherOrder.getId());

            channel.basicAck(deliveryTag, false);//手动确认消息

        } catch (Exception e) {
            log.error("订单处理原始异常: orderId={}, userId={}, voucherId={}, deliveryTag={}",
                    voucherOrder.getId(),
                    voucherOrder.getUserId(),
                    voucherOrder.getVoucherId(),
                    deliveryTag,
                    e);
            try {
                // 获取重试次数
                Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
                if (retryCount == null) {
                    retryCount = 0;
                }

                if (retryCount < 3) {
                    // 重试次数未达到上限，拒绝消息并重新入队
                    log.info("🔄 [RabbitMQ] 消息重试: orderId={}, retryCount={}, deliveryTag={}",
                            voucherOrder.getId(), retryCount + 1, deliveryTag);
                    channel.basicNack(deliveryTag, false, true);//拒绝单个消息并重新入队
                } else {
                    // 重试次数达到上限，拒绝消息并发送到死信队列
                    log.error("💀 [RabbitMQ] 消息处理失败，发送到死信队列: orderId={}, deliveryTag={}",
                            voucherOrder.getId(), deliveryTag);
                    channel.basicNack(deliveryTag, false, false);//拒绝单个消息并发送到死信队列
                }
            } catch (IOException ioException) {
                log.error("❌ [RabbitMQ] 消息确认失败: orderId={}, deliveryTag={}, error={}",
                        voucherOrder.getId(), deliveryTag, ioException.getMessage());
                // 通道可能已关闭，不再尝试确认
            }
        }
    }
    /**
     * 处理优惠券订单（加锁防止重复处理）
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        // 创建锁对象（防止消息重复处理）
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        try {
            // 获取锁
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.warn("⚠️ [RabbitMQ] 获取锁失败，可能存在重复消息: userId={}, orderId={}",
                        userId, voucherOrder.getId());
                throw new RuntimeException("获取锁失败");
            }

            // 保存订单到数据库
            voucherOrderService.createVoucherOrder(voucherOrder);

        } catch(Exception e){
            log.error("处理订单写入数据库异常");
        }
        finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    /**
     * 处理死信队列消息
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DLX_QUEUE)
    public void handleDeadLetterMessage(VoucherOrder voucherOrder, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.error("💀 [RabbitMQ] 处理死信消息: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());

            // 这里可以进行特殊处理，比如：
            // 1. 记录到数据库
            // 2. 发送告警
            // 3. 人工介入处理

            // 确认死信消息
            channel.basicAck(deliveryTag, false);//告知 RabbitMQ 消息已被“处理,可以从队列中删除

        } catch (Exception e) {
            log.error("❌ [RabbitMQ] 死信消息处理失败: orderId={}, error={}",
                    voucherOrder.getId(), e.getMessage(), e);
            // 死信消息处理失败，直接确认（避免无限循环）
            channel.basicAck(deliveryTag, false);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_QUEUE, ackMode = "AUTO")
    public void handleOrderTimeout(Long orderId) {
        voucherOrderService.closeUnpaidOrder(orderId);
    }

    /**
     * 检查消息是否已被处理
     */
    private boolean isMessageAlreadyProcessed(Long orderId) {
        String key = "processed:order:" + orderId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 标记消息已处理
     */
    private void markMessageAsProcessed(Long orderId) {
        String key = "processed:order:" + orderId;
        // 设置过期时间为1小时，防止Redis内存占用过多
        stringRedisTemplate.opsForValue().set(key, "1", 1, TimeUnit.HOURS);
    }

}
