# BigX 国密加解密协议规范

> 版本: v1.2 · 2026-11-08
> 模块: `big-encrypt` · 前端: `bigxvue/src/utils/smCrypto.js`

---

## 0. 项目结构

```
bigxweb/
├── big-encrypt/          # 国密加解密核心模块（本模块）
│   ├── client/
│   │   └── CryptoConfigVO.java            面向前端的安全配置快照
│   ├── core/
│   │   ├── Sm4.java                       SM4/CBC/PKCS7 · BC 实现
│   │   └── Sm3.java                       SM3 + HMAC-SM3
│   ├── service/
│   │   └── SmCryptoService.java           加解密主服务
│   └── advice/
│       ├── SmDecryptRequestBodyAdvice     请求解密
│       └── SmEncryptResponseBodyAdvice    响应加密
├── big-auth/             # 认证服务（端口 8081）
│   └── @SmEncrypt 类级 + /captcha、/public-key @SmPlain
├── big-business/         # 业务服务（端口 8082）
│   └── @SmEncrypt 类级
├── big-gateway/          # 网关（端口 8080）
│   └── SCG 路由 lb://big-auth | lb://big-business
├── big-web-common/       # 公共模块（签名校验、XSS 过滤等）
└── bigxvue/              # Vue 3 前端
    ├── src/utils/smCrypto.js              国密加解密工具类
    ├── src/api/request.js                 Axios 拦截器集成加密
    └── src/main.js                        启动时拉 /auth/public-key

端口分配:
  8080 → big-gateway   (网关入口，前端只访问这里)
  8081 → big-auth      (认证)
  8082 → big-business  (业务)
  8848 → Nacos         (注册中心)
  5174 → Vite dev      (前端开发服务器)

启动顺序: Nacos → big-auth → big-business → big-gateway → Vite dev
```

---

## 1. 算法选型

| 用途 | 算法 | 参数 |
|---|---|---|
| 对称加密 | **SM4** | CBC 模式 · PKCS7Padding · 128-bit key · 128-bit IV |
| 完整性校验 | **HMAC-SM3** | Encrypt-then-MAC · 256-bit tag |
| 防重放 | timestamp + nonce | timestamp 5 分钟有效期 · nonce 一次性消费 |
| 非对称签名(可选) | SM2/SM3withSM2 | 响应签名，默认关闭 |

**设计决策**: 选择 Encrypt-then-MAC（先加密再算 MAC），比 MAC-then-Encrypt 更安全，避免 padding oracle 攻击。

---

## 2. 请求密文信封 `EncryptedRequest`

前端 → 后端，POST/PUT 请求体自动加密：

```json
{
  "alg": "SM4/CBC/PKCS7Padding",
  "keyId": "v1",
  "cipher": "Base64(IV(16字节) || SM4密文)",
  "mac": "Base64(HMAC-SM3 tag)",
  "timestamp": 1794137225119,
  "nonce": "32位随机hex串"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `alg` | String | 算法变换名，固定 `SM4/CBC/PKCS7Padding` |
| `keyId` | String | 密钥版本号，支持轮换期多版本共存 |
| `cipher` | String | `Base64(IV \|\| ciphertext)` · IV 固定前置 16 字节 |
| `mac` | String | `Base64(HMAC-SM3)` · Encrypt-then-MAC 完整性 tag |
| `timestamp` | Long | 客户端毫秒时间戳，后端校验 5 分钟内有效 |
| `nonce` | String | 32 位随机 hex，防重放（服务端消费后不可重复） |

**请求头标记**: `X-Encrypted: true` — 后端据此跳过明文处理链路。

---

## 3. 响应密文信封 `EncryptedPayload`

后端 → 前端，仅加密 `Result.data`，保留 `success/code/message` 明文：

```json
{
  "success": true,
  "code": "200",
  "message": "操作成功",
  "timestamp": 1794137225119,
  "data": {
    "encrypt": true,
    "alg": "SM4/CBC/PKCS7Padding",
    "keyId": "v1",
    "cipher": "Base64(IV(16字节) || SM4密文)",
    "mac": "Base64(HMAC-SM3 tag)",
    "sign": null
  }
}
```

前端通过 `data.encrypt === true` 判断是否需要解密。

---

## 4. MAC 密钥派生

```
MAC_KEY = SM3("big-encrypt-etm-v1" | keyId) || SM4原始密钥(16字节)
         └──── 32 字节盐值 ────┘  └──── 16 字节原始 key ────┘
         合计 48 字节 → 作为 HMAC-SM3 的 key
