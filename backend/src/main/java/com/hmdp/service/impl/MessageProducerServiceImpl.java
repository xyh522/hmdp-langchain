package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.MessageProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 消息生产者服务实现
 */
@Slf4j
@Service
public class MessageProducerServiceImpl implements MessageProducerService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void sendSeckillOrderMessage(VoucherOrder voucherOrder) {
         try{
             rabbitTemplate.convertAndSend(
                     RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                     RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                     voucherOrder
             );
         }catch (Exception e){
             log.error("❌ [RabbitMQ] 秒杀订单消息发送失败: orderId={}, error={}",
                     voucherOrder.getId(), e.getMessage(), e);
              throw new RuntimeException("消息发送失败",e);
         }
    }
}