package com.homestay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.homestay.entity.House;
import com.homestay.entity.Order;

public interface OrderService extends IService<Order> {
    Order createOrder(Order order);
    boolean cancelOrder(Long orderId, Long userId);

    /**
     * 判断订单状态是否处于不可再操作的终态
     */
    boolean isTerminalStatus(String status);

    /**
     * 判断订单是否可以取消（状态非终态 + 入住前 3 天以上）
     */
    boolean canCancel(Order order);

    /**
     * 判断订单是否可以支付（状态为待支付 + 房源已上架）
     */
    boolean canPay(Order order, House house);

    /**
     * 判断订单是否可以评价（状态为已完成）
     */
    boolean canComment(Order order);
}
