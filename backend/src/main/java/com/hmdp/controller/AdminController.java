package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.AdminManagementService;
import com.hmdp.utils.UserHolder;
import com.hmdp.service.IUserService;
import com.hmdp.entity.User;
import com.hmdp.mapper.VoucherMapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @Resource private IUserService userService;
    @Resource private IShopTypeService shopTypeService;
    @Resource private IShopService shopService;
    @Resource private IVoucherService voucherService;
    @Resource private VoucherMapper voucherMapper;
    @Resource private AdminManagementService managementService;

    private boolean isAdmin() {
        if (UserHolder.getUser() == null) return false;
        User user = userService.getById(UserHolder.getUser().getId());
        return user != null && "ADMIN".equals(user.getRole());
    }

    @GetMapping("/shop-types")
    public Result types() {
        if (!isAdmin()) return Result.fail("无管理员权限");
        return Result.ok(shopTypeService.query().orderByAsc("sort").list());
    }

    @GetMapping("/shops")
    public Result shops(@RequestParam Long typeId,
                        @RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long size) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        if (typeId <= 0 || page < 1 || size < 1 || size > 50) return Result.fail("分页参数无效");
        Page<Shop> result = shopService.query().eq("type_id", typeId).orderByAsc("id").page(new Page<>(page, size));
        return Result.ok(result);
    }

    @GetMapping("/shops/{id}")
    public Result shop(@PathVariable Long id) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        Shop shop = shopService.getById(id);
        return shop == null ? Result.fail("店铺不存在") : Result.ok(shop);
    }

    @PutMapping("/shops/{id}")
    public Result updateShop(@PathVariable Long id, @RequestBody Shop shop) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        return managementService.updateShop(id, shop);
    }

    @GetMapping("/shops/{id}/vouchers")
    public Result vouchers(@PathVariable Long id) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        return Result.ok(voucherMapper.queryAdminVouchers(id));
    }

    @PostMapping("/shops/{id}/vouchers")
    public Result addVoucher(@PathVariable Long id, @RequestBody Voucher voucher) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        return managementService.addVoucher(id, voucher);
    }

    @DeleteMapping("/shops/{shopId}/vouchers/{voucherId}")
    public Result deleteVoucher(@PathVariable Long shopId, @PathVariable Long voucherId) {
        if (!isAdmin()) return Result.fail("无管理员权限");
        return managementService.deleteVoucher(shopId, voucherId);
    }
}
