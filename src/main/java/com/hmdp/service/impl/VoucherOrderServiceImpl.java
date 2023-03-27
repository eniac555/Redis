package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */


@Service

public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    //秒杀优惠券
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //3.判断秒杀是否结束
        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
            //已经结束
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        //=======释放锁的时机和事务处理之间的复杂联系=======
        //集群模式下锁不住诶，多个服务时每个服务都会有一个服务内可用的锁
        synchronized (userId.toString().intern()) {
            //获取代理对象（和事务有关）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //加了悲观锁，锁是userId
            return proxy.createVoucherOrder(voucherId);
        }
    }


    @Transactional//有订单表和优惠券表的共同操作，最好加上事务控制
    public Result createVoucherOrder(Long voucherId) {
        //5.一人一单判断
        Long userId = UserHolder.getUser().getId();
        //5.1查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if (count > 0) {
            //用户已经购买过
            return Result.fail("已经购买过");
        }
        //6.扣减库存++++乐观锁---CAS
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//sql语句  set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0)
                //where条件  id = ? and stock = ?
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户id
        voucherOrder.setUserId(userId);
        //7.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //写入数据库
        save(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);
    }
}
