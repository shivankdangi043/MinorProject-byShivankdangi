package com.repochat.service;

import com.repochat.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Step 3: the RAG question-answering flow.
 * question -> embed -> similarity search (only in the chosen repo)
 * -> put matching code into the prompt -> LLM answers.
 */
@Service
public class ChatService {

    private final SimpleVectorStore vectorStore;
    private final ChatClient chatClient;
    private final int topK;

    public ChatService(SimpleVectorStore vectorStore, ChatClient chatClient,
                       @Value("${app.top-k}") int topK) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.topK = topK;
    }

    public ChatResponse ask(String repoId, String question) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .filterExpression("repo == '" + repoId + "'")
                        .build());

        String context = hits.stream()
                .map(d -> "--- FILE: " + d.getMetadata().get("path") + " ---\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        String answer = chatClient.prompt()
                .user("CONTEXT:\n" + context + "\n\nQUESTION:\n" + question)
                .call()
                .content();

        List<String> sources = hits.stream()
                .map(d -> String.valueOf(d.getMetadata().get("path")))
                .distinct()
                .toList();

        return new ChatResponse(answer, sources);
    }
}
