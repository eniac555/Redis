package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    //LoginInterceptor是自己创建的，不是spring管理的，不能用resource或者autowire
    //用构造函数注入
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //todo 1.获取请求头中的token
        String token = request.getHeader("authorization");
        //todo 2.基于token获取redis中的用户
        if(StrUtil.isBlank(token)){//判断token是否存在
            //不存在，也不进行拦截，进入到下一个拦截器
            //response.setStatus(401);
            return true;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY+token);
        //3.判断map是否为空
        if (userMap.isEmpty()) {
            //4.不存在，也不进行拦截，进入到下一个拦截器
            //response.setStatus(401);
            return true;
        }
        //todo 5.把hash数据转换为userDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //todo 6.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);
        //todo 7.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//30 minutes
        //todo 8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();

    }
}
