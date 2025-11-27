package com.example.aicodereview.service;

import com.example.aicodereview.entity.Project;
import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ProjectMapper;
import com.example.aicodereview.mapper.ReviewMapper;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReviewProcessor {

    private final ReviewMapper reviewMapper;
    private final StaticAnalyzer staticAnalyzer;
    private final OllamaClient ollamaClient;
    private final GitCloneService gitCloneService;
    private final ProjectMapper projectMapper;

    // clone defaults (injected from application.yml)
    private final int cloneDepth;
    private final long cloneTimeoutSeconds;
    private final int cloneRetries;

    public ReviewProcessor(ReviewMapper reviewMapper, StaticAnalyzer staticAnalyzer, OllamaClient ollamaClient, GitCloneService gitCloneService, ProjectMapper projectMapper,
                           @Value("${clone.depth:1}") int cloneDepth,
                           @Value("${clone.timeoutSeconds:120}") long cloneTimeoutSeconds,
                           @Value("${clone.retries:3}") int cloneRetries) {
        this.reviewMapper = reviewMapper;
        this.staticAnalyzer = staticAnalyzer;
        this.ollamaClient = ollamaClient;
        this.gitCloneService = gitCloneService;
        this.projectMapper = projectMapper;
        this.cloneDepth = cloneDepth;
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
        this.cloneRetries = cloneRetries;
    }

    @Async("reviewTaskExecutor")
    public void processReviewAsync(Long reviewId, String gitUrl, String commitId) {
        File tmpDir = null;
        try {
            tmpDir = new File(System.getProperty("java.io.tmpdir"), "repo-" + UUID.randomUUID());
            tmpDir.mkdirs();

            // lookup project for credentials
            Project project = projectMapper.selectById(getProjectIdFromReview(reviewId));
            String username = project != null ? project.getGitUsername() : null;
            String token = project != null ? project.getGitToken() : null;

            // perform clone using configured defaults
            gitCloneService.cloneRepository(gitUrl, tmpDir, username, token, cloneDepth, cloneTimeoutSeconds, cloneRetries);

            // checkout commit if provided
            if (commitId != null && !commitId.isBlank()) {
                try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(tmpDir)) {
                    git.checkout().setName(commitId).call();
                } catch (Exception ignored) {
                }
            }

            // run static analysis
            String summary = staticAnalyzer.analyzeDirectory(tmpDir);

            // prepare initial resultSummary and mark as RUNNING
            Review start = new Review();
            start.setId(reviewId);
            start.setResultSummary("Static analysis:\n" + summary + "\n\nLLM suggestions:\n");
            start.setStatus("RUNNING");
            reviewMapper.updateById(start);

            // stream from Ollama and append to resultSummary as chunks arrive
            AtomicBoolean hadError = new AtomicBoolean(false);
            ollamaClient.streamGenerate(buildPrompt(summary), chunk -> {
                try {
                    // append chunk to current resultSummary
                    Review r = reviewMapper.selectById(reviewId);
                    String prev = r != null && r.getResultSummary() != null ? r.getResultSummary() : "";
                    String updated = prev + chunk;
                    Review update = new Review();
                    update.setId(reviewId);
                    update.setResultSummary(updated);
                    update.setStatus("RUNNING");
                    update.setCreatedAt(r != null ? r.getCreatedAt() : LocalDateTime.now());
                    reviewMapper.updateById(update);
                } catch (Exception e) {
                    hadError.set(true);
                }
            });

            if (hadError.get()) {
                Review r = new Review();
                r.setId(reviewId);
                r.setStatus("FAILED");
                r.setResultSummary("Ollama streaming error (see logs)");
                reviewMapper.updateById(r);
            } else {
                // mark DONE
                Review r = new Review();
                r.setId(reviewId);
                r.setStatus("DONE");
                reviewMapper.updateById(r);
            }

        } catch (Exception e) {
            Review r = new Review();
            r.setId(reviewId);
            r.setStatus("FAILED");
            r.setResultSummary("Processing error: " + e.getMessage());
            reviewMapper.updateById(r);
        } finally {
            if (tmpDir != null && tmpDir.exists()) {
                try {
                    FileUtils.deleteDirectory(tmpDir);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String buildPrompt(String summary) {
        return "You are an AI code review assistant. Given the following static analysis summary, provide concise, prioritized action items and specific suggestions developers can follow.\n\n" + summary + "\n\nRespond with bullet points and brief explanations.";
    }

    private Long getProjectIdFromReview(Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        return review != null ? review.getProjectId() : null;
    }
}