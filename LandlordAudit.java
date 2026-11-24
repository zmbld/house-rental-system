package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房东资质审核资料
 */
@Data
@TableName("landlord_audit")
public class LandlordAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 申请用户ID */
    private Long userId;
    /** 真实姓名 */
    private String realName;
    /** 身份证号 */
    private String idCard;
    /** 联系电话 */
    private String phone;
    /** 联系地址 */
    private String address;
    /** 房产证/资质证明图片URL */
    private String certImage;
    /** 房屋图片URL，多个用逗号分隔 */
    private String houseImages;
    /** 审核状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回 */
    private String status;
    /** 管理员审核意见 */
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 审核时间 */
    private LocalDateTime auditTime;
}
