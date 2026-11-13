package com.example.aiterminal.service;

import com.example.aiterminal.config.ChatSettings;
import com.example.aiterminal.dto.StreamEvent;
import com.example.aiterminal.entity.ChatMessage;
import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.repository.ChatMessageRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 编排上下文、模型流式响应与部分结果持久化。 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatClient chatClient;
    private final SessionService sessionService;
    private final ChatMessageRepository messageRepository;
    private final ChatSettings settings;
    private final ImageStorageService imageStorage;
    private final String apiKey;

    public ChatService(
            ChatClient chatClient,
            SessionService sessionService,
            ChatMessageRepository messageRepository,
            ChatSettings settings,
            ImageStorageService imageStorage,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClient = chatClient;
        this.sessionService = sessionService;
        this.messageRepository = messageRepository;
        this.settings = settings;
        this.imageStorage = imageStorage;
        this.apiKey = apiKey;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> loadHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
    }

    public Flux<StreamEvent> stream(String sessionId, String userText, String imageBase64) {
        if (apiKey.isBlank() || "missing".equalsIgnoreCase(apiKey)) {
            return Flux.just(StreamEvent.error("API_KEY_MISSING", "尚未配置模型 API Key，请设置 OPENAI_API_KEY 后重启服务。"));
        }

        try {
            sessionService.requireSession(sessionId);
            boolean hasImage = imageBase64 != null && !imageBase64.isBlank();
            String imageUrl = hasImage ? imageStorage.save(imageBase64.trim()) : null;
            List<ChatMessage> history = trimHistory(loadHistory(sessionId), settings.maxHistoryTurns());
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(settings.systemPrompt().replace(
                    "{currentDate}", LocalDate.now().toString())));
            for (ChatMessage message : history) {
                promptMessages.add("assistant".equals(message.getRole())
                        ? new AssistantMessage(message.getContent())
                        : buildUserMessage(message.getContent(), message.getImageUrl()));
            }
            promptMessages.add(buildUserMessage(userText, imageUrl));
            sessionService.appendMessage(sessionId, "user", userText, imageUrl);

            var optionsBuilder = OpenAiChatOptions.builder()
                    .temperature(settings.temperature())
                    .maxTokens(settings.maxTokens())
                    .topP(settings.topP());
            if (hasImage && settings.visionModel() != null && !settings.visionModel().isBlank()) {
                optionsBuilder.model(settings.visionModel());
            }
            var options = optionsBuilder.build();
            AtomicReference<StringBuilder> answer = new AtomicReference<>(new StringBuilder());
            AtomicBoolean saved = new AtomicBoolean();
            Flux<ChatResponse> modelStream = chatClient.prompt(
                            new org.springframework.ai.chat.prompt.Prompt(promptMessages, options))
                    .stream()
                    .chatResponse();
            Flux<StreamEvent> response = modelStream
                    .map(ChatService::responseText)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(text -> answer.get().append(text))
                    .map(StreamEvent::delta)
                    .concatWith(Mono.fromRunnable(() ->
                                    persistPartial(sessionId, answer.get().toString(), saved))
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenReturn(StreamEvent.done()))
                    .onErrorResume(error -> Mono.fromRunnable(() ->
                                    persistPartial(sessionId, answer.get().toString(), saved))
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenReturn(toErrorEvent(error)))
                    .doFinally(signal -> {
                        if (signal == reactor.core.publisher.SignalType.CANCEL) {
                            Mono.fromRunnable(() ->
                                            persistPartial(sessionId, answer.get().toString(), saved))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnError(saveError -> log.error(
                                            "Failed to persist cancelled assistant response for session {}",
                                            sessionId, saveError))
                                    .subscribe();
                        }
                    });
            return response;
        } catch (ApiException exception) {
            return Flux.just(StreamEvent.error("SESSION_NOT_FOUND", exception.getMessage()));
        } catch (RuntimeException exception) {
            return Flux.just(toErrorEvent(exception));
        }
    }

    /** 构建用户消息；带图片时附加媒体内容，图片文件缺失时退化为纯文本。 */
    private Message buildUserMessage(String text, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return new UserMessage(text);
        }
        byte[] bytes = imageStorage.loadBytes(imageUrl);
        if (bytes == null) {
            return new UserMessage(text);
        }
        MimeType mime = MimeType.valueOf(imageStorage.mimeOfUrl(imageUrl));
        return UserMessage.builder()
                .text(text)
                .media(List.of(new Media(mime, new ByteArrayResource(bytes))))
                .build();
    }

    static List<ChatMessage> trimHistory(List<ChatMessage> messages, int maxTurns) {
        if (maxTurns <= 0 || messages.isEmpty()) {
            return List.of();
        }
        int userTurns = 0;
        int start = messages.size();
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (!"user".equals(messages.get(index).getRole())) {
                continue;
            }
            userTurns++;
            if (userTurns >= maxTurns) {
                start = index;
                break;
            }
        }
        if (start == messages.size() && !messages.isEmpty()) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(start, messages.size()));
    }

    private void persistPartial(String sessionId, String text, AtomicBoolean saved) {
        if (text.isBlank() || !saved.compareAndSet(false, true)) {
            return;
        }
        sessionService.appendMessage(sessionId, "assistant", text);
    }

    private static String responseText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static StreamEvent toErrorEvent(Throwable error) {
        String detail = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (detail.contains("401") || detail.contains("unauthorized") || detail.contains("authentication")) {
            return StreamEvent.error("UNAUTHORIZED", "模型鉴权失败，请检查 API Key。");
        }
        if (detail.contains("429") || detail.contains("rate limit") || detail.contains("quota")) {
            return StreamEvent.error("RATE_LIMITED", "模型请求频率或额度受限，请稍后重试。");
        }
        if (detail.contains("timeout") || detail.contains("timed out")) {
            return StreamEvent.error("TIMEOUT", "模型响应超时，请稍后重试。");
        }
        return StreamEvent.error("UPSTREAM_ERROR", "模型服务暂时不可用，请检查接口配置或稍后重试。");
    }
}
