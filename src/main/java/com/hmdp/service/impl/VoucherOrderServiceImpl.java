package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */


@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    //加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //创建线程用来执行后续的下单任务
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct//在当前类--VoucherOrderHandler--初始化完毕后立即执行
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //创建线程任务
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断获取锁是否 成功
        if (!isLock) {
            //不成功
            log.error("不允许重复下单");
        }
        //获取锁成功
        try {
            proxy.createVoucherOrder2(voucherOrder);
        } finally {
            //lock.unLock();
            lock.unlock();
        }
    }

    //秒杀优惠券 ----基础版

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//        //3.判断秒杀是否结束
//        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
//            //已经结束
//            return Result.fail("秒杀已经结束");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        //=======释放锁的时机和事务处理之间的复杂联系=======
//        //集群模式下锁不住诶，多个服务时每个服务都会有一个服务内可用的锁
//        synchronized (userId.toString().intern()) {
//            //获取代理对象（和事务有关）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //加了悲观锁，锁是userId
//            return proxy.createVoucherOrder(voucherId);
//        }
//    }


    //秒杀优惠券 ----分布式锁--基础版的分布式锁
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//        //3.判断秒杀是否结束
//        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
//            //已经结束
//            return Result.fail("秒杀已经结束");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//
//        // todo
//        RLock lock = redissonClient.getLock("order:" + userId);//使用redisson创建锁对象
//        boolean isLock = lock.tryLock();
//
//        //获取锁
//        //boolean isLock = lock.tryLock(5);
//        //判断获取锁是否 成功
//        if (!isLock) {
//            //失败，返回错误信息或者重试，这里应该直接失败
//            return Result.fail("一人仅允许下一单");
//        }
//        //获取锁成功
//        try {
//            //获取代理对象（和事务有关）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //加了悲观锁，锁是userId
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //lock.unLock();
//            lock.unlock();
//        }
//
//    }


    private IVoucherOrderService proxy;

    //秒杀优惠券---lua脚本改进版
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();

        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        //2.判断结果为0
        int r = result.intValue();
        if (r != 0) {
            //2.1 判断结果不为0，没有资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2 判断结果为0，有资格，保存下单信息到阻塞队列

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.4用户id
        voucherOrder.setUserId(userId);
        //2.5代金券id
        voucherOrder.setVoucherId(voucherId);
        //2.6创建阻塞队列
        orderTasks.add(voucherOrder);
        //3.获取代理对象（和事务有关）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //todo
        //3.返回订单id
        return Result.ok(orderId);


    }

    //判断是否一人一单并创建订单
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



    //阻塞队列后续处理过程用的函数方法
    @Transactional//有订单表和优惠券表的共同操作，最好加上事务控制
    public void createVoucherOrder2(VoucherOrder voucherOrder) {
        //5.一人一单判断
        Long userId = voucherOrder.getId();
        //5.1查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2判断是否存在
        if (count > 0) {
            //用户已经购买过
            log.error("已经购买过");
        }
        //6.扣减库存++++乐观锁---CAS
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//sql语句  set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                //where条件  id = ? and stock = ?
                .update();
        if (!success) {
            log.error("库存不足");
        }
        //7.创建订单
        save(voucherOrder);
    }
}
