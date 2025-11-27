package com.example.aicodereview.controller;

import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ReviewMapper;
import com.example.aicodereview.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    public ReviewController(ReviewService reviewService, ReviewMapper reviewMapper) {
        this.reviewService = reviewService;
        this.reviewMapper = reviewMapper;
    }

    @PostMapping("/run")
    public ResponseEntity<Review> runReview(@RequestParam Long projectId, @RequestParam String commitId) {
        Review r = reviewService.createAndRunReview(projectId, commitId);
        return ResponseEntity.ok(r);
    }

    @GetMapping
    public ResponseEntity<List<Review>> list() {
        return ResponseEntity.ok(reviewMapper.selectList(null));
    }
}