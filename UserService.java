package com.homestay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.homestay.entity.RegisterRequest;
import com.homestay.entity.User;

public interface UserService extends IService<User> {
    User login(String username, String password);
    boolean register(User user);
    /**
     * 前台注册：房客直接通过，房东提交资质资料并进入待审核
     */
    void register(RegisterRequest req);
    boolean adminSave(User user);
}
