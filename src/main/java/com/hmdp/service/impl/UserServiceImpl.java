package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //发送验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合返回错误信息
            return Result.fail("手机号格式错误!");
        }

        //3.生成验证码
        String code = RandomUtil.randomNumbers(6);

        /*
        //4.保存验证码到session
        session.setAttribute("code", code);
        */

        //TODO 4.保存验证码到redis，key为login:code:phone，value为code，设置了两分钟有效期
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        //会用到阿里云或者其他的第三方平台，懒得做了
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    //实现登录的接口
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合返回错误信息
            return Result.fail("手机号格式错误!");
        }

        /*
        //2.校验验证码，从session获取
        Object cacheCode = session.getAttribute("code");//session里面的code
        String code = loginForm.getCode();//前端里面的code
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //3.验证码不一致，报错
            return Result.fail("验证码错误");
        }
        */

        // TODO 2.校验验证码，从redis获取
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();//前端里面的code
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3.验证码不一致，报错
            return Result.fail("验证码错误");
        }

        //4.验证码一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.如果是新用户，即用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        //7.新用户和老用户都要进行用户信息保存到session的操作

        //session.setAttribute("user", user);
        //把所有用户信息都扔进去可能会造成用户信息泄露，并且内存压力过大
        //提供UserDto类，仅包含用户部分非敏感信息
        //UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //session.setAttribute("user", userDTO);


        //TODO 7.新用户和老用户都要进行用户信息保存到redis的操作
        //todo 7.1生成随机token，作为登录令牌（直接用手机号会泄露隐私）
        String token = UUID.randomUUID().toString(true);
        //todo 7.2将user对象转换为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //转变成map，用于下一步存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //todo 7.3 存储
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        //todo 7.4 设置有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,
                RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //todo 8.返回token给客户端（浏览器）
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }


}
