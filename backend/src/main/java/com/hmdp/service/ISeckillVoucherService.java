package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.entity.SeckillVoucher;
import java.util.List;

public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    List<SeckillVoucherDTO> querySeckillVouchersOfShop(Long shopId);
}