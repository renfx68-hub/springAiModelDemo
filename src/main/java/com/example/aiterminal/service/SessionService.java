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

/** 会话生命周期与历史消息读取业务；所有操作都限定在所属用户名下。 */
@Service
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public SessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public SessionResponse create(String title, String ownerId) {
        Instant now = Instant.now();
        return toResponse(sessionRepository.save(
                new ChatSession(UUID.randomUUID().toString(), title, ownerId, now)), List.of());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(String ownerId) {
        return sessionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(session -> toResponse(session, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(String id, String ownerId) {
        ChatSession session = requireSession(id, ownerId);
        List<MessageResponse> messages = messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(id)
                .stream().map(SessionService::toMessageResponse).toList();
        return toResponse(session, messages);
    }

    @Transactional
    public SessionResponse rename(String id, String title, String ownerId) {
        ChatSession session = requireSession(id, ownerId);
        session.rename(title, Instant.now());
        return toResponse(sessionRepository.save(session), List.of());
    }

    @Transactional
    public void delete(String id, String ownerId) {
        sessionRepository.delete(requireSession(id, ownerId));
    }

    /** 取会话并校验归属，非本人会话一律按不存在处理（不泄露存在性）。 */
    @Transactional(readOnly = true)
    public ChatSession requireSession(String id, String ownerId) {
        return sessionRepository.findById(id)
                .filter(session -> ownerId != null && ownerId.equals(session.getOwnerId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "找不到该会话"));
    }

    @Transactional
    public void appendMessage(String sessionId, String role, String content) {
        appendMessage(sessionId, role, content, null);
    }

    @Transactional
    public MessageResponse appendMessage(String sessionId, String role, String content, String imageUrl) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "找不到该会话"));
        ChatMessage message = new ChatMessage(role, content, imageUrl, Instant.now());
        session.addMessage(message, Instant.now());
        sessionRepository.save(session);
        return new MessageResponse(message.getId(), role, content, imageUrl, message.getCreatedAt());
    }

    private static SessionResponse toResponse(ChatSession session, List<MessageResponse> messages) {
        return new SessionResponse(session.getId(), session.getTitle(), session.getCreatedAt(),
                session.getUpdatedAt(), messages);
    }

    private static MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getImageUrl(), message.getCreatedAt());
    }
}
