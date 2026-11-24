package com.homestay.entity;

import lombok.Data;

import java.util.List;

/**
 * 注册请求（含房东资质资料）
 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    /** 角色: TENANT-房客, LANDLORD-房东 */
    private String role;

    // ===== 房东资质资料（注册房东时必填）=====
    /** 真实姓名 */
    private String realName;
    /** 身份证号 */
    private String idCard;
    /** 联系地址 */
    private String address;
    /** 房产证/资质证明图片URL */
    private String certImage;
    /** 房屋图片URL列表 */
    private List<String> houseImages;
}
