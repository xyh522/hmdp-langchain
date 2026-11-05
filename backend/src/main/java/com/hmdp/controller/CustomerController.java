package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.CustomerThread;
import com.hmdp.mapper.CustomerThreadMapper;
import com.hmdp.service.MessageService;
import com.hmdp.utils.UserHolder;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/customer")
public class CustomerController {
    @Resource private MessageService messageService;
    @Resource private CustomerThreadMapper customerThreadMapper;

    @GetMapping
    public Result listThreads(@RequestParam("session_id") String sessionId) {
        String loginSessionId = requireSessionId(sessionId);
        return Result.ok(customerThreadMapper.selectList(new QueryWrapper<CustomerThread>()
                .eq("user_id", UserHolder.getUser().getId())
                .eq("session_id", loginSessionId)
                .orderByDesc("update_time")));
    }

    @PostMapping
    public Result createThread(@RequestBody Map<String, Object> body) {
        String sessionId = requireSessionId(body.get("session_id"));
        CustomerThread thread = new CustomerThread();
        thread.setThreadId(UUID.randomUUID().toString());
        thread.setSessionId(sessionId);
        thread.setUserId(UserHolder.getUser().getId());
        thread.setTitle("新对话");
        thread.setIsDeleted(0);
        thread.setCreateTime(LocalDateTime.now());
        thread.setUpdateTime(LocalDateTime.now());
        customerThreadMapper.insert(thread);
        return Result.ok(customerThreadMapper.selectById(thread.getThreadId()));
    }

    @DeleteMapping("/thread/{threadId}")
    public Result deleteThread(@PathVariable("threadId") String threadId,
                               @RequestParam("session_id") String sessionId) {
        int deleted = customerThreadMapper.delete(new QueryWrapper<CustomerThread>()
                .eq("thread_id", threadId)
                .eq("user_id", UserHolder.getUser().getId())
                .eq("session_id", requireSessionId(sessionId)));
        return deleted > 0 ? Result.ok() : Result.fail("对话不存在或无权删除");
    }

    @GetMapping("/thread/{threadId}/messages")
    public Result listThreadMessages(@PathVariable("threadId") String threadId,
                                     @RequestParam("session_id") String sessionId) {
        return Result.ok(messageService.listMessagesByThreadId(
                threadId, UserHolder.getUser().getId(), requireSessionId(sessionId)));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chat(@RequestBody Map<String, Object> body) {
        Object threadId = body.get("thread_id");
        Object message = body.get("message");
        if (threadId == null || message == null) {
            throw new IllegalArgumentException("thread_id 和 message 不能为空");
        }
        String sessionId = requireSessionId(body.get("session_id"));
        Integer userId = Math.toIntExact(UserHolder.getUser().getId());
        return messageService.chat(threadId.toString(), message.toString(), userId, sessionId)
                .map(content -> ServerSentEvent.<Object>builder()
                        .data(Collections.singletonMap("content", content)).build())
                .concatWith(Flux.just(ServerSentEvent.<Object>builder()
                        .event("done").data("[DONE]").build()));
    }

    private String requireSessionId(Object value) {
        if (value == null) throw new IllegalArgumentException("session_id 不能为空");
        String sessionId = value.toString().trim();
        if (sessionId.isEmpty() || sessionId.length() > 64) {
            throw new IllegalArgumentException("session_id 格式不正确");
        }
        return sessionId;
    }
}
