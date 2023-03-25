package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       /* //1.获取session
        HttpSession session = request.getSession();
        //2.获取session中的用户
        Object user = session.getAttribute("user");
        //3.判断用户是否存在
        if (user == null) {
            //4.不存在，进行拦截，返回401状态码（未授权）
            response.setStatus(401);
            return false;
        }
        //5.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser((UserDTO) user);*/

        /**
         * 这部分放在第一层拦截器了
         */
//        //todo 1.获取请求头中的token
//        String token = request.getHeader("authorization");
//        //todo 2.基于token获取redis中的用户
//        if(StrUtil.isBlank(token)){//判断token是否存在
//            //不存在，进行拦截，返回401状态码（未授权）
//            response.setStatus(401);
//            return false;
//        }
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
//                .entries(RedisConstants.LOGIN_USER_KEY+token);
//        //3.判断map是否为空
//        if (userMap.isEmpty()) {
//            //4.不存在，进行拦截，返回401状态码（未授权）
//            response.setStatus(401);
//            return false;
//        }
//        //todo 5.把hash数据转换为userDto对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        //todo 6.存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser(userDTO);
//        //todo 7.刷新token有效期
//        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//30 minutes
//        //todo 8.放行

        //1.判断是否需要拦截（ThreadLocal是否有这个用户）
        if (UserHolder.getUser()==null){
            //  没有，需要拦截，设置状态码
            response.setStatus(401);
            return false;
        }
        //有则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();

    }
}
