package com.homestay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.homestay.common.PageResult;
import com.homestay.common.Result;
import com.homestay.entity.Comment;
import com.homestay.entity.House;
import com.homestay.entity.Order;
import com.homestay.entity.User;
import com.homestay.mapper.CommentMapper;
import com.homestay.service.HouseService;
import com.homestay.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class OrderController {

    /** 评价内容最小长度 */
    private static final int COMMENT_MIN_LEN = 5;
    /** 评价内容最大长度 */
    private static final int COMMENT_MAX_LEN = 500;

    @Autowired
    private OrderService orderService;

    @Autowired
    private HouseService houseService;

    @Autowired
    private CommentMapper commentMapper;

    @GetMapping("/order/list")
    public String listPage() {
        return "order-list";
    }

    @PostMapping("/api/order/create")
    @ResponseBody
    public Result<?> create(@RequestBody Order order, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        order.setTenantId(user.getId());

        // 计算总价（仅在未传入价格时）
        if (order.getTotalPrice() == null) {
            House house = houseService.getById(order.getHouseId());
            if (house == null) {
                return Result.error("房源不存在");
            }
            long days = order.getCheckOutDate().toEpochDay() - order.getCheckInDate().toEpochDay();
            order.setTotalPrice(house.getPrice().multiply(BigDecimal.valueOf(days)));
        }

        try {
            Order saved = orderService.createOrder(order);
            return Result.ok("下单成功", saved);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/api/order/cancel/{id}")
    @ResponseBody
    public Result<?> cancel(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        try {
            orderService.cancelOrder(id, user.getId());
            return Result.ok("取消成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/api/order/pay/{id}")
    @ResponseBody
    public Result<?> pay(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!order.getTenantId().equals(user.getId())) {
            return Result.error("无权操作");
        }
        if (!"UNPAID".equals(order.getStatus())) {
            return Result.error("订单状态不正确（仅待支付订单可支付）");
        }
        // 房源下架后，未支付订单不可继续支付
        House house = houseService.getById(order.getHouseId());
        if (house == null) {
            return Result.error("房源不存在");
        }
        if (!"ONLINE".equals(house.getStatus())) {
            return Result.error("该房源已下架，订单无法继续支付");
        }
        order.setStatus("PAID");
        orderService.updateById(order);
        return Result.ok("支付成功（模拟）");
    }

    /**
     * 房源下架时将该房源下所有未支付订单标记为不可操作（仅返回受影响订单数与提示信息，
     * 不直接修改订单状态，由管理员/系统另行处理）
     */
    @PostMapping("/api/order/mark-affected/{houseId}")
    @ResponseBody
    public Result<?> markAffectedOrders(@PathVariable Long houseId, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return Result.error("无权操作");
        }
        // 此接口为预留，可由管理员手动调用。核心逻辑在 AdminController 下架房源中已体现：
        // 房源下架后订单列表接口会带上 offline 标记，前端禁用支付按钮，无需真正修改订单状态。
        return Result.ok("标记成功（前端会同步禁用支付按钮）");
    }

    /**
     * 房东为房客办理入住：PAID -> CHECKED_IN
     * 条件更新保证并发下不会重复流转
     */
    @PostMapping("/api/order/check-in/{id}")
    @ResponseBody
    public Result<?> checkIn(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        House house = houseService.getById(order.getHouseId());
        if (house == null || !house.getLandlordId().equals(user.getId())) {
            return Result.error("只有房源房东可办理入住");
        }
        boolean ok = orderService.update(new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getStatus, "PAID")
                .set(Order::getStatus, "CHECKED_IN"));
        return ok ? Result.ok("入住办理成功") : Result.error("仅已支付订单可办理入住");
    }

    /**
     * 房东确认退房完成：CHECKED_IN -> COMPLETED（完成后房客方可评价）
     */
    @PostMapping("/api/order/complete/{id}")
    @ResponseBody
    public Result<?> complete(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        House house = houseService.getById(order.getHouseId());
        if (house == null || !house.getLandlordId().equals(user.getId())) {
            return Result.error("只有房源房东可确认完成");
        }
        boolean ok = orderService.update(new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getStatus, "CHECKED_IN")
                .set(Order::getStatus, "COMPLETED"));
        return ok ? Result.ok("订单已完成，房客现在可以评价") : Result.error("仅已入住订单可确认完成");
    }

    /**
     * 获取当前登录用户在指定房源下所有"已完成且未评价"的订单，
     * 供"我要评价"表单使用。防止未入住用户刷评价。
     */
    @GetMapping("/api/order/commentable/{houseId}")
    @ResponseBody
    public Result<List<Map<String, Object>>> commentableOrders(
            @PathVariable Long houseId, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        List<Order> orders = orderService.list(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getTenantId, user.getId())
                        .eq(Order::getHouseId, houseId)
                        .eq(Order::getStatus, "COMPLETED")
                        .orderByDesc(Order::getCreateTime));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : orders) {
            Long c = commentMapper.selectCount(
                    new LambdaQueryWrapper<Comment>().eq(Comment::getOrderId, o.getId()));
            if (c != null && c > 0) continue; // 跳过已评价订单
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", o.getId());
            item.put("orderNo", o.getOrderNo());
            item.put("checkInDate", o.getCheckInDate());
            item.put("checkOutDate", o.getCheckOutDate());
            rows.add(item);
        }
        return Result.ok(rows);
    }

    @GetMapping("/api/order/list")
    @ResponseBody
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if ("LANDLORD".equals(user.getRole())) {
            // 房东看到自己房源的订单
            List<House> houses = houseService.list(
                    new LambdaQueryWrapper<House>().eq(House::getLandlordId, user.getId()));
            if (houses == null || houses.isEmpty()) {
                return Result.ok(new PageResult<>(0L, new ArrayList<>(), pageNum, pageSize));
            }
            List<Long> houseIds = houses.stream().map(House::getId).collect(Collectors.toList());
            wrapper.in(Order::getHouseId, houseIds);
        } else {
            // 房客/管理员看自己作为房客的订单
            wrapper.eq(Order::getTenantId, user.getId());
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> page = new Page<>(pageNum, pageSize);
        Page<Order> orderPage = orderService.page(page, wrapper);

        // 组装房源信息与可操作性标记
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            House house = houseService.getById(order.getHouseId());
            item.put("order", order);
            item.put("house", house);
            // 房源状态供前端判断：下架后是否让按时间按钮
            item.put("houseStatus", house != null ? house.getStatus() : null);
            // 是否可取消（订单状态 + 入住时间）
            item.put("canCancel", orderService.canCancel(order));
            // 是否可支付（仅未支付 + 房源已上架）
            item.put("canPay", orderService.canPay(order, house));
            // 是否可评价：仅已完成订单，且该订单尚未评价过
            if (orderService.canComment(order)) {
                Long commented = commentMapper.selectCount(
                        new LambdaQueryWrapper<Comment>().eq(Comment::getOrderId, order.getId()));
                item.put("canComment", commented == null || commented == 0);
            } else {
                item.put("canComment", false);
            }
            // 是否处于不可操作的终态
            item.put("terminal", orderService.isTerminalStatus(order.getStatus()));
            // 房东视角：可办理入住 / 可确认完成
            if ("LANDLORD".equals(user.getRole())) {
                item.put("canCheckIn", "PAID".equals(order.getStatus()));
                item.put("canComplete", "CHECKED_IN".equals(order.getStatus()));
            }
            rows.add(item);
        }

        PageResult<Map<String, Object>> pageResult = new PageResult<>(
                orderPage.getTotal(), rows, pageNum, pageSize);
        return Result.ok(pageResult);
    }

    @PostMapping("/api/comment/add")
    @ResponseBody
    public Result<?> addComment(@RequestBody Comment comment, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }

        // 1. 校验评分
        if (comment.getRating() == null || comment.getRating() < 1 || comment.getRating() > 5) {
            return Result.error("评分必须在1-5之间");
        }

        // 2. 校验评论内容
        String content = comment.getContent();
        if (content == null || content.trim().isEmpty()) {
            return Result.error("评价内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() < COMMENT_MIN_LEN) {
            return Result.error("评价内容至少 " + COMMENT_MIN_LEN + " 个字");
        }
        if (trimmed.length() > COMMENT_MAX_LEN) {
            return Result.error("评价内容最多 " + COMMENT_MAX_LEN + " 个字");
        }
        comment.setContent(trimmed);

        // 3. 校验必须关联有效订单
        if (comment.getOrderId() == null) {
            return Result.error("评价必须关联订单");
        }
        Order order = orderService.getById(comment.getOrderId());
        if (order == null) {
            return Result.error("订单不存在");
        }
        if (!order.getTenantId().equals(user.getId())) {
            return Result.error("只能评价自己的订单");
        }
        // 只有已完成的订单才能评价
        if (!"COMPLETED".equals(order.getStatus())) {
            return Result.error("仅已完成的订单可以评价");
        }
        // 防止重复评价
        Long existingCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getOrderId, comment.getOrderId()));
        if (existingCount != null && existingCount > 0) {
            return Result.error("该订单已评价，请勿重复提交");
        }

        // 4. 写入评价
        comment.setUserId(user.getId());
        // 如果前端未传 houseId，从订单中推断
        if (comment.getHouseId() == null) {
            comment.setHouseId(order.getHouseId());
        }
        commentMapper.insert(comment);

        // 5. 重新计算房源评分与评论数
        updateHouseRating(comment.getHouseId());

        return Result.ok("评价成功");
    }

    @PostMapping("/api/comment/reply/{commentId}")
    @ResponseBody
    public Result<?> reply(@PathVariable Long commentId, @RequestBody Map<String, String> body,
                           HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return Result.error("评价不存在");
        }
        House house = houseService.getById(comment.getHouseId());
        if (house == null || !house.getLandlordId().equals(user.getId())) {
            return Result.error("只有该房源的房东才能回复");
        }
        String reply = body.get("reply");
        if (reply == null || reply.trim().isEmpty()) {
            return Result.error("回复内容不能为空");
        }
        if (reply.length() > 500) {
            return Result.error("回复内容最多 500 个字");
        }
        comment.setReply(reply.trim());
        commentMapper.updateById(comment);
        return Result.ok("回复成功");
    }

    private void updateHouseRating(Long houseId) {
        // 计算平均评分
        java.util.List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().eq(Comment::getHouseId, houseId));
        if (comments.isEmpty()) return;

        double avgRating = comments.stream()
                .mapToInt(Comment::getRating)
                .average()
                .orElse(0);
        House house = houseService.getById(houseId);
        house.setRating(BigDecimal.valueOf(Math.round(avgRating * 10.0) / 10.0));
        house.setReviewCount(comments.size());
        houseService.updateById(house);
    }
}