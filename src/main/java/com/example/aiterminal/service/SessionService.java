package com.example.aiterminal.service;

import com.example.aiterminal.dto.MessageResponse;
import com.example.aiterminal.dto.SessionResponse;
import com.example.aiterminal.entity.ChatMessage;
import com.example.aiterminal.entity.ChatSession;
import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.repository.ChatMessageRepository;
import com.example.aiterminal.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话生命周期与历史消息读取业务。 */
@Service
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public SessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public SessionResponse create(String title) {
        Instant now = Instant.now();
        return toResponse(sessionRepository.save(new ChatSession(UUID.randomUUID().toString(), title, now)), List.of());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(session -> toResponse(session, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(String id) {
        ChatSession session = requireSession(id);
        List<MessageResponse> messages = messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(id)
                .stream().map(SessionService::toMessageResponse).toList();
        return toResponse(session, messages);
    }

    @Transactional
    public SessionResponse rename(String id, String title) {
        ChatSession session = requireSession(id);
        session.rename(title, Instant.now());
        return toResponse(sessionRepository.save(session), List.of());
    }

    @Transactional
    public void delete(String id) {
        sessionRepository.delete(requireSession(id));
    }

    @Transactional(readOnly = true)
    public ChatSession requireSession(String id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "找不到该会话"));
    }

    @Transactional
    public void appendMessage(String sessionId, String role, String content) {
        ChatSession session = requireSession(sessionId);
        session.addMessage(new ChatMessage(role, content, Instant.now()), Instant.now());
        sessionRepository.save(session);
    }

    private static SessionResponse toResponse(ChatSession session, List<MessageResponse> messages) {
        return new SessionResponse(session.getId(), session.getTitle(), session.getCreatedAt(),
                session.getUpdatedAt(), messages);
    }

    private static MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
