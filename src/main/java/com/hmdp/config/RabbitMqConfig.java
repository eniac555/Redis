package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息队列-死信队列-配置类
 */
@Configuration
public class RabbitMqConfig {

    //普通交换机
    public static final String NORMAL_EXCHANGE_NAME = "normal_exchange";
    //死信交换机
    public static final String DEAD_EXCHANGE_NAME = "dead_exchange";
    //普通队列
    public static final String NORMAL_QUEUE_NAME = "normal_queue";
    //死信队列
    public static final String DEAD_QUEUE_NAME = "dead_queue";


    //声明普通交换机   xExchange别名
    @Bean("xExchange")
    public DirectExchange xExchange() {
        return new DirectExchange(NORMAL_EXCHANGE_NAME);
    }


    //声明死信交换机   yExchange别名
    @Bean("yExchange")
    public DirectExchange yExchange() {
        return new DirectExchange(DEAD_EXCHANGE_NAME);
    }

    //声明普通队列（死信交换机同时绑定了死信队列）
    @Bean("queueA")
    public Queue queueA() {
        Map<String, Object> arguments = new HashMap<>(3);
        //过期时间
        arguments.put("x-message-ttl", 10000);
        //设置死信交换机，消息成为死信之后转发到对应的死信交换机
        arguments.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);//DLX:表示是死信交换机
        //设置死信routingKey，死信交换机发送消息到死信队列
        //没有死信交换机，也就没有所谓的死信队列，两者还要通过路由键绑定在一起。
        //=======这一步必不可少=======
        arguments.put("x-dead-letter-routing-key", "dead_exchange_to_dead_queue");//DLK：表示设置了死信路由key
        return QueueBuilder.durable(NORMAL_QUEUE_NAME).withArguments(arguments).build();
    }


    //声明死信队列
    @Bean("deadQueue")
    public Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE_NAME).build();
    }

    //绑定普通队列和普通交换机
    @Bean
    public Binding NormalQueueToNormalExchange(@Qualifier("xExchange") DirectExchange exchange,
                                               @Qualifier("queueA") Queue queue){
        return BindingBuilder.bind(queue).to(exchange).with("normal_exchange_to_normal_queue");
    }

    //绑定死信队列和死信交换机
    @Bean
    public Binding DeadQueueToDeadExchange(@Qualifier("yExchange") DirectExchange exchange,
                                           @Qualifier("deadQueue") Queue queue){
        return BindingBuilder.bind(queue).to(exchange).with("dead_exchange_to_dead_queue");
        //本以为和前面声明普通队列时设置死信路由key是重复的，但是好像都不能少
    }




}
