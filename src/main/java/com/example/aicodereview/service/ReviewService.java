package com.example.aicodereview.service;

import com.example.aicodereview.entity.Project;
import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ProjectMapper;
import com.example.aicodereview.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReviewService {
    private final ProjectMapper projectMapper;
    private final ReviewMapper reviewMapper;
    private final AIReviewService aiReviewService;

    public ReviewService(ProjectMapper projectMapper, ReviewMapper reviewMapper, AIReviewService aiReviewService) {
        this.projectMapper = projectMapper;
        this.reviewMapper = reviewMapper;
        this.aiReviewService = aiReviewService;
    }

    @Transactional
    public Review createAndRunReview(Long projectId, String commitId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        Review review = new Review();
        review.setProjectId(projectId);
        review.setCommitId(commitId);
        review.setStatus("PENDING");
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        try {
            String result = aiReviewService.analyzeRepository(project.getGitUrl(), commitId);
            review.setResultSummary(result);
            review.setStatus("DONE");
            reviewMapper.updateById(review);
        } catch (Exception e) {
            review.setStatus("FAILED");
            review.setResultSummary(e.getMessage());
            reviewMapper.updateById(review);
            throw e;
        }
        return review;
    }
}