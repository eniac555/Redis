package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    /**
     * 起始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1679875200L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;


    //注入redis用于生成自增长序列号
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //获取当前日期，就是每天都刷新，避免超出32比特位上限，便于统计
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //设置自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date, 1);

        //3.拼接并返回
        return timeStamp << COUNT_BITS | count;
    }

    /**
     * 获取起始时间的秒数值
     * @param args
     */
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.
                of(2023, 3, 27, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
