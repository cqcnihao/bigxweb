# BigX 国密加解密协议规范

> 版本: v1.3 · 2026-11-14
> 模块: `big-encrypt` · 前端: `bigxvue/src/utils/smCrypto.js`
>
> v1.3 变更：请求签名改为**客户端密钥体系**（浏览器本地生成 SM2 密钥对）；响应签名**在 auth/business 已开启**；
> 新增启动期私钥守卫；未签名时信封不再输出 `"sign": null`。

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
| 请求签名 | **SM2/SM3withSM2** | 客户端私钥签、服务端按 `clientKeyId` 取公钥验；代码默认 `verify-signature=false`，auth/business 的 yml 已显式设为 `true` |
| 响应签名 | **SM2/SM3withSM2** | 服务端全局私钥签、前端全局公钥验；代码默认 `signResponse=false`，auth/business 的 yml 已显式设为 `true`（`sign-response`） |

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
  "sign": "Base64(SM2 签名)",
  "clientKeyId": "9f3c...16字节hex",
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
| `sign` | String | `Base64(SM2 签名)` · **必填**（`verify-signature=true` 时），缺失直接 `CRY-4003` |
| `clientKeyId` | String | 服务端登记时签发的公钥标识，服务端据此取公钥验签 |
| `timestamp` | Long | 客户端毫秒时间戳，后端校验 5 分钟内有效 |
| `nonce` | String | 32 位随机 hex，防重放（服务端消费后不可重复） |

**签名原文**（请求侧，含 nonce 以绑定防重放参数）：
```
sign_payload = timestamp + "\n" + nonce + "\n" + cipher
```

**严格验签语义**：`decision.isSign() == true` 时信封**必须**带 `sign`，缺失一律抛 `CRY-4003`。
不存在「没签名就放行」的宽松模式——否则攻击者只要删掉 `sign` 字段即可绕过校验。

**请求头标记**: `X-Encrypted: true` — 后端据此跳过明文处理链路。

---

## 3. 响应密文信封 `EncryptedPayload`

后端 → 前端，仅加密 `Result.data`，保留 `success/code/message` 明文：

```json
{
  "success": true,
  "code": "200",
  "message": "操作成功",
  "timestamp": 1794659968615,
  "data": {
    "encrypt": true,
    "alg": "SM4/CBC/PKCS7Padding",
    "keyId": "v1",
    "cipher": "Base64(IV(16字节) || SM4密文)",
    "mac": "Base64(HMAC-SM3 tag)",
    "sign": "Base64(SM2 签名)",
    "timestamp": 1794659968616
  }
}
```

| 字段 | 说明 |
|---|---|
| `encrypt` | 固定 `true`，前端据此判断是否需要解密 |
| `sign` | `sign-response=true` 时存在；**关闭时该字段整个省略，不会出现 `"sign": null`** |
| `timestamp` | 信封内时间戳，**参与签名**，与外层 `Result.timestamp` 是两个独立值（毫秒级可能差 1） |

**响应签名原文**（与请求侧对称，但**不含 nonce**——响应没有防重放需求）：
```
sign_payload = timestamp + "\n" + cipher
```

> ⚠️ 注意取的是**信封内**的 `data.timestamp`，不是外层 `Result.timestamp`。
> 两者相差 1 毫秒，取错会导致「签名看着完全正常却 100% 验不过」，极难排查。

**两道独立防线**：`mac` 防密文被改（对称密钥，前端能算能验）；`sign` 防整包被替换（非对称，前端只能验不能伪造）。
两者都要过，缺一不可。

**为什么前端不能自己签名**：响应签名必须由服务端私钥产生，前端只持有公钥。
因此响应签名用**全局密钥对**（`sm2.public-key` / `sm2.private-key`），
与请求侧「每个客户端一把密钥」的体系完全独立。

