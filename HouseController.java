package com.homestay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.homestay.common.PageResult;
import com.homestay.common.Result;
import com.homestay.entity.*;
import com.homestay.mapper.CommentMapper;
import com.homestay.mapper.HouseCalendarMapper;
import com.homestay.mapper.HouseImageMapper;
import com.homestay.mapper.UserMapper;
import com.homestay.service.HouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HouseController {

    @Autowired
    private HouseService houseService;

    @Autowired
    private HouseImageMapper houseImageMapper;

    @Autowired
    private HouseCalendarMapper houseCalendarMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    // ==================== 页面路由 ====================

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/house/list")
    public String listPage() {
        return "house-list";
    }

    @GetMapping("/house/detail/{id}")
    public String detailPage(@PathVariable Long id, Model model) {
        model.addAttribute("houseId", id);
        return "house-detail";
    }

    @GetMapping("/house/publish")
    public String publishPage() {
        return "house-publish";
    }

    @GetMapping("/house/calendar")
    public String calendarManagePage() {
        return "house-calendar";
    }

    // ==================== API接口 ====================

    @GetMapping("/api/house/list")
    @ResponseBody
    public Result<PageResult<House>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer maxGuests,
            @RequestParam(required = false) String houseType,
            @RequestParam(required = false, defaultValue = "rating_desc") String sortBy) {

        Page<House> page = houseService.searchHouses(pageNum, pageSize, city, minPrice, maxPrice, maxGuests, houseType, sortBy);
        PageResult<House> pageResult = new PageResult<>(
                page.getTotal(), page.getRecords(), pageNum, pageSize);
        return Result.ok(pageResult);
    }

    @GetMapping("/api/house/detail/{id}")
    @ResponseBody
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        House house = houseService.getById(id);
        if (house == null) {
            return Result.error("房源不存在");
        }

        // 获取图片列表
        List<HouseImage> images = houseImageMapper.selectList(
                new LambdaQueryWrapper<HouseImage>()
                        .eq(HouseImage::getHouseId, id)
                        .orderByAsc(HouseImage::getSortOrder));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("house", house);
        data.put("images", images);
        return Result.ok(data);
    }

    @GetMapping("/api/house/calendar/{houseId}")
    @ResponseBody
    public Result<List<Map<String, Object>>> calendar(@PathVariable Long houseId,
                                                       @RequestParam Integer year,
                                                       @RequestParam Integer month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        List<HouseCalendar> calendars = houseCalendarMapper.selectList(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, houseId)
                        .ge(HouseCalendar::getDate, firstDay)
                        .le(HouseCalendar::getDate, lastDay));

        // 转为Map便于查找
        Map<LocalDate, HouseCalendar> calMap = calendars.stream()
                .collect(Collectors.toMap(HouseCalendar::getDate, c -> c));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            HouseCalendar cal = calMap.get(date);
            if (cal != null) {
                item.put("status", cal.getStatus());
                if (cal.getSpecialPrice() != null) {
                    item.put("specialPrice", cal.getSpecialPrice());
                }
            } else {
                item.put("status", "AVAILABLE");
            }
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 获取房源未来 N 个月内所有不可预订日期（已订 BOOKED + 已封锁 BLOCKED），
     * 供前端 Flatpickr 提前禁用，减少无效下单请求。
     */
    @GetMapping("/api/house/disabled-dates/{houseId}")
    @ResponseBody
    public Result<List<String>> disabledDates(
            @PathVariable Long houseId,
            @RequestParam(defaultValue = "6") Integer months) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusMonths(months);
        List<HouseCalendar> calendars = houseCalendarMapper.selectList(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, houseId)
                        .ge(HouseCalendar::getDate, today)
                        .lt(HouseCalendar::getDate, end));
        List<String> disabled = new ArrayList<>();
        for (HouseCalendar cal : calendars) {
            if ("BOOKED".equals(cal.getStatus()) || "BLOCKED".equals(cal.getStatus())) {
                disabled.add(cal.getDate().toString());
            }
        }
        return Result.ok(disabled);
    }

    @PostMapping("/api/house/save")
    @ResponseBody
    public Result<?> save(@RequestBody House house, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        // 房东资质未通过审核前不允许发布房源（重新查库，避免用登录时的旧快照）
        User dbUser = userMapper.selectById(user.getId());
        if (dbUser != null && !"APPROVED".equals(dbUser.getAuditStatus())) {
            if ("PENDING".equals(dbUser.getAuditStatus())) {
                return Result.error("资质资料正在审核中，审核通过后即可发布房源");
            }
            return Result.error("资质审核未通过，暂不能发布房源");
        }
        house.setLandlordId(user.getId());
        house.setStatus("PENDING");
        house.setRating(BigDecimal.ZERO);
        house.setReviewCount(0);
        houseService.save(house);
        return Result.ok("发布成功，等待审核", house.getId());
    }

    @PostMapping("/api/house/set-special-price")
    @ResponseBody
    public Result<?> setSpecialPrice(@RequestBody Map<String, Object> params, HttpSession session) {
        Long houseId = Long.valueOf(params.get("houseId").toString());
        String dateStr = (String) params.get("date");
        BigDecimal price = new BigDecimal(params.get("price").toString());

        User user = (User) session.getAttribute("loginUser");
        House house = houseService.getById(houseId);
        if (house == null || !house.getLandlordId().equals(user.getId())) {
            return Result.error("无权操作");
        }

        LocalDate date = LocalDate.parse(dateStr);
        HouseCalendar calendar = houseCalendarMapper.selectOne(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, houseId)
                        .eq(HouseCalendar::getDate, date));

        // 已预订日期的价格已随订单落库，不允许再改动
        if (calendar != null && "BOOKED".equals(calendar.getStatus())) {
            return Result.error("该日期已被预订，不能修改价格");
        }

        if (calendar == null) {
            calendar = new HouseCalendar();
            calendar.setHouseId(houseId);
            calendar.setDate(date);
            calendar.setStatus("AVAILABLE");
            calendar.setSpecialPrice(price);
            houseCalendarMapper.insert(calendar);
        } else {
            calendar.setSpecialPrice(price);
            houseCalendarMapper.updateById(calendar);
        }
        return Result.ok("设置成功");
    }

    /**
     * 房东查看自己的房源列表（房态日历页使用）
     */
    @GetMapping("/api/house/my")
    @ResponseBody
    public Result<List<House>> myHouses(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        List<House> houses = houseService.list(
                new LambdaQueryWrapper<House>().eq(House::getLandlordId, user.getId()));
        return Result.ok(houses);
    }

    /**
     * 房东修改日历状态：AVAILABLE <-> BLOCKED（封锁/解封）。
     * 已预订（BOOKED）日期不可修改，防止破坏订单一致性。
     */
    @PostMapping("/api/house/calendar/set-status")
    @ResponseBody
    public Result<?> setCalendarStatus(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        Long houseId = Long.valueOf(params.get("houseId").toString());
        String dateStr = (String) params.get("date");
        String status = (String) params.get("status");
        if (!"AVAILABLE".equals(status) && !"BLOCKED".equals(status)) {
            return Result.error("非法的日历状态，仅支持 AVAILABLE / BLOCKED");
        }
        House house = houseService.getById(houseId);
        if (house == null || !house.getLandlordId().equals(user.getId())) {
            return Result.error("无权操作");
        }
        LocalDate date = LocalDate.parse(dateStr);
        HouseCalendar cal = houseCalendarMapper.selectOne(
                new LambdaQueryWrapper<HouseCalendar>()
                        .eq(HouseCalendar::getHouseId, houseId)
                        .eq(HouseCalendar::getDate, date));
        if (cal != null && "BOOKED".equals(cal.getStatus())) {
            return Result.error("该日期已被预订，不能修改状态");
        }
        if (cal == null) {
            cal = new HouseCalendar();
            cal.setHouseId(houseId);
            cal.setDate(date);
            cal.setStatus(status);
            houseCalendarMapper.insert(cal);
        } else {
            cal.setStatus(status);
            houseCalendarMapper.updateById(cal);
        }
        return Result.ok("BLOCKED".equals(status) ? "已封锁该日期" : "已解封该日期");
    }

    @GetMapping("/api/house/comments/{houseId}")
    @ResponseBody
    public Result<List<Comment>> comments(@PathVariable Long houseId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getHouseId, houseId)
                        .orderByDesc(Comment::getCreateTime));
        return Result.ok(comments);
    }
}
