package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/**
 * 封装的redis工具类
 */


@Slf4j
@Component
public class CacheClient {

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR =
            Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }


    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断商户是否在redis中
        if (StrUtil.isNotBlank(json)) {//非空有多种情况：null  ""   " "  "\t \n \f \r"  都表示空
            //3.存在直接返回
            //转成对象返回
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否是空值
        if (json!=null){//查不到对象表示null，查到空对象是空字符串之类
            //返回一个错误信息
            return null;
        }
        //4.不存在，进而查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            //4.1 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//2分钟
            //5.数据库里不存在，返回错误
            return null;
        }
        //6.存在，把查询到的商户信息写入redis  并设置超时时间
        this.set(key,r,time,unit);
        //7.从redis里取出商户信息并返回
        return r;
    }



    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题将逻辑进行封装
    public <R, ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,
                                          Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断商户是否在redis中
        if (StrUtil.isBlank(json)) {//非空有多种情况：null  ""   " "  "\t \n \f \r"  都表示空
            //3.未命中直接返回
            return null;
        }
        //4.命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回店铺信息
            return r;
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
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.set(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //6.4 失败，返回已经过期的商铺信息
        return r;
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



}
