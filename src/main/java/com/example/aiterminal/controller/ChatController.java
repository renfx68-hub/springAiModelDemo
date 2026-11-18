package com.example.aiterminal.controller;

import com.example.aiterminal.dto.ChatRequest;
import com.example.aiterminal.dto.StreamEvent;
import com.example.aiterminal.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import com.example.aiterminal.dto.ChatRegenerateRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** SSE 流式聊天入口；响应订阅取消时上游 Flux 会收到 cancel 信号。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> stream(@RequestAttribute("userId") String userId,
            @Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.sessionId(), userId, request.message(), request.imageBase64(),
                Boolean.TRUE.equals(request.professional()), Boolean.TRUE.equals(request.legal()),
                Boolean.TRUE.equals(request.webSearch()))
                .map(event -> ServerSentEvent.<StreamEvent>builder(event)
                        .event(event.type())
                        .build());
    }

    /** 重新生成最后一条回答：删除旧回答后按相同上下文重新流式输出。 */
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> regenerate(@RequestAttribute("userId") String userId,
            @Valid @RequestBody ChatRegenerateRequest request) {
        return chatService.regenerate(request.sessionId(), userId,
                Boolean.TRUE.equals(request.professional()), Boolean.TRUE.equals(request.legal()),
                Boolean.TRUE.equals(request.webSearch()))
                .map(event -> ServerSentEvent.<StreamEvent>builder(event)
                        .event(event.type())
                        .build());
    }
}
