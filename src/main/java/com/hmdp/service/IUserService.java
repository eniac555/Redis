package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    //发送验证码
    Result sendCode(String phone, HttpSession session);

    //实现登录接口
    Result login(LoginFormDTO loginForm, HttpSession session);

    //用户签到
    Result sign();

    //用户签到统计
    Result signCount();
}