```

**MAC 输入串**:
```
mac_payload = alg | keyId | cipherBase64
```

**MAC 计算**:
```
mac = HMAC-SM3(key = MAC_KEY, data = mac_payload.getBytes(UTF-8))
```

**MAC 校验顺序**: 后端先校验 MAC，再解密。Encrypt-then-MAC 语义：未认证的密文绝不送进解密器，避免 padding oracle。

---

## 5. SM4 密文格式

```
SM4-CBC-PKCS7(plaintext, key, iv)
输出: ciphertext_bytes
最终: Base64(iv(16) || ciphertext_bytes)
```

- IV: 16 字节随机数，每次加密独立生成，随密文一起传输
- PKCS7Padding: 明文按 16 字节分组补齐

---

## 6. 密钥下发协议

### 6.1 端点

```
GET /auth/public-key     ← @SmPlain 明文豁免，无需加密
```

**为什么用明文**：这是密钥握手的第一步——前端尚无密钥可用，必须明文拉取。端点加上 `@SmPlain` 注解豁免加密拦截器。

### 6.2 响应结构 `CryptoConfigVO`

```json
{
  "code": "200",
  "success": true,
  "data": {
    "sm4Alg": "SM4/CBC/PKCS7Padding",
    "sm4DefaultKeyId": "v1",
    "sm4Keys": {
      "v1": "ZGV2LXNtNC1kZXYta2V5IQ==",
      "v2": "...(轮换期共存)"
    },
    "macEnabled": true,
    "macBinding": "big-encrypt-etm-v1",
    "sm2PublicKey": "MFkwEw...",
    "sm2KeyId": "v1",
    "sm2Mode": "C1C3C2"
  }
}
```

### 6.3 字段安全分级

| 字段 | 下发 | 说明 |
|---|---|---|
| `sm4Keys` | ✅ 下发 | Base64(rawKey)，dev 环境直接下发 |
| `sm4Alg` / `sm4DefaultKeyId` | ✅ 下发 | 算法选择、当前生效版本 |
| `macBinding` / `macEnabled` | ✅ 下发 | MAC 绑定串（固定常量）、是否启用 MAC |
| `sm2PublicKey` / `sm2KeyId` | ✅ 下发 | SM2 公钥 + 版本号，预留给生产环境 SM2 握手 |
| ~~`sm3.macKey`~~ | ❌ **不下发** | HMAC 独立密钥——下发等于放弃 Encrypt-then-MAC 的安全意义 |
| ~~`sm2.privateKey`~~ | ❌ **不下发** | 绝不下发 |

### 6.4 生产环境安全升级

> dev 环境直接下发 SM4 key 仅为了开发便利。生产环境必须改用 **SM2 混合加密握手**：

```
前端                              后端
  │ fetch('/auth/public-key')       │
  │◀──── sm2PublicKey ───────────── │
  │                                 │
  │ 生成随机 SM4 会话密钥 sessionKey  │
  │ sessionKey = random(16 bytes)   │
  │                                 │
  │ 用 SM2 公钥加密 sessionKey      │
  │ cipher = SM2_Enc(sessionKey)    │
  │                                 │
  │ POST /auth/session             │
  │ body = { sm2CrtCipher }         │
  │                                 │
  │            SM2 私钥解密          │
  │◀──────── 会话建立 ──────────── │
  │                                 │
  │ 后续请求全部用 sessionKey 加密   │
