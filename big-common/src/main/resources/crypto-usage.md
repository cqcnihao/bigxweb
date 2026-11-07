# 加密/解密功能使用指南

## 1. 配置加密功能

在`big-gateway/src/main/resources/application.yml`中配置：

```yaml
crypto:
  aes:
    enabled: true
    secret-key: "your-base64-encoded-secret-key"
    # algorithm: "AES/GCM/NoPadding"
```

## 2. 生成密钥

使用以下代码生成AES密钥：

```java
String key = AesCryptoUtil.generateKey();
System.out.println("密钥: " + key);
```

## 3. 在服务中使用

```java
@Service
public class MyService {
    
    @Autowired
    private CryptoService cryptoService;
    
    public String processSensitiveData(String data) {
        // 加密
        String encrypted = cryptoService.encrypt(data);
        
        // 解密
        String decrypted = cryptoService.decrypt(encrypted);
        
        return decrypted;
    }
}
```

## 4. 网关层集成

在`big-gateway`模块中，可以使用`CryptoFilter`来实现：
- 请求体自动解密
- 响应体自动加密

### 4.1 在路由中启用 CryptoFilter

在 `big-gateway/src/main/resources/application.yml` 的 `spring.cloud.gateway.routes` 中为需要加解密的路由添加 `CryptoFilter`：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**
          filters:
            - CryptoFilter=decrypt,encrypt  # 同时启用解密+加密
            # 或单独启用：
            # - CryptoFilter=decrypt
            # - CryptoFilter=encrypt
```

> ✅ `CryptoFilter` 会自动识别以下 Header 控制行为：
> - `X-Verify-Signature: true` → 触发验签（必须在 `X-Encrypt-Response` 之前）
> - `X-Encrypt-Response: true` → 触发响应体加密

### 4.2 请求体格式（客户端发送）

请求体必须为标准 JSON 结构，支持验签 + 解密：

```json
{
  "encryptedData": "base64-encoded-ciphertext",
  "signature": "base64-hmac-signature",
  "algorithm": "AES/GCM/NoPadding",
  "timestamp": 1715089234000
}
```

> ⚠️ 顺序强制：`CryptoFilter` 内部严格按 **验签 → 解密 → 业务处理 → 加密响应** 执行，不可跳过或颠倒。

### 4.3 响应体格式（服务端返回）

启用 `X-Encrypt-Response: true` 后，响应体将被自动加密并包装为：

```json
{
  "encryptedData": "base64-encoded-ciphertext",
  "algorithm": "AES/GCM/NoPadding"
}
```

## 5. 安全注意事项

- 密钥必须安全存储，建议使用配置中心或环境变量
- 生产环境禁用明文密钥配置
- 定期轮换密钥
- 使用GCM模式提供认证加密，防止篡改
