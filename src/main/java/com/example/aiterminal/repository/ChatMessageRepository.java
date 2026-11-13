package com.example.aiterminal.repository;

import com.example.aiterminal.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 消息存取接口，按时间顺序恢复会话内容。 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(String sessionId);
}
