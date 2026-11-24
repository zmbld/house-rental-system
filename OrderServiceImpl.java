package com.homestay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.homestay.entity.House;
import com.homestay.entity.HouseCalendar;
import com.homestay.entity.Order;
import com.homestay.mapper.HouseCalendarMapper;
import com.homestay.mapper.HouseMapper;
import com.homestay.mapper.OrderMapper;
import com.homestay.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private HouseCalendarMapper houseCalendarMapper;

    @Autowired
    private HouseMapper houseMapper;

    /**
     * 可取消的订单状态：待支付、已支付
     */
    private static final String[] CANCELLABLE_STATUS = {"UNPAID", "PAID"};

    /**
     * 不可再操作（支付/取消）的订单状态
     */
    private static final String[] TERMINAL_STATUS = {"CHECKED_IN", "COMPLETED", "CANCELLED"};

    @Override
    @Transactional
    public Order createOrder(Order order) {
        // 1. 校验房源是否存在
        House house = houseMapper.selectById(order.getHouseId());
        if (house == null) {
            throw new RuntimeException("房源不存在");
        }
        // 2. 校验房源状态：只有已上架房源的状态才能下单
        if (!"ONLINE".equals(house.getStatus())) {
            throw new RuntimeException("该房源当前不可预订（PENDING待审核或已下架）");
        }
        // 3. 校验日期合法性
        if (order.getCheckInDate() == null || order.getCheckOutDate() == null) {
            throw new RuntimeException("请选择入住和退房日期");
        }
        if (!order.getCheckInDate().isBefore(order.getCheckOutDate())) {
            throw new RuntimeException("退房日期必须晚于入住日期");
        }
        // 不能预订历史日期
        if (order.getCheckInDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("入住日期不能早于今天");
        }
        // 4. 校验日期范围内是否已被预订
        List<HouseCalendar> calendars = houseCalendarMapper.selectList(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, order.getHouseId())
                        .ge(HouseCalendar::getDate, order.getCheckInDate())
                        .lt(HouseCalendar::getDate, order.getCheckOutDate())
        );
        for (HouseCalendar cal : calendars) {
            if ("BOOKED".equals(cal.getStatus())) {
                throw new RuntimeException("所选日期内存在已被预订的日期，请重新选择");
            }
            if ("BLOCKED".equals(cal.getStatus())) {
                throw new RuntimeException("所选日期内存在已被房东封锁的日期，请重新选择");
            }
        }

        // 5. 生成订单编号
        String orderNo = "HS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        order.setOrderNo(orderNo);

        // 6. 计算天数与总价
        long days = ChronoUnit.DAYS.between(order.getCheckInDate(), order.getCheckOutDate());
        order.setNights((int) days);

        // 使用特殊价格重算总价（如果日期范围内存在 specialPrice，则取平均值，否则使用房源默认价格）
        BigDecimal totalPrice = BigDecimal.ZERO;
        if (calendars.isEmpty()) {
            totalPrice = house.getPrice().multiply(BigDecimal.valueOf(days));
        } else {
            for (HouseCalendar cal : calendars) {
                BigDecimal dayPrice = cal.getSpecialPrice() != null ? cal.getSpecialPrice() : house.getPrice();
                totalPrice = totalPrice.add(dayPrice);
            }
            // 如果日期范围内没有全覆盖的日历（剩余日期使用 default价格）
            if (calendars.size() < days) {
                long defaultDays = days - calendars.size();
                totalPrice = totalPrice.add(house.getPrice().multiply(BigDecimal.valueOf(defaultDays)));
            }
        }
        order.setTotalPrice(totalPrice);

        order.setStatus("UNPAID");
        boolean saved = save(order);

        if (saved) {
            // 7. 原子锁定日期：唯一索引 + upsert，并发下单互斥；BLOCKED 日期不可被订单覆盖
            //    任一天占位失败（返回0）则抛异常，@Transactional 会连同订单一起回滚
            for (LocalDate date = order.getCheckInDate(); date.isBefore(order.getCheckOutDate()); date = date.plusDays(1)) {
                int rows = houseCalendarMapper.upsertBooked(order.getHouseId(), date, "BOOKED");
                if (rows == 0) {
                    throw new RuntimeException("手速慢了，所选日期刚被预订或已被房东封锁，请重新选择");
                }
            }
        }

        return order;
    }

    @Override
    @Transactional
    public boolean cancelOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getTenantId().equals(userId)) {
            throw new RuntimeException("只能取消自己的订单");
        }

        // 已入住、已完成、已取消订单不允许再取消
        if ("CHECKED_IN".equals(order.getStatus())) {
            throw new RuntimeException("已入住订单不可取消");
        }
        if ("COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("已完成订单不可取消");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("订单已取消，无需重复操作");
        }

        // 只有待支付或已支付状态可取消
        boolean isCancellable = false;
        for (String s : CANCELLABLE_STATUS) {
            if (s.equals(order.getStatus())) {
                isCancellable = true;
                break;
            }
        }
        if (!isCancellable) {
            throw new RuntimeException("当前订单状态不可取消");
        }

        // 入住前3天可免费取消
        long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), order.getCheckInDate());
        if (daysUntilCheckIn < 3) {
            throw new RuntimeException("入住前3天内不可取消订单");
        }

        order.setStatus("CANCELLED");
        order.setCancelTime(LocalDateTime.now());
        updateById(order);

        // 释放房态日历
        List<HouseCalendar> calendars = houseCalendarMapper.selectList(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, order.getHouseId())
                        .ge(HouseCalendar::getDate, order.getCheckInDate())
                        .lt(HouseCalendar::getDate, order.getCheckOutDate())
        );
        for (HouseCalendar cal : calendars) {
            cal.setStatus("AVAILABLE");
            houseCalendarMapper.updateById(cal);
        }

        return true;
    }

    /**
     * 校验订单是否处于不可再操作的终态
     */
    public boolean isTerminalStatus(String status) {
        if (status == null) return false;
        for (String s : TERMINAL_STATUS) {
            if (s.equals(status)) return true;
        }
        return false;
    }

    /**
     * 校验订单是否允许取消
     */
    public boolean canCancel(Order order) {
        if (order == null) return false;
        if (isTerminalStatus(order.getStatus())) return false;
        // 入住前3天可取消
        long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), order.getCheckInDate());
        return daysUntilCheckIn >= 3;
    }

    /**
     * 校验订单是否可以支付（状态为待支付 + 房源为已上架）
     */
    public boolean canPay(Order order, House house) {
        if (order == null || house == null) return false;
        if (!"UNPAID".equals(order.getStatus())) return false;
        return "ONLINE".equals(house.getStatus());
    }

    /**
     * 校验订单是否可以评价（状态为已完成）
     */
    public boolean canComment(Order order) {
        if (order == null) return false;
        return "COMPLETED".equals(order.getStatus());
    }
}