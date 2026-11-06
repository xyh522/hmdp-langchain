package com.hmdp.config;

import io.lettuce.core.protocol.ConnectionFacade;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
@Configuration
public class RabbitMQConfig {
    // 秒杀订单相关常量
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    // 死信队列相关常量
    public static final String SECKILL_ORDER_DLX_EXCHANGE = "seckill.order.dlx.exchange";
    public static final String SECKILL_ORDER_DLX_QUEUE = "seckill.order.dlx.queue";
    public static final String SECKILL_ORDER_DLX_ROUTING_KEY = "seckill.order.dlx";

    public static final String SECKILL_CACHE_EXCHANGE = "seckill.cache.invalidation.exchange";
    /**
     * 消息转换器 - 使用JSON格式
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
    /**
     * 监听器容器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connectionFactory);
        factory.setMessageConverter(messageConverter());

        return factory;
    }

    /**
     * 秒杀订单交换机
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        //定义一个持久化的、非自动删除的直连交换机
        return new DirectExchange(SECKILL_ORDER_EXCHANGE, true, false);
    }
    /**
     * 秒杀订单队列
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_ORDER_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定秒杀订单队列到交换机
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    // ==================== 死信队列配置 ====================

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange seckillOrderDlxExchange() {
        return new DirectExchange(SECKILL_ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DLX_QUEUE).build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding seckillOrderDlxBinding() {
        return BindingBuilder.bind(seckillOrderDlxQueue())
                .to(seckillOrderDlxExchange())
                .with(SECKILL_ORDER_DLX_ROUTING_KEY);
    }


    @Bean
    public FanoutExchange seckillCacheExchange() {
        return new FanoutExchange(SECKILL_CACHE_EXCHANGE, true, false);
    }

    @Bean
    public AnonymousQueue seckillCacheQueue() {
        AnonymousQueue queue = new AnonymousQueue();

        // 兼容当前 RabbitMQ：移除旧版 Spring AMQP 默认添加的弃用参数。
        queue.setMasterLocator(null);

        return queue;
    }

    @Bean
    public Binding seckillCacheBinding() {
        return BindingBuilder.bind(seckillCacheQueue())
                .to(seckillCacheExchange());
    }


}
