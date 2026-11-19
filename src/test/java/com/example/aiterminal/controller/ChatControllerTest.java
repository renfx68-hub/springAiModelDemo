package com.example.aiterminal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aiterminal.config.AuthInterceptor;
import com.example.aiterminal.dto.StreamEvent;
import com.example.aiterminal.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

/** 通过 MVC 的异步响应断言 SSE 以事件流形式返回。 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void allowRequests() throws Exception {
        org.mockito.Mockito.when(authInterceptor.preHandle(
                        any(HttpServletRequest.class), any(HttpServletResponse.class), any()))
                .thenReturn(true);
    }

    @Test
    void streamEndpointWritesSourcesDeltaAndDoneEvents() throws Exception {
        var source = new StreamEvent.Source("Example", "https://example.com/news", "Media", null, "Snippet");
        org.mockito.Mockito.when(chatService.stream("session-1", "user-1", "你好", null, false, false, true))
                .thenReturn(Flux.just(StreamEvent.sources(List.of(source)),
                        StreamEvent.delta("你好"), StreamEvent.done()));

        var result = mockMvc.perform(post("/api/chat/stream")
                        .requestAttr("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"你好\",\"webSearch\":true}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:sources")))
                .andExpect(content().string(containsString("\"url\":\"https://example.com/news\"")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("\"type\":\"delta\"")))
                .andExpect(content().string(containsString("\"content\":\"")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void regenerateEndpointStreamsNewAnswer() throws Exception {
        org.mockito.Mockito.when(chatService.regenerate("session-1", "user-1", false, true, false))
                .thenReturn(Flux.just(StreamEvent.delta("重新"), StreamEvent.done()));

        var result = mockMvc.perform(post("/api/chat/regenerate")
                        .requestAttr("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"sessionId\":\"session-1\",\"legal\":true}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:done")));
    }
}
