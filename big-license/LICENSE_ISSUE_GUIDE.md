# Bigx 许可证签发全流程

> 适用版本：Truelicense 1.33 + JDK 21 + big-license 自定义扩展
> 目标：解决 Truelicense 默认 `SHA1withDSA` 与 RSA 密钥不兼容的历史坑，签发可落地的生产许可证

---

## 一、前置条件

| 项目 | 值 |
|---|---|
| 密钥库密码 | `bigx-store-pwd-2024`（写死） |
| 私钥 alias | `privateKey` |
| 公钥 alias | `publicCert` |
| 签名算法 | `SHA256withRSA`（覆盖 Truelicense 默认） |
| 许可证文件 | `license.lic` |
| 有效期 | 签发日起 10 年 |

三个运行时文件必须部署在服务启动目录的 `./license/` 下：

```
license/
├── privateKeys.store    # 私钥库（签发端独占，绝不下发生产）
├── publicCerts.store    # 公钥库（校验端使用）
└── license.lic          # 许可证文件（校验端使用）
```

---

## 二、生成密钥库

使用 JDK keytool 生成 RSA 2048 密钥对（JKS 格式）：

```powershell
$pwd = "bigx-store-pwd-2024"
$ksDir = "D:\bigxweb\license"

# 1. 生成私钥库
keytool -genkeypair `
  -alias privateKey `
  -keyalg RSA -keysize 2048 `
  -validity 3650 `
  -dname "CN=Bigx, OU=Dev, O=CQC, L=Chongqing, ST=Chongqing, C=CN" `
  -keystore "$ksDir\privateKeys.store" `
  -storetype JKS `
  -storepass $pwd -keypass $pwd `
  -sigalg SHA256withRSA

# 2. 导出自签名证书
keytool -exportcert `
  -alias privateKey `
  -keystore "$ksDir\privateKeys.store" `
  -storepass $pwd `
  -file "$ksDir\pub.cer"

# 3. 导入公钥库
keytool -importcert `
  -alias publicCert `
  -file "$ksDir\pub.cer" `
  -keystore "$ksDir\publicCerts.store" `
  -storepass $pwd `
  -storetype JKS -noprompt

# 4. 清理临时证书
Remove-Item "$ksDir\pub.cer"
```

### 注意事项

- **JKS alias 大小写不敏感**：keytool 生成的 alias 可能显示为小写 `privatekey`，Java API 访问时不区分，配置文件写 `privateKey` 即可。
- **sigalg 必须指定**：`SHA256withRSA`，否则 keytool 默认用 SHA256withRSA 签证书（JDK 21 默认），私钥条目自带算法元信息。

---

## 三、Truelicense 1.33 × JDK 21 RSA 兼容坑

### 3.1 现象

直接调用 `LicenseManager.store(content, file)` 签发 RSA 密钥的许可证时报错：

```
java.security.InvalidKeyException: No installed provider supports this key:
  sun.security.rsa.RSAPrivateCrtKeyImpl
    at java.base/java.security.Signature$Delegate.chooseProvider(Signature.java:1303)
    at de.schlichtherle.xml.GenericCertificate.sign(GenericCertificate.java:283)
```

### 3.2 根因定位

通过 `javap -c -p` 反编译 `LicenseNotary.class` 发现：

```
private static final java.lang.String SHA1_WITH_DSA;
protected java.security.Signature getSignatureEngine();
  0: getstatic  #40   // Field SHA1_WITH_DSA:Ljava/lang/String;
```

**Truelicense 1.33 的 `LicenseNotary.getSignatureEngine()` 硬编码返回 `SHA1withDSA`**。

DSA 签名引擎只接受 DSA 类型的 PrivateKey，碰到 RSA key 直接抛 `InvalidKeyException`。JDK 21 的安全提供者策略比旧版更严格，没有 fallback 到 BC 提供者的可能。

### 3.3 修复方案

继承 `CustomLicenseManager` → 重写 `getLicenseNotary()` → 返回内部类 `RsaLicenseNotary` → 覆盖 `getSignatureEngine()` 返回 `SHA256withRSA`：

```java
// CustomLicenseManager.java 核心片段（big-license 模块）

private static final String SIGN_ALGO = "SHA256withRSA";

@Override
protected synchronized LicenseNotary getLicenseNotary() {
    LicenseNotary notary = super.getLicenseNotary();
    return new RsaLicenseNotary(notary.getKeyStoreParam());
}

private static class RsaLicenseNotary extends LicenseNotary {
    RsaLicenseNotary(KeyStoreParam param) { super(param); }

