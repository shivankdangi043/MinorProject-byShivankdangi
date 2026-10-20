package com.repochat.service;

import com.repochat.dto.RepoInfo;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Step 2 of the pipeline (the "R" in RAG preparation):
 * read files -> split into chunks -> create embeddings -> store in vector store.
 */
@Service
public class RepoIngestionService {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode",
            "venv", "__pycache__", ".gradle", "out", "vendor");

    private static final Set<String> TEXT_EXT = Set.of(
            "java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "cs",
            "php", "rb", "swift", "html", "css", "scss", "xml", "json", "yml", "yaml",
            "properties", "gradle", "sql", "md", "txt", "sh", "toml", "env.example");

    private final RepoCloneService cloneService;
    private final SimpleVectorStore vectorStore;
    private final RepoRegistry registry;
    private final File storeFile;
    private final long maxBytes;

    public RepoIngestionService(RepoCloneService cloneService,
                                SimpleVectorStore vectorStore,
                                RepoRegistry registry,
                                @Value("${app.data-dir}") String dataDir,
                                @Value("${app.max-file-kb}") int maxFileKb) {
        this.cloneService = cloneService;
        this.vectorStore = vectorStore;
        this.registry = registry;
        this.storeFile = new File(dataDir, "vectorstore.json");
        this.maxBytes = maxFileKb * 1024L;
    }

    public RepoInfo ingest(String url) throws Exception {
        String repoId = cloneService.toRepoId(url);
        Path root = cloneService.clone(url, repoId);

        // 1) read files
        List<Document> docs = new ArrayList<>();
        int fileCount = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (!isIndexable(root, p)) continue;
                String text = Files.readString(p);
                if (text.isBlank()) continue;
                String rel = root.relativize(p).toString().replace('\\', '/');
                docs.add(new Document(text, Map.of("repo", repoId, "path", rel)));
                fileCount++;
            }
        }

        // 2) split into chunks (metadata is copied to every chunk)
        List<Document> chunks = new TokenTextSplitter(1500, 350, 5, 10000, true).apply(docs);
        if (chunks.size() > 80) chunks = new ArrayList<>(chunks.subList(0, 80));
        System.out.println("Total chunks to index: " + chunks.size());

        // 3) embed + store, in small batches to respect API limits
        int batch = 5;
        for (int i = 0; i < chunks.size(); i += batch) {
            vectorStore.add(chunks.subList(i, Math.min(i + batch, chunks.size())));
            Thread.sleep(4500);
            System.out.println("Indexed " + Math.min(i + batch, chunks.size()) + "/" + chunks.size());
            Thread.sleep(4500);
            System.out.println("Indexed " + Math.min(i + batch, chunks.size()) + "/" + chunks.size());
        }
        vectorStore.save(storeFile);

        RepoInfo info = new RepoInfo(repoId, url.trim(), fileCount, chunks.size());
        registry.put(info);
        return info;
    }

    private boolean isIndexable(Path root, Path file) throws Exception {
        for (Path part : root.relativize(file)) {
            if (SKIP_DIRS.contains(part.toString())) return false;
        }
        if (Files.size(file) > maxBytes) return false;
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : name;
        if (!TEXT_EXT.contains(ext) && !name.equals("dockerfile") && !name.equals("pom.xml")) return false;
        return true;
    }
}
