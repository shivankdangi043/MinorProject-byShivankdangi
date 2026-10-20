package com.repochat.controller;

import com.repochat.dto.*;
import com.repochat.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RepoController {

    private final RepoIngestionService ingestion;
    private final RepoRegistry registry;
    private final ChatService chat;

    public RepoController(RepoIngestionService ingestion, RepoRegistry registry, ChatService chat) {
        this.ingestion = ingestion;
        this.registry = registry;
        this.chat = chat;
    }

    /** Upload (clone + index) a GitHub repo. */
    @PostMapping("/repos")
    public ResponseEntity<?> addRepo(@RequestBody IngestRequest req) {
        try {
            return ResponseEntity.ok(ingestion.ingest(req.url()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/repos")
    public List<RepoInfo> list() {
        return registry.all();
    }

    /** Ask a question about an indexed repo. */
    @PostMapping("/chat")
    public ResponseEntity<?> ask(@RequestBody ChatRequest req) {
        try {
            return ResponseEntity.ok(chat.ask(req.repoId(), req.question()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