```

VO 里已预留 `sm2PublicKey` / `sm2KeyId` 字段，扩展时新增一个 `/auth/session` 端点即可。

---

## 7. 前后端实现对照

### 后端 (Java · BouncyCastle)

```
big-encrypt/
├── client/
│   └── CryptoConfigVO.java          ← 面向前端的安全配置快照
├── core/
│   ├── Sm4.java                     ← SM4/CBC/PKCS7 · BC 实现
│   └── Sm3.java                     ← SM3 + 手写 HMAC-SM3 (FIPS-198)
├── service/
│   └── SmCryptoService.java
│       ├── encrypt(plaintext, alg, keyId)    → EncryptedPayload
│       ├── decrypt(cipher, alg, keyId, mac)  → 明文 String
│       ├── resolveKey(keyId)                 → byte[16] (从配置读 Base64)
│       ├── resolveMacKey(keyId)              → salt || rawKey
│       ├── mac(alg, keyId, cipher)           → Base64(HMAC-SM3)
│       └── toCryptoConfigVO()                → 下发给前端的快照
└── advice/
    ├── SmDecryptRequestBodyAdvice.java      ← 请求解密 @RequestBodyAdvice
    └── SmEncryptResponseBodyAdvice.java      ← 响应加密 @ResponseBodyAdvice
```

**加密配置** (`application.yml`):
```yaml
big:
  encrypt:
    enabled: true
    replay-check: true
    sm4:
      alg: SM4/CBC/PKCS7Padding
      default-key-id: ${SM4_DEFAULT_KEY_ID:v1}
      keys:
        v1: ${SM4_KEY_V1:ZGV2LXNtNC1kZXYta2V5IQ==}   # 生产用环境变量注入
      mac-enabled: true
    sm3:
      # mac-key 留空则自动由 SM4 key 派生；也可独立配置
      mac-key: ${SM3_MAC_KEY:}
```

**注解标记**:
```java
@SmEncrypt                    // 类级/方法级：启用国密加解密
@SmPlain                      // 明文豁免（/captcha、/public-key）
```

### 前端 (Vue · sm-crypto)

```javascript
// ========= main.js 启动阶段 =========
// 先拉密钥 → 再 mount 应用，确保加密模块就绪
bootstrapCrypto()
  ├─ sessionStorage 有缓存？→ loadCryptoConfigFromCache() ✅
  └─ fetch('/auth/public-key') 明文拉取
       └─ smCrypto.initCryptoConfig(data)
            ├─ runtimeKeys   ← {v1: base64Key, v2: ...}
            ├─ runtimeAlg   ← "SM4/CBC/PKCS7Padding"
            └─ sessionStorage.setItem('crypto_keys', ...)

// ========= smCrypto.js 核心 API =========
smCrypto.isReady                     // 启动时可轮询等待
smCrypto.initCryptoConfig(vo)        // 接收后端下发的 CryptoConfigVO
smCrypto.loadCryptoConfigFromCache() // sessionStorage 恢复
smCrypto.encrypt(plainText, keyId?)  // → EncryptedRequest 信封
smCrypto.decrypt(envelope, keyId?)   // → UTF-8 明文字符串
smCrypto.decryptPayload(data)        // → 自动解密响应 data 字段
```

**集成点** (`request.js`):
```
请求拦截器: smCrypto.isReady
             ├─ true  → POST/PUT 自动加密 → config.data = smCrypto.encrypt(jsonStr)
             └─ false → 抛错或降级（取决于业务）
响应拦截器: res.data.encrypt === true → res.data = smCrypto.decryptPayload(res.data)
```

---

## 8. sm-crypto 库 API 踩坑备忘

> sm-crypto 是纯 JS 国密库，但 API 有类型自动转换，极易误用。

### 8.1 `sm4.encrypt / sm4.decrypt`

```javascript
// ✅ 正确
sm4.encrypt(utf8String, keyHex, {mode:'cbc', iv: ivHex})
sm4.decrypt(cipherHex,  keyHex, {mode:'cbc', iv: ivHex})
// 输入/输出均为字符串：encrypt 输入当作 UTF-8 文本，输出 HEX
//                    decrypt 输入当作 HEX 密文，输出 UTF-8 文本

