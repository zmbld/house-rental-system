package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("house_image")
public class HouseImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long houseId;
    private String imageUrl;
    private Integer sortOrder;
}
