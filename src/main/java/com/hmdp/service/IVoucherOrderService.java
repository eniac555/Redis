package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author eniac555
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    //秒杀优惠券
    Result seckillVoucher(Long voucherId);

    //原始版本的创建订单
    Result createVoucherOrder(Long voucherId);

    //优化后使用阻塞队列进行操作的创建订单处理过程
    void createVoucherOrder2(VoucherOrder voucherOrder);

}
