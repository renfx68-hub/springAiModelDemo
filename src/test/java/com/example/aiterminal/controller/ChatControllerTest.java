package com.example.aiterminal.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.aiterminal.dto.StreamEvent;
import com.example.aiterminal.service.ChatService;
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

    @Test
    void streamEndpointWritesDeltaAndDoneEvents() throws Exception {
        org.mockito.Mockito.when(chatService.stream("session-1", "你好", null))
                .thenReturn(Flux.just(StreamEvent.delta("你好"), StreamEvent.done()));

        var result = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("\"type\":\"delta\"")))
                .andExpect(content().string(containsString("\"content\":\"")))
                .andExpect(content().string(containsString("event:done")));
    }
}