**信封字段省略规则**：`EncryptedPayload` 标注了 `@JsonInclude(NON_NULL)`，
未签名时直接不输出 `sign` 键，而不是输出 `"sign": null`。
（历史 bug：前端见 `sign` 字段存在但值为 null，会误判「有签名」而验签失败。）

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
GET  /auth/public-key              ← @SmPlain 明文豁免，无需加密（配置下发 + 公钥下发）
POST /crypto/sm2/client-key        ← @SmPlain 明文豁免，登记客户端公钥
DELETE /crypto/sm2/client-key/{id} ← @SmPlain 明文豁免，注销客户端公钥
```

**为什么用明文**：这是密钥握手的第一步——前端尚无密钥可用，必须明文拉取。端点加上 `@SmPlain` 注解豁免加密拦截器。

**为什么客户端密钥登记端点也必须明文**：首次登记时客户端还没有 `clientKeyId`，
若该端点也要求信封加密 + 签名，就会陷入「要验签必须先登记、要登记必须先验签」的死循环。
公钥本身不是秘密，明文传输不降低安全性；真正的机密性由后续业务报文的 SM4 信封承担。

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
| `sm2PublicKey` / `sm2KeyId` | ✅ 下发 | **响应签名验签用**：前端据此校验响应确实来自本服务端 |
| ~~`sm3.macKey`~~ | ❌ **不下发** | HMAC 独立密钥——下发等于放弃 Encrypt-then-MAC 的安全意义 |
| ~~`sm2.privateKey`~~ | ❌ **不下发** | 绝不下发 |

### 6.4 客户端密钥体系（严格验签的落地前提）

请求验签要求「每个加密请求都带合法 SM2 签名」。但浏览器**不可能持有服务端私钥**，
若把服务端私钥下发到前端，等于把签名能力交给所有人，验签彻底失效。因此请求侧改用**客户端自持密钥**：

```
浏览器                                      服务端
  │ 1. sm2.generateKeyPair()                  │
  │    私钥存 localStorage（永不上传）        │
  │                                           │
  │ 2. POST /crypto/sm2/client-key            │
  │    body = { publicKey: "04||X||Y" }       │
  │                                           │
  │ 3. 返回 { clientKeyId, expiresIn }        │
  │◀──────────────────────────────────────────│
  │                                           │
  │ 4. 请求：用本地私钥签名                    │
  │    sign = SM2_Sign(ts+"\n"+nonce+"\n"+cipher)
  │    信封带 clientKeyId                     │
  │──────────────────────────────────────────▶│
  │                                           │
  │                    5. 按 clientKeyId 取公钥验签
```

**关键约束**：

| 约束 | 原因 |
|---|---|
| `clientKeyId` 由服务端随机生成，**不接受客户端指定** | 否则攻击者可用他人 id 覆盖公钥，冒充对方 |
| 公钥登记端点 `@SmPlain` 明文 | 避免「验签需先登记、登记需先验签」的死循环 |
| 公钥归一化为 X.509 后存储 | 浏览器产的是裸公钥点 hex（`04‖X‖Y`），服务端统一转换 |
| `clientKeyTtlSeconds` 默认 86400 | 过期后服务端查不到公钥 → 所有加密请求失败，前端须在过期前主动续期 |
| 登记公钥 ≠ 身份认证 | 只证明「持有对应私钥」；身份仍由 Sa-Token 登录态承担 |

### 6.5 生产环境安全升级

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
│   ├── Sm3.java                     ← SM3 + 手写 HMAC-SM3 (FIPS-198)
│   └── Sm2.java                     ← SM2 签名/验签 · BC SM3withSM2
├── controller/
│   ├── Sm2KeyController.java        ← GET  公钥 + 配置下发
│   └── ClientKeyController.java     ← POST 登记客户端公钥 / DELETE 注销
├── support/
│   ├── ClientKeyStore.java          ← 客户端公钥存储接口
│   ├── InMemoryClientKeyStore.java  ← 单机实现
│   └── RedisClientKeyStore.java     ← 集群实现（多实例共享）
├── service/
│   └── SmCryptoService.java
│       ├── encrypt(plaintext, alg, keyId)      → EncryptedPayload（含 sign）
│       ├── decrypt(cipher, alg, keyId, mac)    → 明文 String
│       ├── signPayload(ts, nonce, cipher)      → 请求签名原文
│       ├── signPayload(ts, cipher)             → 响应签名原文
│       ├── sign(raw) / verify(raw, sign, id)   → SM2 签名/验签
│       ├── registerClientKey(publicKey)        → clientKeyId
│       ├── resolveClientPublicKey(id)          → X.509 公钥
│       ├── resolveKey(keyId)                   → byte[16] (从配置读 Base64)
│       ├── resolveMacKey(keyId)                → salt || rawKey
│       ├── mac(alg, keyId, cipher)             → Base64(HMAC-SM3)
│       └── toCryptoConfigVO()                  → 下发给前端的快照
└── advice/
    ├── SmDecryptRequestBodyAdvice.java      ← 请求解密 + MAC + 验签
    └── SmEncryptResponseBodyAdvice.java     ← 响应加密 + 签名
```

