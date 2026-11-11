# License 签名注解体系

> 版本：1.4.7-SNAPSHOT | 算法：SHA256withRSA + Base64 | 密钥库：JKS

## 1. 架构

```
┌──────────────────────────── big-license ─────────────────────────────┐
│  annotation/@Sign           方法后置签名注解                            │
│  annotation/@Verify         方法前置验签注解                            │
│  annotation/BizType         业务类型枚举 (PRODUCT / ORDER / ...)       │
│  Signable.java              实体接口：getBizId() + buildSignPayload()   │
│  LicenseSignService         RSA 签名 / 验签底层服务                    │
│                                                                       │
│  ── 签名存储层 ──                                                     │
│  entity/SysSignature        独立签名表实体                              │
│  mapper/SysSignatureMapper  继承 BaseMapper<SysSignature>              │
│                                                                       │
│  ── 自动配置层 ──                                                     │
│  LicenseVerifyAutoConfiguration    big.license.enabled=true → 装服务   │
│  SignatureAutoConfiguration        enabled + 存在 SysSignatureMapper   │
│                                    → 装 SignatureAspect               │
│  aspect/SignatureAspect      @Around 织入 upsert 签名表                │
└───────────────────────────────────────────────────────────────────────┘
                                          ↑
                                          │ 宿主模块 (如 big-business)
                                          ↓
     @MapperScan({"...business.mapper", "com.cqcnihao.bigx.license.mapper"})
     big.license.enabled: true   ← 开启签名
```

**解耦核心**：签名数据完全与业务表解耦，统一存入 `sys_signature` 独立表；业务实体不再有 `sign` 字段。切面通过 Spring Boot 自动配置条件化注册，无 MyBatis-Plus 的模块（如 gateway）完全不受影响。

### 依赖声明

big-license 内部使用 **provided** scope 引入 AOP 和 MyBatis-Plus，编译期可用、运行时由宿主模块提供：

```xml
<!-- big-license/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aop</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <scope>provided</scope>
</dependency>
```

宿主模块（如 big-business）必须提供运行时依赖：spring-boot-starter-aop + mybatis-plus。

## 2. 自动配置条件链

```
SignatureAutoConfiguration
  ├── @ConditionalOnProperty("big.license.enabled=true")     ← 总开关（配置期判定）
  ├── @ConditionalOnClass({ Aspect.class, SysSignatureMapper.class })
  │                                                           ← 编译期判定，无 AOP/MP 环境直接跳过
  └── ObjectProvider<SysSignatureMapper>.getIfAvailable()    ← 运行时延迟获取（解决 MapperScan 时序问题）
                                                               切面内部 null check 兜底
```

| 场景 | 切面是否注册 | 说明 |
|------|------------|------|
| `big.license.enabled=false` | ❌ 不注册 | `@ConditionalOnProperty` 失败，静默跳过 |
| `enabled=true` 但无 AOP/MP | ❌ 不注册 | `@ConditionalOnClass` 失败，零侵入 |
| `enabled=true` + MapperScan 扫了 license.mapper | ✅ 注册 | `ObjectProvider` 延迟拿到 Bean |
| `enabled=true` 但宿主没扫 license.mapper | ⚠️ 注册但签名/验签跳过 | 切面创建成功，内部 `signatureMapper == null` → warn 日志静默跳过 |

### 为什么不能用 `@ConditionalOnBean(SysSignatureMapper.class)`

`@MapperScan` 通过 `MapperScannerConfigurer`（`BeanDefinitionRegistryPostProcessor`）注册 Mapper Bean；`@AutoConfiguration` 通过 Spring Boot 的 ImportCandidates 机制加载。二者执行顺序**不稳定**——自动配置的条件评估常常早于 Mapper 扫描完成，导致 SysSignatureMapper Bean 还没注册时 `@ConditionalOnBean` 就判定 "不存在"，切面永远无法装配。

