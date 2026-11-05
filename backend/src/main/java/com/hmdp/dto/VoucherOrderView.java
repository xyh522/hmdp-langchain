package com.hmdp.dto;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

@Data
public class VoucherOrderView {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voucherId;
    private Integer status;
    private LocalDateTime createTime;
    private String voucherTitle;
    private Long payValue;
    private String shopName;
}
