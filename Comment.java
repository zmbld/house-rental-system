package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long houseId;
    private Long userId;
    private Long orderId;
    /** 评分(1-5) */
    private Integer rating;
    /** 评价内容 */
    private String content;
    /** 房东回复 */
    private String reply;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
