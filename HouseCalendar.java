package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("house_calendar")
public class HouseCalendar {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long houseId;
    private LocalDate date;
    /** 状态: AVAILABLE-可订, BOOKED-已订, BLOCKED-已封锁 */
    private String status;
    /** 特殊价格(为null则使用默认价格) */
    private BigDecimal specialPrice;
}
