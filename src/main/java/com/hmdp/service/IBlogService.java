package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    //查询热门博客
    Result queryHotBlog(Integer current);

    //查看博客详情
    Result queryBlogById(Long id);

    //点赞设置
    Result likeBlog(Long id);

    //查看点赞排行榜
    Result queryBlogLikes(Long id);

}
