package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime;//逻辑过期 过期时间
    private Object data;//想存进redis的数据

    //这么一操作，存进去的包括了原本应该存进去的数据+逻辑过期时间
}
