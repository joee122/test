package com.example.aicodereview.service;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.*;

@Service
public class GitCloneService {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Clone repository with optional credentials, shallow clone and timeout/retry.
     * Returns the cloned directory (caller should cleanup).
     */
    public File cloneRepository(String gitUrl, File targetDir, String username, String token, int depth, long timeoutSeconds, int maxAttempts) throws Exception {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Future<File> f = executor.submit(() -> {
                CloneCommand cmd = Git.cloneRepository()
                        .setURI(gitUrl)
                        .setDirectory(targetDir)
                        .setNoCheckout(false)
                        .setCloneAllBranches(false);
                if (depth > 0) cmd.setDepth(depth);
                if (username != null && token != null) {
                    cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token));
                }
                try (Git git = cmd.call()) {
                    return targetDir;
                }
            });

            try {
                return f.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                f.cancel(true);
                lastEx = new Exception("Clone timed out");
            } catch (ExecutionException ee) {
                lastEx = (ee.getCause() instanceof Exception) ? (Exception) ee.getCause() : ee;
            }
            // backoff
            Thread.sleep(1000L * attempt);
        }
        throw lastEx == null ? new Exception("Clone failed") : lastEx;
    }
}