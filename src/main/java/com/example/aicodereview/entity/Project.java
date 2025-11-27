package com.example.aicodereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String gitUrl;
    // optional credentials (请勿将敏感凭据明文提交到公共仓库)
    private String gitUsername;
    private String gitToken;
    private String authType; // NONE, HTTP_TOKEN, SSH
}