**加密配置** (`application.yml`):
```yaml
big:
  encrypt:
    enabled: true
    replay-check: true
    # 响应签名：每个加密响应都附带 SM2 签名，前端据此验证响应来源
    sign-response: ${SM2_SIGN_RESPONSE:true}
    sm4:
      alg: SM4/CBC/PKCS7Padding
      default-key-id: ${SM4_DEFAULT_KEY_ID:v1}
      keys:
        v1: ${SM4_KEY_V1:ZGV2LXNtNC1kZXYta2V5IQ==}   # 生产用环境变量注入
      mac-enabled: true
    sm2:
      # 严格验签：信封必须带签名，缺失直接拒绝
      verify-signature: ${SM2_VERIFY_SIGN:true}
      # 客户端密钥体系：浏览器本地生成密钥对，登记公钥后按 clientKeyId 验签
      client-key-enabled: ${SM2_CLIENT_KEY_ENABLED:true}
      client-key-ttl-seconds: ${SM2_CLIENT_KEY_TTL:86400}
      # 全局密钥对：仅用于「响应签名」（前端只持有公钥）
      # dev 兜底值见 big-auth / big-business 的 application.yml（两服务共用同一对，
      # 前端只需一份公钥）；生产必须注入 SM2_PUBLIC_KEY / SM2_PRIVATE_KEY，
      # prod profile 检测到该 dev 默认私钥会直接拒绝启动。
      public-key: '${SM2_PUBLIC_KEY:<dev 兜底，见 yml>}'
      private-key: '${SM2_PRIVATE_KEY:<dev 兜底，见 yml>}'
    sm3:
      # mac-key 留空则自动由 SM4 key 派生；也可独立配置
      mac-key: ${SM3_MAC_KEY:}
```

> 公钥/私钥值必须加**单引号**：Base64 里的 `+` `/` `=` 在 YAML 中容易被误解析。

**注解标记**:
```java
@SmEncrypt                    // 类级/方法级：启用国密加解密
@SmPlain                      // 明文豁免（/captcha、/public-key、/client-key）
```

### 前端 (Vue · sm-crypto)

```javascript
// ========= main.js 启动阶段 =========
// 先拉密钥 → 再 mount 应用，确保加密模块就绪
bootstrapCrypto()
  ├─ ensureClientKeyPair()      // 本地生成 SM2 密钥对（私钥存 localStorage）
  ├─ ensureClientKeyRegistered()// 登记公钥，拿 clientKeyId
  ├─ sessionStorage 有缓存？→ loadCryptoConfigFromCache() ✅
  │    └─ 缓存里没有 sm2PublicKey？→ 补拉一次 /auth/public-key
  └─ fetch('/auth/public-key') 明文拉取
       └─ smCrypto.initCryptoConfig(data)
            ├─ runtimeKeys        ← {v1: base64Key, v2: ...}
            ├─ runtimeAlg        ← "SM4/CBC/PKCS7Padding"
            ├─ runtimeSm2RawPointHex ← 服务端公钥（已转裸点 hex）
            └─ sessionStorage.setItem('crypto_keys', ...)

// ========= smCrypto.js 核心 API =========
smCrypto.isReady                     // 启动时可轮询等待
smCrypto.initCryptoConfig(vo)        // 接收后端下发的 CryptoConfigVO
smCrypto.loadCryptoConfigFromCache() // sessionStorage 恢复
smCrypto.ensureClientKeyPair()       // 本地生成/读取客户端密钥对
smCrypto.ensureClientKeyRegistered() // 登记公钥（过期自动续期）
smCrypto.encrypt(plainText, keyId?)  // → EncryptedRequest 信封（含 sign + clientKeyId）
smCrypto.decrypt(envelope, keyId?)   // → UTF-8 明文字符串
smCrypto.decryptPayload(data)        // → 自动解密响应 data 字段
smCrypto.verifyResponseSign(env)     // → 校验响应签名（返回 boolean）
smCrypto.hasServerSm2PublicKey()     // → 是否已装载服务端公钥
```

