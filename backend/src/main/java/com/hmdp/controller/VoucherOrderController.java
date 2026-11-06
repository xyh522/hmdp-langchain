package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.dto.VoucherOrderView;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @GetMapping("/list")
    public Result listMyOrders(@RequestParam(defaultValue = "1") long current) {
        if (current < 1) return Result.fail("页码无效");
        Long userId = UserHolder.getUser().getId();
        IPage<VoucherOrderView> page = voucherOrderMapper.selectUserOrders(new Page<>(current, 10), userId);
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result myOrder(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(voucherOrderMapper.selectUserOrder(id, userId));
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
    /**
     * 普通券下单，与秒杀流程分离。
     */
    @PostMapping("/ordinary/{id}")
    public Result buyOrdinaryVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.buyOrdinaryVoucher(voucherId);
    }
}