// ❌ 绝对禁止！多做一层 hex 转换
const plainHex = bytesToHex(utf8ToBytes(jsonStr))
sm4.encrypt(plainHex, keyHex, ...)
// sm4 内部会把 plainHex 当作 UTF-8 文本加密
// → 明文膨胀 2x，后端解密得到的是 hex 字符串而非 JSON！
```

### 8.2 `sm3 / hmac`

```javascript
// ✅ 正确：只接受 byte array，返回 byte array
sm3(byteArray)                              // → Array<number>[32]
hmac(dataByteArray, keyByteArray)           // → Array<number>[32]

// ❌ sm3 没有 hex 字符串版本！
sm3("hello")        // "hello" 会被当成 char codes [104,101,108,108,111] 处理
```

### 8.3 导入路径

```javascript
// ✅ sm3 + hmac 必须从源码子路径导入
import sm2Sm3 from 'sm-crypto/src/sm2/sm3.js'

// ❌ 顶层模块只导出 sm4/sm2
import { sm3 } from 'sm-crypto'        // sm3 未暴露在顶层
```

---

## 9. 故障排查

| 错误码 / 现象 | 含义 | 排查方向 |
|---|---|---|
| `SM4 密钥未初始化` | 前端未调用 `initCryptoConfig()` | 检查 `/auth/public-key` 是否可达、`smCrypto.isReady` 是否为 false |
| `CRY-4004` MAC 校验失败 | MAC tag 不匹配 | 1) 前端 `runtimeKeys[keyId]` 是否与后端一致<br>2) mac_payload 拼接格式 `alg\|keyId\|cipher`<br>3) MAC key 派生是否也是 `salt\|rawKey`<br>4) sm-crypto 的 `sm3()` 返回 byte array 不是 hex |
| `CRY-4001` 密钥缺失 | 后端找不到 keyId 对应密钥 | `big.encrypt.sm4.keys.{keyId}` 未配置或 Base64 解码后长度 ≠ 16 |
| `CRY-4005` 请求已过期 | timestamp 偏差超 5 分钟 | 检查客户端系统时间 |
| `HttpMessageNotReadableException` | JSON parse error | 解密后不是合法 JSON。典型原因：前端多做了 hex 转换 |
| `padding is invalid` | SM4 padding 错误 | 密钥不对 → 解密得到垃圾 → PKCS7 校验失败。优先排查 keyId 和 Base64 解码 |
| 前端 `/auth/public-key` 403 | 直连后端绕过网关 | 必须通过网关端口访问；dev 环境走 Vite 代理 |
| 网关请求超时（15s+） | SCG 连不到下游 | **见 9.1 Nacos IP 问题** |
| `Service Unavailable 503` | 网关没找到实例 | Nacos 注册中心无服务实例或 IP 错误 |

### 9.1 Nacos 注册 IP 问题（高频坑）

**症状**：网关启动正常、Nacos 控制台能看到服务注册、但所有通过网关的请求都超时。网关日志：
```
ConnectException: Connection timed out: no further information: /10.69.120.164:8081
```

**根因**：Windows 多网卡机器上，Nacos 自动选了虚拟网卡 IP（如 WSL/Hyper-V 的 `10.x.x.x`），而非实际通信网卡 IP。SCG 路由到虚拟网卡 IP 自然不通。

**修复**：所有后端服务的 `application.yml` 都必须显式指定：
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        ip: ${NACOS_IP:192.168.115.164}   # ← 关键：写死真实网卡 IP
        prefer-ip-address: true
```

**快速定位**：
```powershell
# 查看本机所有 IP
Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'}

# 查 Nacos 里服务注册的 IP（对比本机实际 IP）
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=big-auth
```

重启后验证：
```
# 正确日志
nacos registry, DEFAULT_GROUP big-auth 192.168.115.164:8081 register finished
#                                ^^^^ 必须是真实 IP
```

### 常见日志诊断