**集成点** (`request.js`):
```
请求拦截器: smCrypto.isReady
             ├─ true  → POST/PUT 自动加密（含 SM2 签名 + clientKeyId）
             └─ false → 抛错或降级（取决于业务）

响应拦截器（顺序不能颠倒：先验签，再解密）:
  1. verifyResponse(envelope)
       ├─ 信封无 sign        → 放行（后端可能未开启响应签名）
       ├─ 有 sign 但无公钥   → 抛错拒绝（否则删掉公钥即可绕过验签）
       ├─ 有 sign 且验签失败 → 抛错拒绝（报文可能被篡改）
       └─ 验签通过          → 继续
  2. smCrypto.decryptPayload(envelope)
```

> ⚠️ **为什么「有签名但没公钥」必须拒绝而不能放行**：
> 若放行，攻击者只需让前端拿不到公钥（或篡改配置响应）即可让验签整体失效，
> 这条防线形同虚设。宁可报错让用户刷新，也不能静默跳过。

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

### 8.4 `sm2.doVerifySignature`

```javascript
// ✅ 正确
sm2.doVerifySignature(原文String, 签名hex, 公钥hex, { der: true, hash: true })
// - 签名必须是 **hex 字符串**（服务端下发的是 Base64，需先转 hex）
// - 公钥必须是 **裸公钥点 hex**（04||X||Y 共 65 字节 = 130 个 hex 字符）
// - der: true  → 服务端 BouncyCastle 输出的是 DER 编码签名
// - hash: true → 内部自动做 SM3 摘要（传原文而非摘要值）

// ❌ 三个高频错误
sm2.doVerifySignature(payload, signBase64, pubHex, ...)  // 签名没转 hex
sm2.doVerifySignature(payload, signHex, x509Base64, ...) // 公钥没转裸点
sm2.doVerifySignature(payload, signHex, pubHex, {der:true, hash:false})
                                                         // hash:false 需自己先算 SM3
```

**X.509 Base64 → 裸公钥点**：SM2/P-256 的 SubjectPublicKeyInfo 末尾固定是 65 字节未压缩点，
直接取尾部即可，无需引入 ASN.1 解析库：

```javascript
const der = base64ToBytes(x509Base64)
const point = der.subarray(der.length - 65)   // 04 || X(32) || Y(32)
if (point.length === 65 && point[0] === 0x04) {
  rawPointHex = bytesToHex(point)
}
```

> 这个「取尾部 65 字节」的技巧只适用于曲线点格式为未压缩的 SM2/P-256。
> 若换曲线（如 P-384 或压缩点），长度和前缀都会变，必须改用真正的 ASN.1 解析。

---

## 9. 故障排查