    @Override
    protected Signature getSignatureEngine() {
        try {
            return Signature.getInstance(SIGN_ALGO);
        } catch (Exception e) {
            throw new RuntimeException("无法创建 SHA256withRSA 签名引擎", e);
        }
    }
}
```

### 3.4 为什么不设置 GenericCertificate.signatureAlgorithm

试过在 `initialize(LicenseContent)` 里调 `cert.setSignatureAlgorithm("SHA256withRSA")` ——没用。

`GenericCertificate.sign()` 方法的调用链：

```
LicenseNotary.sign(cert, content)
  → Signature sig = getSignatureEngine()   // ← 算法在这里就定死了
  → cert.sign(content, privateKey, sig)    // ← 传入的 sig 实例已经是 SHA1withDSA
```

`signatureAlgorithm` 字段只在 **签名完成后** 被 `GenericCertificate.sign()` 自身设置，对引擎选择没有反向影响。所以必须在 `getSignatureEngine()` 源头劫持。

---

## 四、签发许可证

### 4.1 签发工具 GenLic.java

放在项目根目录独立执行，避免污染生产代码：

```java
import de.schlichtherle.license.*;
import com.cqcnihao.bigx.license.manager.CustomLicenseManager;
import com.cqcnihao.bigx.license.manager.LicenseParams;
import java.io.*;
import java.util.*;
import javax.security.auth.x500.X500Principal;

