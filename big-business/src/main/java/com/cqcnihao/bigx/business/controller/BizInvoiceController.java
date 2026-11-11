package com.cqcnihao.bigx.business.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqcnihao.bigx.business.dto.InvoiceDTO;
import com.cqcnihao.bigx.business.entity.BizInvoice;
import com.cqcnihao.bigx.business.service.BizInvoiceService;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.web.common.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "发票管理", description = "发票 CRUD 示例")
@RestController
@RequestMapping("/invoice")
@RequiredArgsConstructor
@SaCheckLogin
@SmEncrypt
public class BizInvoiceController extends BaseController {

    private final BizInvoiceService invoiceService;

    @Operation(summary = "分页查询发票", description = "status: 0=新增待确认，1=已生效，2=修改待确认，3=删除待确认")
    @GetMapping("/page")
    public Result<IPage<BizInvoice>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(invoiceService.page(current, size, keyword, status));
    }

    @Operation(summary = "根据 ID 查询发票")
    @GetMapping("/{id}")
    public Result<BizInvoice> get(@PathVariable Long id) {
        return Result.success(invoiceService.getById(id));
    }

    @Operation(summary = "新增发票（第一步：落草稿，待确认）")
    @PostMapping
    public Result<BizInvoice> create(@Valid @RequestBody InvoiceDTO dto) {
        return Result.success(invoiceService.create(dto));
    }

    @Operation(summary = "提交修改（第一步：生效记录写待确认快照）")
    @PutMapping
    public Result<BizInvoice> update(@Valid @RequestBody InvoiceDTO dto) {
        return Result.success(invoiceService.update(dto));
    }

    @Operation(summary = "确认（第二步：确认新增 / 确认修改）")
    @PostMapping("/{id}/confirm")
    public Result<BizInvoice> confirm(@PathVariable Long id) {
        return Result.success(invoiceService.confirm(id));
    }

    @Operation(summary = "取消待确认（草稿删除 / 丢弃修改快照）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        invoiceService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "删除发票")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return Result.success();
    }
}