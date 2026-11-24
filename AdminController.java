package com.homestay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.homestay.common.PageResult;
import com.homestay.common.Result;
import com.homestay.entity.*;
import com.homestay.mapper.*;
import com.homestay.service.HouseService;
import com.homestay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private HouseMapper houseMapper;

    @Autowired
    private HouseService houseService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LandlordAuditMapper landlordAuditMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private HouseCalendarMapper houseCalendarMapper;

    @Autowired
    private CommentMapper commentMapper;

    /**
     * 验证管理员身份
     */
    private boolean isAdmin(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        return user != null && "ADMIN".equals(user.getRole());
    }

    // ==================== 页面路由 ====================

    @GetMapping("")
    public String adminPage() {
        return "admin";
    }

    // ==================== 仪表盘统计 ====================

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Result<Map<String, Object>> dashboard(HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("houseCount", houseMapper.selectCount(null));
        data.put("pendingCount", houseMapper.selectCount(
                new LambdaQueryWrapper<House>().eq(House::getStatus, "PENDING")));
        data.put("onlineCount", houseMapper.selectCount(
                new LambdaQueryWrapper<House>().eq(House::getStatus, "ONLINE")));
        data.put("orderCount", orderMapper.selectCount(null));
        data.put("todayOrderCount", orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .ge(Order::getCreateTime, java.time.LocalDateTime.now().toLocalDate().atStartOfDay())));
        data.put("commentCount", commentMapper.selectCount(null));
        data.put("pendingAuditCount", landlordAuditMapper.selectCount(
                new LambdaQueryWrapper<LandlordAudit>().eq(LandlordAudit::getStatus, "PENDING")));
        return Result.ok(data);
    }

    // ==================== 房源审核 ====================

    @GetMapping("/api/houses/pending")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> pendingHouses(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<House> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<House> wrapper = new LambdaQueryWrapper<House>()
                .eq(House::getStatus, "PENDING")
                .orderByDesc(House::getCreateTime);
        houseMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (House house : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("house", house);
            User landlord = userMapper.selectById(house.getLandlordId());
            row.put("landlordName", landlord != null ? landlord.getNickname() : "未知");
            rows.add(row);
        }

        return Result.ok(new PageResult<>(page.getTotal(), rows, pageNum, pageSize));
    }

    @PostMapping("/api/house/{id}/approve")
    @ResponseBody
    public Result<?> approve(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        House house = houseService.getById(id);
        if (house == null) return Result.error("房源不存在");
        if (!"PENDING".equals(house.getStatus())) return Result.error("仅待审核房源可操作");

        house.setStatus("ONLINE");
        houseService.updateById(house);
        return Result.ok("审核通过");
    }

    @PostMapping("/api/house/{id}/reject")
    @ResponseBody
    public Result<?> reject(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        House house = houseService.getById(id);
        if (house == null) return Result.error("房源不存在");

        house.setStatus("OFFLINE");
        houseService.updateById(house);
        return Result.ok("已驳回");
    }

    @PostMapping("/api/house/{id}/offline")
    @ResponseBody
    public Result<?> offline(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        House house = houseService.getById(id);
        if (house == null) return Result.error("房源不存在");

        house.setStatus("OFFLINE");
        houseService.updateById(house);
        return Result.ok("已下架");
    }

    // ==================== 房东资质审核 ====================

    @GetMapping("/api/audits")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> auditList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<LandlordAudit> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LandlordAudit> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(LandlordAudit::getStatus, status);
        }
        wrapper.orderByDesc(LandlordAudit::getCreateTime);
        landlordAuditMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LandlordAudit audit : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("audit", audit);
            User user = userMapper.selectById(audit.getUserId());
            row.put("username", user != null ? user.getUsername() : "未知");
            row.put("nickname", user != null ? user.getNickname() : "未知");
            rows.add(row);
        }
        return Result.ok(new PageResult<>(page.getTotal(), rows, pageNum, pageSize));
    }

    @PostMapping("/api/audit/{id}/approve")
    @ResponseBody
    public Result<?> approveAudit(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        LandlordAudit audit = landlordAuditMapper.selectById(id);
        if (audit == null) return Result.error("审核记录不存在");
        if (!"PENDING".equals(audit.getStatus())) return Result.error("仅待审核记录可操作");

        audit.setStatus("APPROVED");
        audit.setAuditTime(java.time.LocalDateTime.now());
        landlordAuditMapper.updateById(audit);

        User user = userMapper.selectById(audit.getUserId());
        if (user != null) {
            user.setAuditStatus("APPROVED");
            userMapper.updateById(user);
        }
        return Result.ok("审核通过，该房东现已可发布房源");
    }

    @PostMapping("/api/audit/{id}/reject")
    @ResponseBody
    public Result<?> rejectAudit(@PathVariable Long id,
                                 @RequestParam(required = false) String remark,
                                 HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        LandlordAudit audit = landlordAuditMapper.selectById(id);
        if (audit == null) return Result.error("审核记录不存在");

        audit.setStatus("REJECTED");
        audit.setRemark(remark);
        audit.setAuditTime(java.time.LocalDateTime.now());
        landlordAuditMapper.updateById(audit);

        User user = userMapper.selectById(audit.getUserId());
        if (user != null) {
            user.setAuditStatus("REJECTED");
            userMapper.updateById(user);
        }
        return Result.ok("已驳回");
    }

    // ==================== 房源管理 ====================

    @GetMapping("/api/houses")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> allHouses(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<House> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<House> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(House::getStatus, status);
        }
        wrapper.orderByDesc(House::getCreateTime);
        houseMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (House house : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("house", house);
            User landlord = userMapper.selectById(house.getLandlordId());
            row.put("landlordName", landlord != null ? landlord.getNickname() : "未知");
            rows.add(row);
        }

        return Result.ok(new PageResult<>(page.getTotal(), rows, pageNum, pageSize));
    }

    // ==================== 用户管理 ====================

    @GetMapping("/api/users")
    @ResponseBody
    public Result<PageResult<User>> userList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime);
        userMapper.selectPage(page, wrapper);
        page.getRecords().forEach(u -> u.setPassword(null));

        return Result.ok(new PageResult<>(page.getTotal(), page.getRecords(), pageNum, pageSize));
    }

    @PostMapping("/api/user")
    @ResponseBody
    public Result<?> addUser(@RequestBody User user, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return Result.error("密码不能为空");
        }
        if (user.getRole() == null || (!"TENANT".equals(user.getRole()) && !"LANDLORD".equals(user.getRole()))) {
            return Result.error("角色只能是房东或房客");
        }
        try {
            userService.adminSave(user);
            return Result.ok("添加成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/api/user/{id}")
    @ResponseBody
    public Result<?> updateUser(@PathVariable Long id, @RequestBody User user, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        User existing = userService.getById(id);
        if (existing == null) return Result.error("用户不存在");
        if ("ADMIN".equals(existing.getRole())) return Result.error("不能修改管理员");

        existing.setNickname(user.getNickname());
        existing.setPhone(user.getPhone());
        existing.setEmail(user.getEmail());
        if (user.getRole() != null && ("TENANT".equals(user.getRole()) || "LANDLORD".equals(user.getRole()))) {
            existing.setRole(user.getRole());
        }
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            existing.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes(StandardCharsets.UTF_8)));
        }
        userService.updateById(existing);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/api/user/{id}")
    @ResponseBody
    public Result<?> deleteUser(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        User user = userService.getById(id);
        if (user == null) return Result.error("用户不存在");
        if ("ADMIN".equals(user.getRole())) return Result.error("不能删除管理员");
        userService.removeById(id);
        return Result.ok("删除成功");
    }

    // ==================== 订单管理 ====================

    @GetMapping("/api/orders")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> orderList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<Order> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        orderMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order order : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", order);
            User tenant = userMapper.selectById(order.getTenantId());
            row.put("tenantName", tenant != null ? tenant.getNickname() : "未知");
            House house = houseMapper.selectById(order.getHouseId());
            row.put("houseName", house != null ? house.getName() : "未知");
            rows.add(row);
        }

        return Result.ok(new PageResult<>(page.getTotal(), rows, pageNum, pageSize));
    }

    @DeleteMapping("/api/order/{id}")
    @ResponseBody
    public Result<?> deleteOrder(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        Order order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        orderMapper.deleteById(id);

        // 释放房态：占用中的订单（未取消）删除后，其日期恢复可订，避免房源日期被永久锁死
        if (!"CANCELLED".equals(order.getStatus())) {
            List<HouseCalendar> calendars = houseCalendarMapper.selectList(
                    new LambdaQueryWrapper<HouseCalendar>()
                            .eq(HouseCalendar::getHouseId, order.getHouseId())
                            .ge(HouseCalendar::getDate, order.getCheckInDate())
                            .lt(HouseCalendar::getDate, order.getCheckOutDate()));
            for (HouseCalendar cal : calendars) {
                if ("BOOKED".equals(cal.getStatus())) {
                    cal.setStatus("AVAILABLE");
                    houseCalendarMapper.updateById(cal);
                }
            }
        }
        return Result.ok("订单已删除，相关日期已恢复可订");
    }

    // ==================== 评论管理 ====================

    @GetMapping("/api/comments")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> commentList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");

        Page<Comment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .orderByDesc(Comment::getCreateTime);
        commentMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Comment comment : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("comment", comment);
            User user = userMapper.selectById(comment.getUserId());
            row.put("userName", user != null ? user.getNickname() : "未知");
            House house = houseMapper.selectById(comment.getHouseId());
            row.put("houseName", house != null ? house.getName() : "未知");
            rows.add(row);
        }

        return Result.ok(new PageResult<>(page.getTotal(), rows, pageNum, pageSize));
    }

    @DeleteMapping("/api/comment/{id}")
    @ResponseBody
    public Result<?> deleteComment(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) return Result.error("无权访问");
        commentMapper.deleteById(id);
        return Result.ok("删除成功");
    }
}
