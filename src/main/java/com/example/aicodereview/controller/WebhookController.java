package com.example.aicodereview.controller;

import com.example.aicodereview.entity.Project;
import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ProjectMapper;
import com.example.aicodereview.service.ReviewService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private final ProjectMapper projectMapper;
    private final ReviewService reviewService;

    public WebhookController(ProjectMapper projectMapper, ReviewService reviewService) {
        this.projectMapper = projectMapper;
        this.reviewService = reviewService;
    }

    @PostMapping("/gitlab")
    public ResponseEntity<?> handleGitlabPush(@RequestBody GitlabPushEvent event) {
        // Simplified: map repository name to Project record
        String repoName = event.getProject().getPathWithNamespace();
        Project project = projectMapper.selectOne(
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aicodereview.entity.Project>
                        .lambdaQuery().eq(com.example.aicodereview.entity.Project::getName, repoName)
        );
        if (project == null) {
            // auto-register project
            Project p = new Project();
            p.setName(repoName);
            p.setGitUrl(event.getProject().getGitHttpUrl());
            projectMapper.insert(p);
            project = p;
        }

        String commitId = event.getAfter();
        Review review = reviewService.createAndRunReview(project.getId(), commitId);
        return ResponseEntity.ok(review);
    }

    @Data
    static class GitlabProject {
        private String pathWithNamespace;
        private String gitHttpUrl;
    }

    @Data
    static class GitlabPushEvent {
        private GitlabProject project;
        private String before;
        private String after;
    }
}