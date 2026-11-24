package com.homestay.controller;

import com.homestay.common.Result;
import com.homestay.entity.RegisterRequest;
import com.homestay.entity.User;
import com.homestay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/api/user/login")
    @ResponseBody
    public Result<?> login(@RequestBody User user, HttpSession session) {
        User loginUser = userService.login(user.getUsername(), user.getPassword());
        if (loginUser == null) {
            return Result.error("用户名或密码错误");
        }
        session.setAttribute("loginUser", loginUser);
        session.setMaxInactiveInterval(3600);
        return Result.ok("登录成功", loginUser);
    }

    @PostMapping("/api/user/register")
    @ResponseBody
    public Result<?> register(@RequestBody RegisterRequest req) {
        try {
            userService.register(req);
            if ("LANDLORD".equals(req.getRole())) {
                return Result.ok("注册成功，资质资料已提交，请等待管理员审核");
            }
            return Result.ok("注册成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/api/user/logout")
    @ResponseBody
    public Result<?> logout(HttpSession session) {
        session.invalidate();
        return Result.ok("已退出登录");
    }

    @GetMapping("/api/user/current")
    @ResponseBody
    public Result<?> currentUser(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.ok(user);
    }
}
