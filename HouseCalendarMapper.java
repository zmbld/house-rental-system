package com.homestay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homestay.entity.HouseCalendar;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface HouseCalendarMapper extends BaseMapper<HouseCalendar> {

    /**
     * 原子占位（并发安全）：不存在则插入，存在则更新状态。
     * 依赖 house_calendar 表的 UNIQUE KEY uk_house_date (house_id, date)。
     * 返回值：1=新插入，2=更新成功，0=未生效（日期已是 BLOCKED 或已被并发订单锁定）。
     */
    @Insert("INSERT INTO house_calendar (house_id, date, status) VALUES (#{houseId}, #{date}, #{status}) " +
            "ON DUPLICATE KEY UPDATE status = IF(status = 'BLOCKED', status, VALUES(status))")
    int upsertBooked(@Param("houseId") Long houseId, @Param("date") LocalDate date, @Param("status") String status);
}