| 错误码 / 现象 | 含义 | 排查方向 |
|---|---|---|
| `SM4 密钥未初始化` | 前端未调用 `initCryptoConfig()` | 检查 `/auth/public-key` 是否可达、`smCrypto.isReady` 是否为 false |
| `未配置 SM4 密钥: keyId=undefined` | 前端拿到的 `sm4Keys` 是**空对象**（`Object.keys({})[0]` 为 undefined） | 1) 服务端 yml 的 `big.encrypt.sm4.keys` 是否被 YAML 层级错误挂到了别的根级 key 下（对比接口返回与 EncryptProperties 默认值）<br>2) `target/classes` 是否为旧版本（IDEA 未重新构建资源）<br>3) 浏览器 sessionStorage 旧缓存（main.js 已改为每次启动强制拉取） |
| `CRY-4001` 解密失败 | 密文无法解密 | 密钥不匹配 → 优先排查 `keyId` 与 Base64 解码后长度 ≠ 16 |
| `CRY-4003` 签名无效 | 请求信封签名缺失或不匹配 | 1) 信封是否带 `sign`（严格模式下**必填**）<br>2) `clientKeyId` 是否已登记且未过期<br>3) 签名原文是否为 `ts\nnonce\ncipher`<br>4) 客户端私钥与登记公钥是否配对 |
| `CRY-4004` MAC 校验失败 | MAC tag 不匹配 | 1) 前端 `runtimeKeys[keyId]` 是否与后端一致<br>2) mac_payload 拼接格式 `alg\|keyId\|cipher`<br>3) MAC key 派生是否也是 `salt\|rawKey`<br>4) sm-crypto 的 `sm3()` 返回 byte array 不是 hex |
| `CRY-4005` 请求已过期 | timestamp 偏差超 5 分钟 | 检查客户端系统时间 |
| `CRY-4006` 重复请求 | nonce 已被消费 | 正常防重放行为；重试须换新 nonce |
| `CRY-4007` 信封非法 | 缺 `cipher` 等必填字段 | 请求体是否被别的拦截器改写；检查 `Content-Type` |
| `CRY-4008` 密钥缺失 | 后端找不到对应密钥/私钥 | `big.encrypt.sm4.keys.{keyId}` 未配置；或 `sign-response=true` 但 `sm2.private-key` 为空 |
| `CRY-4009` 配置错误 | 加密配置不合法 | 检查 `client-key-enabled` 与密钥存储是否初始化 |
| **响应 500 且业务已执行** | 响应阶段签名失败 | 最常见：`sign-response=true` 但私钥为空/非法。**启动期守卫已覆盖此场景**，若仍出现请检查环境变量是否在运行期被覆盖 |
| 前端提示「缺少服务端 SM2 公钥」 | 服务端签了名但前端没拿到公钥 | 1) `/auth/public-key` 响应里 `sm2PublicKey` 是否为空<br>2) `sm2.public-key` 是否配置<br>3) sessionStorage 是否残留旧缓存 |
| 前端提示「响应签名校验失败」 | 签名与密文不匹配 | 1) 服务端签名用的私钥与下发公钥是否同一对（**最常见**）<br>2) 验签原文是否用了信封内 `data.timestamp` 而非外层 `timestamp`<br>3) 是否有中间代理改写了响应体 |
| `HttpMessageNotReadableException` | JSON parse error | 解密后不是合法 JSON。典型原因：前端多做了 hex 转换 |
| `padding is invalid` | SM4 padding 错误 | 密钥不对 → 解密得到垃圾 → PKCS7 校验失败。优先排查 keyId 和 Base64 解码 |
| 前端 `/auth/public-key` 403 | 直连后端绕过网关 | 必须通过网关端口访问；dev 环境走 Vite 代理 |
| 网关请求超时（15s+） | SCG 连不到下游 | **见 9.1 Nacos IP 问题** |
| `Service Unavailable 503` | 网关没找到实例 | Nacos 注册中心无服务实例或 IP 错误 |
| 多实例部署下签名随机失败 | 客户端公钥只存在某台机器内存 | 使用 `RedisClientKeyStore`，勿用 `InMemoryClientKeyStore` |

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

> **⚠️ 纯浏览器（bigxvue）场景不能启用独立 MAC key**：浏览器端 MAC 由 SM4 密钥按 §4 的派生式计算，
> 独立 `sm3.mac-key` 不经 `/auth/public-key` 下发，服务端启用后与前端派生值必然不一致，
> 所有加密请求报 CRY-4004。独立 MAC key 仅适用于服务端到服务端的调用方。
> （2026-11-17 实测确认，见根目录《测试过程记录.md》§5.5 遗留项。）

### SM2 密钥轮换

响应签名的密钥轮换需要「新旧公钥同时下发」的过渡期，否则前端换公钥瞬间会验签失败：

1. 生成新密钥对，先只改服务端 `sm2.private-key` 为**新私钥**；
2. 同时让前端能拿到**新公钥**（`/auth/public-key` 的 `sm2PublicKey` 改为新公钥）；
3. 旧会话缓存里是旧公钥 → 会验签失败，需用户刷新（`sessionStorage` 缓存会随页面关闭失效）。

> 与 SM4 的 `sm4Keys` 多版本共存不同，`sm2PublicKey` 只下发**单个**当前公钥，
> 因此 SM2 轮换是「一刀切」而非平滑过渡。若业务不能接受短暂失败，
> 需扩展 `CryptoConfigVO` 支持 `sm2PublicKeys` 多版本 + 信封带 `sm2KeyId`。

---

## 10.1 生产环境部署清单

> ⚠️ **dev 默认密钥已硬编码在代码库中，任何人可见。生产沿用等于把加密和签名能力完全公开。**

### 必须注入的环境变量

