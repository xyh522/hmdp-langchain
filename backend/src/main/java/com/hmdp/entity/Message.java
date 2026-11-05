package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_message")
public class Message {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("thread_id")
    private String threadId;

    /**
     * 发送方类型: user(用户) / assistant(客服)
     */
    @TableField("type")
    private String type;

    @TableField("content")
    private String content;

    @TableField("create_time")
    private LocalDateTime createTime;
}
