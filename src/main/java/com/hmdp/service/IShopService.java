package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    //利用redis缓存保存商户信息
    Result queryById(Long id);

    //设置店铺缓存的redis更新
    Result update(Shop shop);

    //Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