**正确做法**：去掉 `@ConditionalOnBean`，改用 `ObjectProvider<T>` 延迟注入。Spring 在依赖解析阶段会等所有 BeanDefinition 都注册完再给 `ObjectProvider` 赋值，`getIfAvailable()` 能安全返回实际 Bean 或 null。切面内部已有 null check 兜底：

```java
// SignatureAspect.java
if (signatureMapper == null) {
    log.warn("[@Sign] SysSignatureMapper not assembled, skip sign");
    return result;
}
```

## 3. 快速使用

### Step 1：实体实现 Signable

```java
import com.cqcnihao.bigx.license.Signable;
import com.cqcnihao.bigx.license.annotation.BizType;

@Data
@TableName("biz_product")
public class BizProduct implements Serializable, Signable {

    public static final String BIZ_TYPE = BizType.PRODUCT.code();   // "Product"

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productName;
    private Integer status;
    private String pendingData;
    // 注意：实体类不再有 sign 字段！签名存在 sys_signature 表

    @Override
    public Object getBizId() { return id; }

    @Override
    public String buildSignPayload() {
        int s = status == null ? STATUS_ACTIVE : status;
        return switch (s) {
            case STATUS_DRAFT_CREATE   -> String.join("|",
                "C", ns(id), ns(productName), ns(productCode), ns(price), ns(stock), ns(category), ns(description));
            case STATUS_PENDING_UPDATE -> String.join("|", "U", ns(id), ns(pendingData));
            case STATUS_PENDING_DELETE -> String.join("|", "D", ns(id), ns(productName), ns(productCode));
            default                    -> String.join("|", "A", ns(id), ns(productCode));
        };
    }

    private static String ns(Object o) { return o == null ? "" : o.toString(); }
}
```

### Step 2：Service 方法加注解

```java
@Service
public class BizProductServiceImpl implements BizProductService {

    // 返回值为 Signable，切面自动 upsert 到 sys_signature 表
    @Sign(storePayload = true)
    @Transactional
    public BizProduct create(ProductDTO dto) {
        BizProduct p = new BizProduct();
        BeanUtils.copyProperties(dto, p);
        p.setStatus(BizProduct.STATUS_DRAFT_CREATE);
        this.save(p);
        return p;   // 切面在 proceed() 后签名 → upsert sys_signature
    }

    @Sign(storePayload = true)
    @Transactional
    public BizProduct update(ProductDTO dto) { ... }

    @Sign(storePayload = true)
    @Transactional
    public void delete(Long id) { ... }   // void 返回值，切面遍历参数找 Signable

    // confirm 验签：参数无 Signable 时 proceed 后对返回值验签
    @Verify
    @Transactional
    public BizProduct confirm(Long id) { ... }
}
```

### Step 3：宿主模块注册 Mapper

```java
@SpringBootApplication
@MapperScan({
    "com.cqcnihao.bigx.business.mapper",
    "com.cqcnihao.bigx.license.mapper"    // ← 关键：让 SysSignatureMapper 成为 Bean
})
public class BusinessApplication { ... }
```

### Step 4：开启签名

```yaml
big:
  license:
    enabled: true
    store-pwd: "your-store-password"
    license-path: "./license/license.lic"
    public-certs-path: "./license/publicCerts.store"
    private-store-path: "./license/privateKeys.store"
    key-pwd: "your-key-password"
    private-alias: "privateKey"
```

## 4. 注解参数

### @Sign

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `silent` | `boolean` | `true` | 签名能力未启用时是否静默跳过 |
| `bizType` | `String` | `""` | 业务类型码；为空时切面回退到 `Signable.getBizType()` |
| `signType` | `String` | `"SHA256withRSA"` | 签名算法 |
| `storePayload` | `boolean` | `false` | 是否将 payload 原文存入签名表；为 true 时验签直接用存储值，避免业务对象状态变更导致校验失败 |
| `appendHistory` | `boolean` | `false` | 是否保留历史签名版本 |

### @Verify

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `silent` | `boolean` | `false` | 验签失败时是否静默吞掉 |
| `bizType` | `String` | `""` | 业务类型码；为空时回退到 `Signable.getBizType()` |

