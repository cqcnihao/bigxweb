package com.cqcnihao.bigx.business.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqcnihao.bigx.business.dto.OrderDTO;
import com.cqcnihao.bigx.business.entity.BizOrder;
import com.cqcnihao.bigx.business.service.BizOrderService;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.web.common.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单管理", description = "订单 CRUD 示例")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@SaCheckLogin
@SmEncrypt
public class BizOrderController extends BaseController {

    private final BizOrderService orderService;

    @Operation(summary = "分页查询订单", description = "status: 0=新增待确认，1=已生效，2=修改待确认")
    @GetMapping("/page")
    public Result<IPage<BizOrder>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.page(current, size, keyword, status));
    }

    @Operation(summary = "根据 ID 查询订单")
    @GetMapping("/{id}")
    public Result<BizOrder> get(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }

    @Operation(summary = "新增订单（第一步：落草稿，待确认）")
    @PostMapping
    public Result<BizOrder> create(@Valid @RequestBody OrderDTO dto) {
        return Result.success(orderService.create(dto));
    }

    @Operation(summary = "提交修改（第一步：生效记录写待确认快照）")
    @PutMapping
    public Result<BizOrder> update(@Valid @RequestBody OrderDTO dto) {
        return Result.success(orderService.update(dto));
    }

    @Operation(summary = "确认（第二步：确认新增 / 确认修改）")
    @PostMapping("/{id}/confirm")
    public Result<BizOrder> confirm(@PathVariable Long id) {
        return Result.success(orderService.confirm(id));
    }

    @Operation(summary = "取消待确认（草稿删除 / 丢弃修改快照）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return Result.success();
    }
}