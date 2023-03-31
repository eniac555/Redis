package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    //关注或者取关
    Result follow(Long followUserId, Boolean isFollow);

    //判断是否关注
    Result isFollow(Long followUserId);

    //共同关注
    Result followCommons(Long id);
}
