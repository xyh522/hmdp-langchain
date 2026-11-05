package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Message;
import reactor.core.publisher.Flux;

import java.util.List;

public interface MessageService extends IService<Message> {

    List<Message> listMessagesByThreadId(String threadId, Long userId, String sessionId);

    Flux<String> chat(String threadId, String message, Integer userId, String sessionId);
}

