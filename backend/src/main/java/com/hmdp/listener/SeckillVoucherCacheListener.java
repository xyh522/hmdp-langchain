package com.hmdp.listener;

import com.hmdp.cache.SeckillVoucherListLocalCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillVoucherCacheListener {

    @Resource
    private SeckillVoucherListLocalCache localCache;

    @RabbitListener(
            queues = "#{seckillCacheQueue.name}",
            ackMode = "AUTO"
    )
    public void onMessage(
            @Payload Long voucherId,
            @Header("shopId") Long shopId) {

        if (shopId == null || shopId <= 0
                || voucherId == null || voucherId <= 0) {
            throw new AmqpRejectAndDontRequeueException(
                    "缓存失效消息的shopId或voucherId无效"
            );
        }

        // 只清理缓存，不再次广播。
        // 清理失败时异常向外传播，由监听容器处理。
        localCache.invalidateNow(shopId, voucherId);

        log.debug(
                "收到缓存失效广播，shopId={}，voucherId={}",
                shopId,
                voucherId
        );
    }
}