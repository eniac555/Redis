package com.hmdp.rabbitmq;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 死信队列的普通消费者
 * 用来消费正常的消息
 */

public class ConsumerMQ01 {

    /*@Resource
    private ISeckillVoucherService seckillVoucherService;

    @RabbitListener(queues = "normal_queue")
    public void receiveNormal(Message message){
        //获取队列中的消息
        String msg = new String(message.getBody());
        //把消息转成对用的类对象
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        //扣减库存
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId)
                .gt("stock", 0)
                .update();
        //保存订单
        save(voucherOrder);//这里用不了
    }*/

}