```
// ✅ 正常: SM4 解密成功
SmDecryptRequestBodyAdvice - SM4 解密成功, uri=/auth/login, 明文长度=115

// ❌ 解密后明文是 hex 字符串（前端多做了 hex 转换）
// 后端解密预览: 7b22757365726e616d65223a2261646d696e...
// Jackson 解析 JSON 失败: Unexpected character ('b')

// ❌ MAC 校验失败但解密能过（说明密钥对了但 MAC 计算错了）
SmDecryptRequestBodyAdvice - MAC 校验失败
```

---

## 10. 密钥轮换

配置多个 keyId，`default-key-id` 指定当前生效版本：
```yaml
default-key-id: v2
keys:
  v1: old-base64-key...     # 过渡期仍可解密旧客户端
  v2: new-base64-key...     # 新客户端发 v2
```

前端从 `/auth/public-key` 一次性拿到所有 keyId：
```
sm4Keys: { v1: oldBase64, v2: newBase64 }
```

加密用 `sm4DefaultKeyId`（当前 v2），解密时按响应信封里带的 `keyId` 字段选对应密钥——老响应仍能用 v1 解开。

后端 `resolveMacKey(keyId)` 根据信封里带的 keyId 选对应密钥。

### MAC key 独立配置

```yaml
sm3:
  mac-key: ${SM3_MAC_KEY:}   # 配置后优先使用，不再由 SM4 key 派生
```

适合 HSM 托管场景，MAC key 与 SM4 key 分离。**注意**：若启用独立 MAC key，必须通过专用通道下发给前端，绝不能复用 `/auth/public-key`。

---

## 11. 性能参考

| 操作 | 耗时 | 备注 |
|---|---|---|
| SM4-CBC 加密 1KB | ~0.3ms (BC) / ~0.5ms (sm-crypto) | BouncyCastle 有 JIT 加速 |
| HMAC-SM3 | ~0.4ms | 48 字节 key + 几百字节 payload |
| 总开销 (加密+MAC) | ~1ms | 相比网络 RTT 可忽略 |
| 响应解密+MAC 校验 | ~1ms | 同上 |
| `/auth/public-key` 拉取 | ~50ms | 首次启动一次，sessionStorage 缓存 |

国密算法在现代 CPU 上不构成瓶颈，真正耗时的是网关转发和数据库 I/O。

---

## 12. 架构总图

```
                    浏览器启动
                        │
               ┌────────▼────────┐
               │ sessionStorage   │
               │ crypto_keys?    │
               └────┬──────┬─────┘
                    是     否
                    │      │
     loadCache() ◀──┘      ▼ fetch('/auth/public-key')
               ┌─────────────────────┐
               │  big-auth (明文)     │
               │  @SmPlain 豁免       │
               │  SmCryptoService     │
               │  .toCryptoConfigVO() │
               └─────────┬───────────┘
                         │ 返回 CryptoConfigVO
                         ▼
                 smCrypto.initCryptoConfig()
                         │
                    应用 mount ✅


┌──────────┐  X-Encrypted:true   ┌──────────────┐
│ bigxvue  │ ──────────────────▶ │  big-gateway │
│ (前端)   │    EncryptedRequest  │   (网关)     │
│          │  ◀────────────────── │              │
└──────────┘    EncryptedPayload  └──────┬───────┘
                                         │ 转发
                               ┌─────────▼─────────┐
                               │    big-auth        │
                               │  @SmEncrypt        │
                               │  /auth/login       │
                               └─────────┬─────────┘
                                         │
                    SmDecryptRequestBodyAdvice
                    ┌────────────────────┴────────────────────┐
                    │ MAC 校验 → SM4 解密 → Jackson 反序列化     │
                    └──────────────────────────────────────────┘
                                         │
                               Controller 处理
                                         │
                    SmEncryptResponseBodyAdvice
                    ┌────────────────────┴────────────────────┐
                    │ Result.data → 序列化 → SM4 加密 → 信封包装│
                    └──────────────────────────────────────────┘
```

---

*文档由项目实战踩坑总结而来，每一个参数选择都是调试验证过的结果。*