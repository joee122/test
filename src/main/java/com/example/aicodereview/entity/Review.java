package com.example.aicodereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reviews")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String commitId;
    private String status; // PENDING, DONE, FAILED
    private String resultSummary;
    private LocalDateTime createdAt;
}