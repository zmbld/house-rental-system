package com.homestay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.homestay.entity.LandlordAudit;
import com.homestay.entity.RegisterRequest;
import com.homestay.entity.User;
import com.homestay.mapper.LandlordAuditMapper;
import com.homestay.mapper.UserMapper;
import com.homestay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private LandlordAuditMapper landlordAuditMapper;

    @Override
    public User login(String username, String password) {
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getPassword, md5Password);
        return getOne(wrapper);
    }

    @Override
    public boolean register(User user) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (count(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        // MD5加密密码
        user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes(StandardCharsets.UTF_8)));
        // 公开注册默认为房东，房客由管理员添加
        user.setRole(user.getRole() != null ? user.getRole() : "LANDLORD");
        if (user.getAuditStatus() == null) {
            user.setAuditStatus("APPROVED");
        }
        return save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        validatePhone(req.getPhone());
        String role = req.getRole() != null && !req.getRole().isEmpty() ? req.getRole() : "TENANT";
        if (!"TENANT".equals(role) && !"LANDLORD".equals(role)) {
            throw new RuntimeException("注册角色只能是房东或房客");
        }
        // 房东需先提交资质资料
        if ("LANDLORD".equals(role)) {
            if (req.getRealName() == null || req.getRealName().trim().isEmpty()) {
                throw new RuntimeException("请填写真实姓名");
            }
            if (req.getIdCard() == null || !req.getIdCard().matches("\\d{17}[\\dXx]")) {
                throw new RuntimeException("身份证号格式不正确，应为18位（最后一位可为X）");
            }
            if (req.getCertImage() == null || req.getCertImage().trim().isEmpty()) {
                throw new RuntimeException("请上传房产证等资质证明图片");
            }
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, req.getUsername());
        if (count(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8)));
        user.setRole(role);
        user.setNickname(req.getNickname() != null && !req.getNickname().trim().isEmpty() ? req.getNickname() : req.getUsername());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        // 房客无需审核，直接可用；房东提交资料后等待管理员审核
        user.setAuditStatus("LANDLORD".equals(role) ? "PENDING" : "APPROVED");
        save(user);

        if ("LANDLORD".equals(role)) {
            LandlordAudit audit = new LandlordAudit();
            audit.setUserId(user.getId());
            audit.setRealName(req.getRealName());
            audit.setIdCard(req.getIdCard());
            audit.setPhone(req.getPhone());
            audit.setAddress(req.getAddress());
            audit.setCertImage(req.getCertImage());
            if (req.getHouseImages() != null && !req.getHouseImages().isEmpty()) {
                audit.setHouseImages(String.join(",", req.getHouseImages()));
            }
            audit.setStatus("PENDING");
            landlordAuditMapper.insert(audit);
        }
    }

    @Override
    public boolean adminSave(User user) {
        validatePhone(user.getPhone());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (count(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes(StandardCharsets.UTF_8)));
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("TENANT");
        }
        return save(user);
    }

    /** 手机号固定11位，1开头 */
    private void validatePhone(String phone) {
        if (phone != null && !phone.trim().isEmpty() && !phone.trim().matches("1[3-9]\\d{9}")) {
            throw new RuntimeException("手机号格式不正确，应为11位数字（1开头）");
        }
    }
}
