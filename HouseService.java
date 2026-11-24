package com.homestay.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.homestay.entity.House;

import java.math.BigDecimal;

public interface HouseService extends IService<House> {
    Page<House> searchHouses(Integer pageNum, Integer pageSize, String city, BigDecimal minPrice,
                             BigDecimal maxPrice, Integer maxGuests, String houseType, String sortBy);
}
