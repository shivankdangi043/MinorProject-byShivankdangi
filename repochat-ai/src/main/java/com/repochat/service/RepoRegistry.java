package com.repochat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repochat.dto.RepoInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Remembers which repos are indexed (saved in data/repos.json). */
@Service
public class RepoRegistry {

    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<RepoInfo> repos = new ArrayList<>();

    public RepoRegistry(@Value("${app.data-dir}") String dataDir) throws Exception {
        File dir = new File(dataDir);
        dir.mkdirs();
        this.file = new File(dir, "repos.json");
        if (file.exists()) {
            repos.addAll(mapper.readValue(file, new TypeReference<List<RepoInfo>>() {}));
        }
    }

    public synchronized List<RepoInfo> all() {
        return List.copyOf(repos);
    }

    public synchronized void put(RepoInfo info) throws Exception {
        repos.removeIf(r -> r.id().equals(info.id()));
        repos.add(info);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, repos);
    }
}
