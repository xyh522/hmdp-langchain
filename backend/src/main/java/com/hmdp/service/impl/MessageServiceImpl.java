package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.CustomerThread;
import com.hmdp.entity.Message;
import com.hmdp.mapper.CustomerThreadMapper;
import com.hmdp.service.MessageService;
import com.hmdp.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Resource
    private CustomerThreadMapper customerThreadMapper;

    private final WebClient customerWebClient;
    private final ObjectMapper objectMapper;

    public MessageServiceImpl(WebClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${agent.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this.customerWebClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Message> listMessagesByThreadId(String threadId, Long userId, String sessionId) {
        requireOwnedThread(threadId, userId, sessionId);
        return list(new QueryWrapper<Message>()
                .eq("thread_id", threadId)
                .orderByAsc("create_time"));
    }

    @Override
    public Flux<String> chat(String threadId, String message, Integer userId, String sessionId) {
        CustomerThread thread = requireOwnedThread(threadId, userId, sessionId);
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        if ("新对话".equals(thread.getTitle())) {
            thread.setTitle(normalizedMessage.length() > 20
                    ? normalizedMessage.substring(0, 20) + "..."
                    : normalizedMessage);
        }
        thread.setUpdateTime(LocalDateTime.now());
        customerThreadMapper.updateById(thread);

        Message userMessage = new Message();
        userMessage.setThreadId(threadId);
        userMessage.setType("user");
        userMessage.setContent(normalizedMessage);
        userMessage.setCreateTime(LocalDateTime.now());
        save(userMessage);

        StringBuilder fullResponse = new StringBuilder();
        return callAgent(normalizedMessage, userId, threadId)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    if (fullResponse.length() == 0) {
                        return;
                    }
                    Message assistantMessage = new Message();
                    assistantMessage.setThreadId(threadId);
                    assistantMessage.setType("assistant");
                    assistantMessage.setContent(fullResponse.toString());
                    assistantMessage.setCreateTime(LocalDateTime.now());
                    save(assistantMessage);
                    log.info("客服会话 [{}] 回复已落库，长度: {}", threadId, fullResponse.length());
                })
                .doOnError(error -> log.error(
                        "调用智能客服 SSE 失败: threadId={}, userId={}", threadId, userId, error));
    }

    private Flux<String> callAgent(String message, Integer userId, String threadId) {
        Map<String, Object> request = new HashMap<>();
        request.put("message", message);
        request.put("user_id", String.valueOf(userId));
        request.put("thread_id", threadId);

        return customerWebClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !"[DONE]".equals(chunk.trim()))
                .map(this::extractContent)
                .filter(content -> !content.isEmpty());
    }

    private String extractContent(String chunk) {
        try {
            JsonNode json = objectMapper.readTree(chunk);
            if (json.has("error")) {
                throw new IllegalStateException(json.get("error").asText());
            }
            if (json.has("content")) {
                return json.get("content").asText();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception ignored) {
            // 兼容 Agent 直接返回纯文本片段。
        }
        return chunk;
    }

    private CustomerThread requireOwnedThread(String threadId, Number userId, String sessionId) {
        CustomerThread thread = customerThreadMapper.selectById(threadId);
        if (thread == null || thread.getUserId() == null
                || thread.getUserId().longValue() != userId.longValue()
                || !thread.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return thread;
    }
}
