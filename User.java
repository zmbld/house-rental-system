package com.homestay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    /** 角色: ADMIN-管理员, LANDLORD-房东, TENANT-房客 */
    private String role;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    /** 审核状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回（房客/管理员默认已通过，注册房东为待审核） */
    private String auditStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