| 变量 | 说明 | 缺失后果 |
|---|---|---|
| `SM4_KEY_V1` | 16 字节 Base64 SM4 密钥 | prod 守卫拒绝启动 |
| `SM2_PUBLIC_KEY` | X.509 Base64 SM2 公钥 | 前端无法验签响应 |
| `SM2_PRIVATE_KEY` | PKCS#8 Base64 SM2 私钥 | **prod 守卫拒绝启动**（若开启响应签名） |
| `SM3_MAC_KEY` | 可选，独立 MAC key | 自动由 SM4 key 派生 |

### 启动期守卫（fail-fast）

`BigEncryptAutoConfiguration` 在 `@PostConstruct` 阶段执行三层检查，
**任意一层不通过都直接拒绝启动**，而不是等运行期返回 500：

| 检查项 | 触发条件 | 适用 profile |
|---|---|---|
| 响应签名私钥必填 | `sign-response=true` 且 `private-key` 为空 | **全部** |
| 禁止 dev 默认私钥 | `sign-response=true` 且私钥等于代码库中的 dev 值 | **仅 prod** |
| 私钥可用性探测 | 真签一次样例报文，签不出来即失败 | **全部** |
| 验签公钥来源 | `verify-signature=true` 且关闭客户端密钥体系又没配 `public-key` | **仅 prod** |

> 为什么要在启动期检查：这类配置遗漏会导致「业务逻辑全部执行完毕，在返回响应时才抛异常」，
> 表现为**接口 500 但数据库已写入**，排查成本极高。宁可起不来，也不要带着必炸配置上线。

### 生成生产密钥对

```bash
# 用项目内 Sm2 工具类（BouncyCastle）生成
java -cp big-encrypt.jar com.cqcnihao.bigx.encrypt.core.Sm2
# 输出：公钥(X.509 Base64) + 私钥(PKCS#8 Base64)
```

或使用 OpenSSL 3.x：
```bash
openssl ecparam -name SM2 -genkey -noout -out sm2.key
openssl pkcs8 -topk8 -nocrypt -in sm2.key -out sm2-pkcs8.pem
openssl ec -in sm2.key -pubout -out sm2-pub.pem
# 去掉 PEM 头尾与换行，得到 Base64 字符串
```

### 部署顺序（重要）

开启响应签名后，前后端**必须按顺序发布**：

```
1. 先发前端（新版兼容「响应无 sign」，旧后端不签名也能正常工作）
2. 再发后端（开启 sign-response，响应开始带 sign）
```

反过来（先开后端签名、后发前端）不会出错——旧前端会忽略多余的 `sign` 字段。
但若前端是新版、后端签名用的私钥与下发公钥不配对，则**所有加密接口立即 500 前端验签失败**，
因此密钥对必须成对配置、且两个服务（auth / business）用**同一对**（前端只需一份公钥）。

### 多实例部署

客户端公钥必须存 Redis，不能用 `InMemoryClientKeyStore`：

```yaml
# 自动装配：存在 RedisConnectionFactory 时用 RedisClientKeyStore
big:
  encrypt:
    sm2:
      client-key-enabled: true
```

否则请求被负载均衡到另一台机器时，会因「查不到 clientKeyId 对应公钥」而抛 `CRY-4003`，
表现为**间歇性签名失败**（同一用户刷新几次有几次成功），这是最难排查的一类故障。

---

## 11. 性能参考

| 操作 | 耗时 | 备注 |
|---|---|---|
| SM4-CBC 加密 1KB | ~0.3ms (BC) / ~0.5ms (sm-crypto) | BouncyCastle 有 JIT 加速 |
| HMAC-SM3 | ~0.4ms | 48 字节 key + 几百字节 payload |
| 总开销 (加密+MAC) | ~1ms | 相比网络 RTT 可忽略 |
| SM2 签名（响应，服务端） | ~1.5ms | SM3withSM2，仅签 `ts+cipher` 短串 |
| SM2 验签（请求，服务端） | ~2ms | 非对称运算，比对称慢一个量级 |
| SM2 验签（响应，前端 JS） | ~5-10ms | 纯 JS 实现，无 JIT 优化，是前端主要开销 |
| 响应解密+MAC 校验 | ~1ms | 同上 |
| `/auth/public-key` 拉取 | ~50ms | 首次启动一次，sessionStorage 缓存 |

