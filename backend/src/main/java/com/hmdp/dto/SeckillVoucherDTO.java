package com.hmdp.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 展示用的完整秒杀券信息，不包含实时库存。 */
@Data
public class SeckillVoucherDTO {
    private Long id;
    private Long voucherId;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer status;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}