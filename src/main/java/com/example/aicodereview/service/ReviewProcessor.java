package com.example.aicodereview.service;

import com.example.aicodereview.entity.Review;
import com.example.aicodereview.mapper.ReviewMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class ReviewProcessor {

    private final ReviewMapper reviewMapper;
    private final StaticAnalyzer staticAnalyzer;
    private final OllamaClient ollamaClient;

    public ReviewProcessor(ReviewMapper reviewMapper, StaticAnalyzer staticAnalyzer, OllamaClient ollamaClient) {
        this.reviewMapper = reviewMapper;
        this.staticAnalyzer = staticAnalyzer;
        this.ollamaClient = ollamaClient;
    }

    @Async("reviewTaskExecutor")
    public void processReviewAsync(Long reviewId, String gitUrl, String commitId) {
        File tmpDir = null;
        try {
            // create temp dir
            tmpDir = new File(System.getProperty("java.io.tmpdir"), "repo-" + UUID.randomUUID());
            tmpDir.mkdirs();

            // clone repo (consider shallow clone optimization later)
            Git.cloneRepository()
                    .setURI(gitUrl)
                    .setDirectory(tmpDir)
                    .setNoCheckout(false)
                    .call();

            // Optionally checkout specific commitId if provided (naive attempt)
            if (commitId != null && !commitId.isBlank()) {
                try (Git git = Git.open(tmpDir)) {
                    git.checkout().setName(commitId).call();
                } catch (Exception e) {
                    // ignore checkout failure; proceed with default branch
                }
            }

            // run static analysis
            String summary = staticAnalyzer.analyzeDirectory(tmpDir);

            // build prompt for Ollama
            String prompt = "You are an AI code review assistant. Given the following static analysis summary, provide concise, prioritized action items and specific suggestions developers can follow.\n\n"
                    + summary + "\n\nRespond with bullet points and brief explanations.";

            String ollamaResult;
            try {
                ollamaResult = ollamaClient.generate(prompt);
            } catch (Exception e) {
                ollamaResult = "Ollama invocation failed: " + e.getMessage();
            }

            String combined = "Static analysis:\n" + summary + "\n\nLLM suggestions:\n" + ollamaResult;

            // update review to DONE
            Review r = new Review();
            r.setId(reviewId);
            r.setResultSummary(combined);
            r.setStatus("DONE");
            reviewMapper.updateById(r);

        } catch (GitAPIException | IOException e) {
            // update review to FAILED
            Review r = new Review();
            r.setId(reviewId);
            r.setStatus("FAILED");
            r.setResultSummary("Processing error: " + e.getMessage());
            reviewMapper.updateById(r);
        } finally {
            // cleanup
            if (tmpDir != null && tmpDir.exists()) {
                try {
                    FileUtils.deleteDirectory(tmpDir);
                } catch (IOException ignored) {
                }
            }
        }
    }
}