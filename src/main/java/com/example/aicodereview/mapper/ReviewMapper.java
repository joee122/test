package com.example.aicodereview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aicodereview.entity.Review;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}