package com.example.aiterminal.repository;

import com.example.aiterminal.entity.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 会话存取接口。 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findAllByOrderByUpdatedAtDesc();
}
