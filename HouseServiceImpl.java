package com.homestay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.homestay.entity.House;
import com.homestay.mapper.HouseMapper;
import com.homestay.service.HouseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class HouseServiceImpl extends ServiceImpl<HouseMapper, House> implements HouseService {

    @Override
    public Page<House> searchHouses(Integer pageNum, Integer pageSize, String city, BigDecimal minPrice,
                                    BigDecimal maxPrice, Integer maxGuests, String houseType, String sortBy) {
        LambdaQueryWrapper<House> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(House::getStatus, "ONLINE");

        if (StringUtils.hasText(city)) {
            wrapper.like(House::getCity, city);
        }
        if (minPrice != null) {
            wrapper.ge(House::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(House::getPrice, maxPrice);
        }
        if (maxGuests != null) {
            wrapper.ge(House::getMaxGuests, maxGuests);
        }
        if (StringUtils.hasText(houseType)) {
            wrapper.eq(House::getHouseType, houseType);
        }

        // 排序: price_asc, price_desc, rating_desc
        if ("price_asc".equals(sortBy)) {
            wrapper.orderByAsc(House::getPrice);
        } else if ("price_desc".equals(sortBy)) {
            wrapper.orderByDesc(House::getPrice);
        } else {
            wrapper.orderByDesc(House::getRating);
        }

        Page<House> page = new Page<>(pageNum, pageSize);
        return page(page, wrapper);
    }
}