public class GenLic {
    public static void main(String[] a) throws Exception {
        File out = new File("D:/bigxweb/license/license.lic");
        File privateStore = new File("D:/bigxweb/license/privateKeys.store");
        String storePwd = "bigx-store-pwd-2024";
        String keyPwd   = "bigx-store-pwd-2024";

        // CustomLicenseManager 内置 RsaLicenseNotary，自动覆盖签名算法
        LicenseParam param = LicenseParams.issueParam(
            "bigx", privateStore, storePwd, keyPwd);
        CustomLicenseManager manager = new CustomLicenseManager(param);

        Calendar cal = Calendar.getInstance();
        Date issued = cal.getTime();
        cal.add(Calendar.YEAR, 10);
        Date expire = cal.getTime();

        LicenseContent content = new LicenseContent();
        content.setSubject("bigx");
        content.setIssuer(new X500Principal("CN=cqcnihao, OU=bigx, O=cqcnihao, C=CN"));
        content.setIssued(issued);
        content.setNotBefore(issued);
        content.setNotAfter(expire);
        content.setConsumerType("USER");
        content.setInfo("bigx dev license");
        // MAC 绑定时: content.setExtra(new LicenseExtra(Arrays.asList("AA:BB:CC:DD:EE:FF")));

        manager.store(content, out);
        System.out.println("OK: " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }
}
```

### 4.2 编译 & 运行

```powershell
# 1. 先安装最新 big-license（保证 CustomLicenseManager 生效）
cd D:\bigxweb
mvn -pl big-license -am install -DskipTests -q

# 2. 编译 GenLic
$cp = @"
D:\bigxweb\big-license\target\classes;
F:\maven\repository\de\schlichtherle\truelicense\truelicense-core\1.33\truelicense-core-1.33.jar;
F:\maven\repository\de\schlichtherle\truelicense\truelicense-xml\1.33\truelicense-xml-1.33.jar;
F:\maven\repository\org\slf4j\slf4j-api\2.0.17\slf4j-api-2.0.17.jar;
F:\maven\repository\tools\jackson\core\jackson-databind\3.1.7\jackson-databind-3.1.7.jar;
F:\maven\repository\tools\jackson\core\jackson-core\3.1.7\jackson-core-3.1.7.jar;
F:\maven\repository\tools\jackson\core\jackson-annotations\3.1.7\jackson-annotations-3.1.7.jar;
F:\maven\repository\commons-codec\commons-codec\1.17.1\commons-codec-1.17.1.jar
"@

javac -cp $cp -d D:\bigxweb D:\bigxweb\GenLic.java

# 3. 运行签发
java -cp "D:\bigxweb;$cp" GenLic
# 输出: OK: D:\bigxweb\license\license.lic (848 bytes)
```

### 4.3 commons-codec 依赖

Truelicense 1.33 的 `GenericCertificate.sign()` 内部调用了 `org.apache.commons.codec.binary.Base64.encodeBase64()`，单独跑 GenLic 时类路径必须带上 `commons-codec`，否则抛 `NoClassDefFoundError`。

big-license 的 POM 已声明该依赖，业务模块天然带上；独立签发工具需手动补入类路径。

---

## 五、运行时配置

### 5.1 application.yml（已写死生产模式）

```yaml
license:
  enabled: ${BIG_LICENSE_ENABLED:true}            # 默认 true，启用校验
  subject: ${BIG_LICENSE_SUBJECT:bigx}
  license-path: ${BIG_LICENSE_PATH:./license/license.lic}
  public-certs-path: ${BIG_LICENSE_PUB_STORE:./license/publicCerts.store}
  private-store-path: ${BIG_LICENSE_PRIVATE_STORE:./license/privateKeys.store}
  store-pwd: ${BIG_LICENSE_STORE_PWD:bigx-store-pwd-2024}   # 写死
  key-pwd: ${BIG_LICENSE_KEY_PWD:bigx-store-pwd-2024}       # 写死
  private-alias: ${BIG_LICENSE_PRIVATE_ALIAS:privateKey}
  verify-interval-minutes: ${BIG_LICENSE_VERIFY_INTERVAL:30}
  on-fail: ${BIG_LICENSE_ON_FAIL:exit}                       # 校验失败直接退出
  clock-rollback-check: ${BIG_LICENSE_CLOCK_CHECK:true}
```

### 5.2 启动文件布局

```
big-business/                           # 工作目录
├── license/                            # 相对路径 ./license/
│   ├── privateKeys.store               # LicenseSignService 延迟加载
│   ├── publicCerts.store               # 许可证校验用
│   └── license.lic                     # 许可证文件
└── target/
    └── big-business.jar
```

### 5.3 启动验证

正常启动日志应看到：

```
[INFO] 许可证 MAC 绑定校验通过（绑定 0 个 MAC）
```

校验失败时：

```
[WARN] 许可证 MAC 绑定校验失败 | 许可: [...] | 本机: [...]
java.lang.IllegalStateException: 许可证校验失败，服务拒绝启动: ...
```

---

## 六、安全红线

| 规则 | 说明 |
|---|---|
| **privateKeys.store 绝对不下发生产** | 只在签发端（开发机 / CI 流水线）留存 |
| **密码写死 ≠ 可以提交 Git 到公开仓库** | 已提交 → 立即轮换密钥库 + 改密码 + 重新签发 |
| **on-fail: exit 不能改 warn** | 生产环境许可证校验必须阻断启动，降级只允许 dev profile |
| **签名私钥只用于签 License** | 禁止复用业务加密的密钥对 |

---

## 七、故障速查

| 症状 | 排查 |
|---|---|
| `No installed provider supports this key` | 签名算法与密钥类型不匹配 → 确认 `CustomLicenseManager` 生效（不是父类 `LicenseManager`） |
| `NoClassDefFoundError: commons-codec` | 签发工具类路径缺 `commons-codec` |
| `许可证文件不存在` | `license-path` 是相对路径，工作目录下必须有 `./license/license.lic` |
| `签名校验失败` | 1) license.lic 被篡改；2) publicCerts.store 不是对应私钥导出的；3) Truelicense 版本不一致 |
| `密钥库密码错误` | store-pwd / key-pwd 与生成时不一致，或 JKS alias 不对 |
| `MAC 绑定校验失败` | LicenseExtra.macs 里的 MAC 地址与 `HardwareUtil.getLocalMacs()` 归一化后不匹配；通配方案：不绑 MAC 时 macs 置空 |
| `sign 字段始终为空（@Sign 切面已执行但 DB 无值）` | **切面时序问题**：`@Sign` 是环绕通知，签名发生在 `pjp.proceed()` 返回之后；但 Service 内部 `this.save()` 已先落库。切面只改内存对象，不触发二次 update → DB 永远空。**修复方案**：`SignatureAspect.doSign()` 签名后调用 `refreshPersistent(entity)` → 通过缓存的 `BaseMapper.updateById()` 回写 sign 字段 |
| `isVerifyEnabled = false（公钥没加载到）` | **公钥加载用错 API**：`LicenseSignService.loadPublicKey()` 之前用 `ks.getKey(alias, pwd)` 取公钥，但 `publicCerts.store` 里存的是 `trustedCertEntry`（只有证书），`getKey()` 对 PrivateKeyEntry 以外的条目永远返回 null。**修复**：改用 `ks.getCertificate(alias).getPublicKey()` |
| `[SignatureAspect] mapper cache initialized: 0 entries` | **JDK 动态代理泛型丢失**：MyBatis-Plus 的 Mapper 是 JDK 代理，`mapper.getClass().getGenericInterfaces()` 拿不到业务接口的泛型参数（只看到 `BaseMapper` 原始类型）。**修复**：递归向上遍历 `iface.getGenericInterfaces()`，从业务接口 → BaseMapper 的泛型链中提取实体类型 `findEntityInHierarchy()` |
| `JKS alias 被 keytool 强制小写` | `keytool -genkeypair -alias privateKey` 生成的 JKS 里 alias 实际是 `privatekey`（小写）。实测 JKS `KeyStore` 查询时**不区分大小写**（`privateKey` / `privatekey` 都能命中），但常量定义仍应保持与 keytool 显示一致，避免误解 |