> SM2 签名/验签比 SM4 慢两个量级，但只作用于「时间戳 + 密文」这类短串，
> 且响应验签在前端异步执行，实测无感知卡顿。真正耗时的是网关转发和数据库 I/O。
>
> 若前端验签成为瓶颈（如低端移动设备），可考虑：
> 1) 缩小响应签名范围：`sign-response` 是**全局开关**（`EncryptProperties` 级），
>    目前没有「按方法控制响应签名」的注解能力（`@SmEncrypt(sign=...)` 控制的是**请求验签**，别混用）；
>    需要精细化时得扩展 `SmEncrypt` 增加 `signResponse()` 属性并在 `EncryptPolicy` 里透传；
> 2) 验签失败时才提示，不阻塞渲染（当前实现是阻塞的，保证安全优先）。

---

## 12. 架构总图

```
                    浏览器启动
                        │
               ┌────────▼─────────┐
               │ 1. 本地生成 SM2  │  私钥存 localStorage（永不上传）
               │    客户端密钥对   │
               └────────┬─────────┘
                        │ POST /crypto/sm2/client-key 登记公钥
                        ▼ 返回 clientKeyId
               ┌─────────────────────┐
               │ sessionStorage      │
               │ crypto_keys?        │
               └────┬──────┬─────────┘
                    是     否
                    │      │
     loadCache() ◀──┘      ▼ fetch('/auth/public-key')
               ┌─────────────────────┐
               │  big-auth (明文)     │
               │  @SmPlain 豁免       │
               │  SmCryptoService     │
               │  .toCryptoConfigVO() │
               └─────────┬───────────┘
                         │ 返回 CryptoConfigVO（含 sm2PublicKey）
                         ▼
                 smCrypto.initCryptoConfig()
                         │
                    应用 mount ✅


┌──────────┐  X-Encrypted:true   ┌──────────────┐
│ bigxvue  │ ──────────────────▶ │  big-gateway │
│ (前端)   │    EncryptedRequest  │   (网关)     │
│          │  ◀────────────────── │              │
└──────────┘    EncryptedPayload  └──────┬───────┘
     │                                    │ 转发
     │                            ┌───────▼───────────┐
     │                            │    big-auth       │
     │                            │  @SmEncrypt       │
     │                            │  /auth/login      │
     │                            └───────┬───────────┘
     │                                    │
     │               SmDecryptRequestBodyAdvice
     │               ┌────────────────────┴─────────────────────┐
     │               │ ① MAC 校验（Encrypt-then-MAC）            │
     │               │ ② 时间戳 / nonce 防重放                   │
     │               │ ③ 按 clientKeyId 取公钥 → SM2 验签        │
     │               │ ④ SM4 解密 → Jackson 反序列化             │
     │               └──────────────────────┬───────────────────┘
     │                                      │
     │                            Controller 处理
     │                                      │
     │               SmEncryptResponseBodyAdvice
     │               ┌──────────────────────┴───────────────────┐
     │               │ ① Result.data → 序列化 → SM4 加密        │
     │               │ ② HMAC-SM3 算 MAC                       │
     │               │ ③ sign-response? → 全局私钥 SM2 签名     │
     │               └──────────────────────┬───────────────────┘
     │                                      │
     └──────────────────────────────────────┘
        前端响应拦截器（顺序固定）
        ┌───────────────────────────────────────────────┐
        │ ① verifyResponse：用全局公钥验签              │
        │     ├─ 无 sign        → 放行（后端未开启）     │
        │     ├─ 有 sign 无公钥 → 拒绝（防绕过）         │
        │     └─ 验签失败       → 拒绝（防篡改）         │
        │ ② MAC 校验 → SM4 解密 → 交给业务              │
        └───────────────────────────────────────────────┘
```

**两条独立的信任链**：

| 方向 | 签名主体 | 验证主体 | 密钥体系 |
|---|---|---|---|
| 请求（前端 → 后端） | 客户端本地私钥 | 服务端（按 `clientKeyId` 取公钥） | **每客户端一把**（登记制） |
| 响应（后端 → 前端） | 服务端全局私钥 | 前端（全局公钥） | **全局唯一一对** |

> 两者不可混用：请求签名验证「请求方持有其声称的私钥」（身份相关），
> 响应签名验证「响应确实来自本服务端」（来源相关）。
> 因此请求侧必须每客户端一把，响应侧必须全局唯一。

---

*文档由项目实战踩坑总结而来，每一个参数选择都是调试验证过的结果。*