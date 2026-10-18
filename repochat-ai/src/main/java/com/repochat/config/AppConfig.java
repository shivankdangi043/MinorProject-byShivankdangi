package com.repochat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class AppConfig {

    /** In-memory vector store that is saved to / loaded from a JSON file. */
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel,
                                         org.springframework.core.env.Environment env) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(env.getProperty("app.data-dir", "./data"), "vectorstore.json");
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are RepoChat, an expert senior software engineer.
                        You answer questions about a GitHub repository using ONLY the code and
                        file excerpts provided in the CONTEXT. Always mention the file path you
                        are referring to. If the answer is not in the context, say you could not
                        find it in the indexed code instead of guessing.
                        Format code in markdown code blocks.
                        """)
                .build();
    }
}
