package com.example.aiterminal.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 配置共用 ChatClient；会话提示词在每次请求中补入可变日期。 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatSettings settings) {
        return builder.defaultSystem(settings.systemPrompt()).build();
    }
}
