package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 订单编号 */
    private String orderNo;
    private Long houseId;
    /** 房客用户ID */
    private Long tenantId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    /** 入住天数 */
    private Integer nights;
    private String guestName;
    private String guestPhone;
    /** 订单总价 */
    private BigDecimal totalPrice;
    /** 状态: UNPAID-待支付, PAID-已支付, CHECKED_IN-已入住, COMPLETED-已完成, CANCELLED-已取消 */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime cancelTime;
    @TableLogic
    private Integer deleted;
}
