package com.cqcnihao.bigx.business.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqcnihao.bigx.business.dto.ProductDTO;
import com.cqcnihao.bigx.business.entity.BizProduct;
import com.cqcnihao.bigx.business.service.BizProductService;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.web.common.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 加密全局化试点：类级 @SmEncrypt 使本 Controller 全部接口启用国密报文加解密
 * （请求需 EncryptedRequest 信封，响应为 EncryptedPayload；明文豁免用 @SmPlain）。
 */
@Tag(name = "商品管理", description = "商品 CRUD 示例")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@SaCheckLogin
@SmEncrypt
public class BizProductController extends BaseController {

    private final BizProductService productService;

    @Operation(summary = "分页查询商品", description = "status: 0=新增待确认，1=已生效，2=修改待确认")
    @GetMapping("/page")
    public Result<IPage<BizProduct>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(productService.page(current, size, keyword, status));
    }

    @Operation(summary = "根据 ID 查询商品")
    @GetMapping("/{id}")
    public Result<BizProduct> get(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @Operation(summary = "新增商品（第一步：落草稿，待确认）")
    @PostMapping
    public Result<BizProduct> create(@Valid @RequestBody ProductDTO dto) {
        return Result.success(productService.create(dto));
    }

    @Operation(summary = "提交修改（第一步：生效记录写待确认快照）")
    @PutMapping
    public Result<BizProduct> update(@Valid @RequestBody ProductDTO dto) {
        return Result.success(productService.update(dto));
    }

    @Operation(summary = "确认（第二步：确认新增 / 确认修改）")
    @PostMapping("/{id}/confirm")
    public Result<BizProduct> confirm(@PathVariable Long id) {
        return Result.success(productService.confirm(id));
    }

    @Operation(summary = "取消待确认（草稿删除 / 丢弃修改快照）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        productService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }
}