### BizType 枚举

```java
public enum BizType {
    PRODUCT("Product"),
    ORDER("Order"),
    CONTRACT("Contract"),
    INVOICE("Invoice");

    private final String code;
    public String code() { return code; }
    public static BizType fromCode(String code) { ... }
}
```

## 5. 独立签名表

```sql
CREATE TABLE sys_signature (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_id        VARCHAR(64)  NOT NULL COMMENT '业务主键 (对应实体 Signable.getBizId())',
    biz_type      VARCHAR(32)  NOT NULL COMMENT '业务类型码 (对应 BizType.code())',
    sign_type     VARCHAR(32)  NOT NULL COMMENT '签名算法 (SHA256withRSA)',
    payload       TEXT         NULL     COMMENT '签名原文 (storePayload=true 时存储)',
    signature     VARCHAR(512) NOT NULL COMMENT 'RSA 签名 (Base64)',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz (biz_id, biz_type)
) COMMENT '独立签名表';
```

签名长度：SHA256withRSA (2048 位密钥) Base64 后约 344 字节，VARCHAR(512) 足够。

## 6. 签名 payload 约定

| 操作 | 前缀 | 格式 | 目的 |
|------|------|------|------|
| 新增草稿 | `C` | `C|id|name|code|price|stock|category|desc` | 全字段防篡改 |
| 修改待确认 | `U` | `U|id|pendingData JSON` | 防 pendingData 快照被换 |
| 删除待确认 | `D` | `D|id|name|code` | 防记录被替换 |
| 已生效 | `A` | `A|id|code` | 最低校验，只防身份置换 |

## 7. 验签优先策略

切面 `doVerify` 内部：

```
if (sys_signature.payload != null && !payload.isBlank()) {
    verify(sys_signature.payload, sys_signature.signature);  // 用存储的 payload 验签
} else {
    verify(signable.buildSignPayload(), sys_signature.signature);  // 兜底：实时计算
}
```

**`storePayload=true` 的必要性**：实体在确认流程中会从 "草稿" 状态切换到 "生效" 状态，`buildSignPayload()` 会返回不同格式的字符串。如果不存原始 payload，confirm 时永远验签失败。

## 8. 切面自动识别范围

切面递归收集以下类型的 Signable 实例：

| 类型 | 处理 |
|------|------|
| 单个 `Signable` | 直接处理 |
| `Collection<Signable>` | 遍历每个元素 |
| `Signable[]` | 遍历每个元素 |
| void 返回值 | 遍历方法参数中的 Signable |

非 Signable 类型静默跳过。

## 9. Order 保证

```java
@Around("@annotation(verify)") @Order(10)   // 先验签
@Around("@annotation(sign)")   @Order(20)   // 后签名
```

同一方法同时加 @Verify + @Sign 时，验签在入参阶段，签名在返回阶段。

## 10. 异常处理

| 场景 | @Sign 行为 | @Verify 行为 |
|------|-----------|-------------|
| `big.license.enabled=false` | 切面根本不注册 | 切面根本不注册 |
| signService 内部未启用 | 静默跳过 (silent=true) | 仍验签 (公钥已加载时) |
| 签名表无记录 | — | 抛异常或静默 |
| payload 不匹配 | — | silent=false 抛 LicenseSecurityException |

## 11. 配置项

### 开发模式（跳过签名）

```yaml
big:
  license:
    enabled: false   # SignatureAutoConfiguration 整体跳过
```

### 生产模式（启用签名）

```yaml
big:
  license:
    enabled: true
    store-pwd: "your-store-password"
    license-path: "./license/license.lic"
    public-certs-path: "./license/publicCerts.store"
    private-store-path: "./license/privateKeys.store"
    key-pwd: "your-key-password"
    private-alias: "privateKey"
```

## 12. 宿主接入 Checklist

