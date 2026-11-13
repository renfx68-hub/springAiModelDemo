package com.example.aiterminal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.aiterminal.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证只保留最近的完整用户轮次，避免模型上下文无界增长。 */
class ChatServiceTest {

    @Test
    void trimHistoryKeepsOnlyMostRecentUserTurns() {
        List<ChatMessage> history = List.of(
                message("user", "旧问题"),
                message("assistant", "旧回答"),
                message("user", "新问题一"),
                message("assistant", "新回答一"),
                message("user", "新问题二"),
                message("assistant", "新回答二"));

        List<ChatMessage> trimmed = ChatService.trimHistory(history, 2);

        assertEquals(List.of("新问题一", "新回答一", "新问题二", "新回答二"),
                trimmed.stream().map(ChatMessage::getContent).toList());
    }

    private static ChatMessage message(String role, String content) {
        return new ChatMessage(role, content, Instant.now());
    }
}
