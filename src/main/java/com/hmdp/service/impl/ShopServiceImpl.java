package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    //注入工具类


    //利用redis缓存保存商户信息
    //设置超时时间
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop1 = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,
                id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期时间解决缓存击穿
        Shop shop2 = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,
                id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = queryWithLogicExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在~");
        }

        //7.从redis里取出商户信息并返回
        return Result.ok(shop);
    }


    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR =
            Executors.newFixedThreadPool(10);


    //解决缓存击穿的代码----逻辑过期----简化了，不考虑存空值
    public Shop queryWithLogicExpire(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断商户是否在redis中
        if (StrUtil.isBlank(shopJson)) {//非空有多种情况：null  ""   " "  "\t \n \f \r"  都表示空
            //3.未命中直接返回
            return null;
        }
        //4.命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回店铺信息
            return shop;
        }
        //5.2已过期，缓存重建

        //6.缓存重建
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取锁是否成功
        if (isLock){
            //6.3 成功，再开启一个线程，实现缓存重建
            //建立了线程池，用submit提交一个任务
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //6.4 失败，返回已经过期的商铺信息
        return shop;
    }


    //先向redis写入带有逻辑过期时间的数据的代码
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装成带有逻辑过期时间的数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }



    //解决缓存击穿的代码----互斥锁
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断商户是否在redis中
        if (StrUtil.isNotBlank(shopJson)) {//非空有多种情况：null  ""   " "  "\t \n \f \r"  都表示空
            //3.存在直接返回
            //转成对象返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if (shopJson!=null){//查不到对象表示null，查到空对象是空字符串之类
            //返回一个错误信息
            return null;
        }

        //4.开始实现缓存重建
        //4.1 获取互斥锁
        String lockKey = "lock:shop:"+id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断互斥锁获取是否成功
            if(!isLock){
                //4.3 失败，休眠一段时间，并重试
                Thread.sleep(50);
                return queryWithMutex(id);//递归
            }

            //4.4 成功，根据id查询数据库
            shop = getById(id);
            //模拟重建时间较长...
            Thread.sleep(200);
            //5.数据库里不存在，返回错误
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue()
                        .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//2分钟
                return null;
            }

            //6.存在，把查询到的商户信息写入redis  并设置超时时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//30分钟

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7. 释放互斥锁
            unLock(lockKey);
        }
        //8.从redis里取出商户信息并返回
        return shop;
    }


    //尝试获取互斥锁的代码
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);//10秒没有释放自动过期
        return BooleanUtil.isTrue(flag);
    }


    //释放锁的代码
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    //解决缓存穿透的代码
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断商户是否在redis中
        if (StrUtil.isNotBlank(shopJson)) {//非空有多种情况：null  ""   " "  "\t \n \f \r"  都表示空
            //3.存在直接返回
            //转成对象返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断命中的是否是空值
        if (shopJson!=null){//查不到对象表示null，查到空对象是空字符串之类
            //返回一个错误信息
            return null;
        }

        //4.不存在，进而查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //4.1 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//2分钟
            //5.数据库里不存在，返回错误
            return null;
        }

        //6.存在，把查询到的商户信息写入redis  并设置超时时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//30分钟
        //7.从redis里取出商户信息并返回
        return shop;
    }


    //设置店铺缓存的redis更新
    @Override
    @Transactional//删除缓存时抛出异常，前面的事务需要回滚
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("店铺id不能为空~");
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
        //建议采用这种先更新数据库再删除缓存的方式
    }
}