1. ✅ pom.xml 引入 big-license + mybatis-plus-spring-boot4-starter + spring-boot-starter-aop
2. ✅ `@MapperScan` 包含 `com.cqcnihao.bigx.license.mapper`
3. ✅ application.yml 设置 `big.license.enabled: true`
4. ✅ 实体类 `implements Signable`（getBizId + buildSignPayload）
5. ✅ 实体类 **不要再加 sign 字段**
6. ✅ Service 写方法加 `@Sign(storePayload = true)`
7. ✅ Service confirm 方法加 `@Verify`
8. ✅ DB 执行 `sys_signature` 表 DDL
9. ✅ 启动确认日志：切面被注册、签名表 upsert 正常

## 13. 新增业务类型 Checklist

1. ✅ `BizType` 枚举加新条目：`PAYMENT("Payment")`
2. ✅ 实体类常量引用：`public static final String BIZ_TYPE = BizType.PAYMENT.code()`
3. ✅ Service `@Sign(bizType = "Payment")` — 可选，不写则切面自动回退 `Signable.getBizType()`
4. ✅ Service `@Verify(bizType = "Payment")` — 同理

## 14. LicenseSignService 公钥加载修复（v1.4.7）

### 现象

```
[LicenseSignService] public alias not found: publicCert
isVerifyEnabled = false
```

### 根因

`publicCerts.store` 通过 `keytool -importcert` 导入，JKS 内部存的是 `trustedCertEntry`。`KeyStore.getKey()` 只查 PrivateKeyEntry 类型条目，碰到 trustedCertEntry 永远返回 null。

### 修复

```java
// 正确：先取证书，再 getPublicKey()
Certificate cert = ks.getCertificate("publicCert");
return cert.getPublicKey();
```

启动日志应变为：

```
[LicenseSignService] init OK | sign=ON
isVerifyEnabled = true
isSignEnabled   = true
```

## 15. 运行验证（big-business 实测）

### 前置条件

- `big.license.enabled=true`（默认值）
- `@MapperScan({"com.cqcnihao.bigx.business.mapper", "com.cqcnihao.bigx.license.mapper"})`
- DB 执行 `sys_signature` 建表 DDL

### 启动日志确认切面装配

```
SignatureAutoConfiguration matched:
   - @ConditionalOnClass found required classes 'org.aspectj.lang.annotation.Aspect',
     'com.cqcnihao.bigx.license.mapper.SysSignatureMapper' (OnClassCondition)
   - @ConditionalOnProperty (big.license.enabled=true) matched (OnPropertyCondition)
```

### 签名链路（create / update）

```
23:57:25.671 [http-nio-8082-exec-10] DEBUG c.c.b.license.aspect.SignatureAspect
    - [@Sign] updated sys_signature id=1           ← upsert 命中旧记录（唯一键 biz_id+biz_type）
    - [@Sign] OK. bizType=Product, bizId=4,
      target=BizProductServiceImpl#update, sign=[MAotoCD4A4RMx9tA...]
```

### 验签链路（confirm）

```
23:58:22.827 [http-nio-8082-exec-9]  DEBUG c.c.b.license.aspect.SignatureAspect
    - [@Verify] OK. bizType=Product, bizId=4,
      target=BizProductServiceImpl#confirm
```

### 完整数据流

```
PUT  /product               → BizProductServiceImpl#update    → @Sign   → upsert sys_signature(biz_id=4, biz_type=Product)
POST /product/4/confirm     → BizProductServiceImpl#confirm   → @Verify → select sys_signature where biz_id=4 → signService.verify → OK
```

切面内部优先策略：`sys_signature.payload` 不为空时用存储值验签（`storePayload=true`），否则实时调用 `Signable.buildSignPayload()` 重算。

### 日志级别调优

默认切面打 `DEBUG` 级日志，生产环境建议：

```yaml
logging:
  level:
    com.cqcnihao.bigx.license: INFO
```

DEBUG 输出签名前 16 字符 + bizType/bizId/方法名；INFO 完全静默；ERROR 仅验签失败时出现。