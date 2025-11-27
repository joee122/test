package com.example.aicodereview.service;

import com.example.aicodereview.entity.Project;
import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ProjectMapper;
import com.example.aicodereview.mapper.ReviewMapper;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class ReviewProcessor {

    private final ReviewMapper reviewMapper;
    private final StaticAnalyzer staticAnalyzer;
    private final OllamaClient ollamaClient;
    private final GitCloneService gitCloneService;
    private final ProjectMapper projectMapper;

    public ReviewProcessor(ReviewMapper reviewMapper, StaticAnalyzer staticAnalyzer, OllamaClient ollamaClient, GitCloneService gitCloneService, ProjectMapper projectMapper) {
        this.reviewMapper = reviewMapper;
        this.staticAnalyzer = staticAnalyzer;
        this.ollamaClient = ollamaClient;
        this.gitCloneService = gitCloneService;
        this.projectMapper = projectMapper;
    }

    @Async("reviewTaskExecutor")
    public void processReviewAsync(Long reviewId, String gitUrl, String commitId) {
        File tmpDir = null;
        try {
            // create temp dir
            tmpDir = new File(System.getProperty("java.io.tmpdir"), "repo-" + UUID.randomUUID());
            tmpDir.mkdirs();

            // try to lookup project to get credentials (best effort)
            Project project = projectMapper.selectById(getProjectIdFromReview(reviewId));
            String username = project != null ? project.getGitUsername() : null;
            String token = project != null ? project.getGitToken() : null;

            // clone with shallow depth, timeout and retries
            gitCloneService.cloneRepository(gitUrl, tmpDir, username, token, 1, 120, 3);

            // checkout commit if provided
            if (commitId != null && !commitId.isBlank()) {
                try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(tmpDir)) {
                    git.checkout().setName(commitId).call();
                } catch (Exception ignored) {
                }
            }

            // run static analysis
            String summary = staticAnalyzer.analyzeDirectory(tmpDir);

            // prompt to Ollama
            String prompt = "You are an AI code review assistant. Given the following static analysis summary, provide concise, prioritized action items and specific suggestions developers can follow.\n\n"
                    + summary + "\n\nRespond with bullet points and brief explanations.";

            String ollamaResult = ollamaClient.generate(prompt);

            String combined = "Static analysis:\n" + summary + "\n\nLLM suggestions:\n" + ollamaResult;

            // update review
            Review r = new Review();
            r.setId(reviewId);
            r.setResultSummary(combined);
            r.setStatus("DONE");
            reviewMapper.updateById(r);

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

    private Long getProjectIdFromReview(Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        return review != null ? review.getProjectId() : null;
    }
}