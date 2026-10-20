package com.repochat.service;

import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Step 1 of the pipeline: download the GitHub repo to local disk. */
@Service
public class RepoCloneService {

    private final Path reposDir;

    public RepoCloneService(@Value("${app.data-dir}") String dataDir) {
        this.reposDir = Path.of(dataDir, "repos");
    }

    /** Turns https://github.com/owner/name(.git) into "owner-name". */
    public String toRepoId(String url) {
        String clean = url.trim().replaceAll("\\.git$", "").replaceAll("/+$", "");
        String[] parts = clean.split("/");
        if (parts.length < 2 || !clean.startsWith("https://github.com/")) {
            throw new IllegalArgumentException("Please provide a valid https://github.com/owner/repo URL");
        }
        return (parts[parts.length - 2] + "-" + parts[parts.length - 1]).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public Path clone(String url, String repoId) throws Exception {
        Files.createDirectories(reposDir);
        Path target = reposDir.resolve(repoId);
        deleteIfExists(target);
        try (Git git = Git.cloneRepository()
                .setURI(url.trim())
                .setDirectory(target.toFile())
                .setDepth(1).setTimeout(60).setProgressMonitor(new org.eclipse.jgit.lib.TextProgressMonitor(new java.io.PrintWriter(System.out, true)))
                .call()) {
            return target;
        }
    }

    private void deleteIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
