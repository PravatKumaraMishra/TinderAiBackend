package me.pravat.tinder_ai_backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import me.pravat.tinder_ai_backend.profile.ProfileTools;

@Configuration
public class AiConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ProfileTools profileTools) {
        return builder.build();
    }
}
