package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("house")
public class House {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 房东用户ID */
    private Long landlordId;
    private String name;
    private String city;
    private String address;
    /** 房型: WHOLE-整套, PRIVATE-独立房间, SHARED-合住房间 */
    private String houseType;
    /** 面积(平方米) */
    private Double area;
    /** 设施(逗号分隔) */
    private String facilities;
    /** 每晚价格 */
    private BigDecimal price;
    /** 最大入住人数 */
    private Integer maxGuests;
    /** 房源描述 */
    private String description;
    /** 封面图片URL */
    private String coverImage;
    /** 状态: PENDING-待审核, ONLINE-已上架, OFFLINE-已下架 */
    private String status;
    /** 平均评分 */
    private BigDecimal rating;
    /** 评论数量 */
    private Integer reviewCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
