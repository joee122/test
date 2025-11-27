package com.example.aicodereview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aicodereview.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}