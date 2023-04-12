package com.hmdp.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.annotation.Resource;

public class ProducerMQ {
    @Resource
    private RabbitTemplate rabbitTemplate;

}
