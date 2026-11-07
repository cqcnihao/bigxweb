# 📦 big-web-common 模块

> **模块名称**：big-web-common  
> **模块版本**：1.3.5-SNAPSHOT  
> **创建时间**：2026-04-27  
> **模块说明**：Web 通用配置和组件模块，接管所有 Web 应用的基础配置

---

## 🎯 模块定位

`big-web-common` 是一个 **Web 通用配置模块**，用于集中管理所有 Web 应用的通用配置和组件，避免在各业务模块中重复配置。

### 模块层级关系

```
big-core (核心异常和常量)
    ↓
big-common (通用响应、异常处理、Result)
    ↓
big-utils (工具类、拦截器、配置)
    ↓
big-web-common (Web 通用配置) ← 新模块
    ↓
业务模块 (web, webTiny, webMyuc, etc.)
```

---

## 📋 功能清单

### ✅ 已实现功能

| 功能 | 说明 | 位置 |
|------|------|------|
| **CORS 跨域配置** | 统一处理跨域请求 | `config/CorsConfig.java` |
| **Web MVC 配置** | 注册全局拦截器 | `config/WebMvcConfig.java` |
| **TraceId 拦截器** | 链路追踪 | 继承自 `big-utils` |
| **请求日志拦截器** | 记录请求详情 | `interceptor/RequestLogInterceptor.java` |
| **基础 Controller** | 健康检查、服务信息 | `controller/BaseController.java` |

### 🔄 待迁移功能

以下功能可以从各业务模块迁移到 `big-web-common`：

| 功能 | 当前所在模块 | 说明 |
|------|------------|------|
| 文件上传配置 | webPiyw | 文件上传限制和路径配置 |
| 静态资源配置 | web, webTiny | 静态资源映射 |
| Thymeleaf 配置 | 各模块 | 模板引擎配置 |
| Sa-Token 配置 | big-auth | 认证授权配置 |

---

## 🚀 快速开始

### 1. 在业务模块中引入依赖

在业务模块的 `pom.xml` 中添加：

```xml
<dependencies>
    <!-- 引入 big-web-common -->
    <dependency>
        <groupId>com.cqcnihao</groupId>
        <artifactId>big-web-common</artifactId>
        <version>1.3.5-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. 配置组件扫描

在启动类中添加组件扫描：

```java
@SpringBootApplication(scanBasePackages = {
    "com.cqcnihao.web.common",  // 扫描 big-web-common
    "com.cqcnihao.yourmodule"   // 扫描业务模块
})
public class YourWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourWebApplication.class, args);
    }
}
```

### 3. 自动生效的功能

引入后，以下功能**自动生效**，无需额外配置：

✅ CORS 跨域支持  
✅ TraceId 链路追踪  
✅ 请求日志记录  
✅ 健康检查接口 `/api/base/health`  
✅ 服务信息接口 `/api/base/info`  

---

## 📖 使用示例

### 1. 健康检查

**请求**：
```bash
GET http://localhost:8080/api/base/health
```

**响应**：
```json
{
  "success": true,
  "code": "200",
  "message": "操作成功",
  "data": {
    "status": "UP",
    "timestamp": "2026-04-27T10:30:00",
    "service": "bigweb"
  },
  "timestamp": 1234567890
}
```

### 2. 服务信息

**请求**：
```bash
GET http://localhost:8080/api/base/info
```

**响应**：
```json
{
  "success": true,
  "code": "200",
  "message": "操作成功",
  "data": {
    "name": "Bigweb Service",
    "version": "1.3.5-SNAPSHOT",
    "javaVersion": "21.0.9",
    "timestamp": "2026-04-27T10:30:00"
  },
  "timestamp": 1234567890
}
```

### 3. 自定义 Controller 继承 BaseController

```java
@RestController
@RequestMapping("/api/user")
public class UserController extends BaseController {
    
    // 自动继承 health() 和 info() 方法
    
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        // 业务逻辑
    }
}
```

---

## 🔧 配置说明

### 1. CORS 跨域配置

**默认配置**（开发环境）：
```java
config.addAllowedOriginPattern("*");  // 允许所有域名
config.setAllowCredentials(true);      // 允许携带认证信息
```

**生产环境建议**：
```java
// 在业务模块中覆盖 CorsConfig
@Configuration
public class CustomCorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("https://yourdomain.com");  // 指定域名
        // ...
    }
}
```

### 2. 请求日志拦截器

**日志格式**：
```
>>> 请求开始: GET /api/users | IP: 192.168.1.100 | User-Agent: Mozilla/5.0...
<<< 请求完成: GET /api/users | 状态: 200 | 耗时: 45ms
```

**排除路径**：
- `/actuator/**` - 监控端点
- `/druid/**` - Druid 监控
- `/swagger-resources/**` - API 文档
- `/static/**` - 静态资源
- `/favicon.ico` - 网站图标

### 3. TraceId 链路追踪

**请求头**：
```
X-Trace-Id: abc123def456...
```

**日志输出**：
```
2026-04-27 10:30:00 INFO  c.c.web.controller.UserController:25 abc123de 查询用户信息
```

---

## 📊 模块依赖

### 依赖的其他模块

```xml
<!-- 基础模块 -->
<dependency>
    <groupId>com.cqcnihao</groupId>
    <artifactId>big-common</artifactId>
    <version>1.3.5-SNAPSHOT</version>
</dependency>

<!-- 工具模块 -->
<dependency>
    <groupId>com.cqcnihao</groupId>
    <artifactId>big-utils</artifactId>
    <version>1.3.5-SNAPSHOT</version>
</dependency>
```

### 提供的依赖（provided scope）

这些依赖由业务模块提供：

| 依赖 | 用途 |
|------|------|
| spring-boot-starter-web | Spring MVC |
| spring-boot-starter-validation | 参数校验 |
| knife4j-openapi3-jakarta-spring-boot-starter | API 文档 |
| spring-boot-starter-cache | 缓存支持 |
| spring-boot-starter-data-redis | Redis 支持 |
| spring-boot-starter-actuator | 监控端点 |

---

## 🎨 扩展开发

### 1. 添加自定义拦截器

```java
@Configuration
public class CustomWebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加自定义拦截器
        registry.addInterceptor(new MyCustomInterceptor())
                .addPathPatterns("/api/**");
    }
}
```

### 2. 添加自定义过滤器

```java
@Component
public class MyCustomFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        // 过滤器逻辑
        chain.doFilter(request, response);
    }
}
```

### 3. 覆盖默认配置

```java
@Configuration
@Primary  // 优先使用此配置
public class CustomCorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        // 自定义 CORS 配置
    }
}
```

---

## 📝 最佳实践

### 1. 业务模块依赖顺序

```xml
<dependencies>
    <!-- 1. 基础模块 -->
    <dependency>
        <groupId>com.cqcnihao</groupId>
        <artifactId>big-common</artifactId>
    </dependency>
    
    <!-- 2. 工具模块 -->
    <dependency>
        <groupId>com.cqcnihao</groupId>
        <artifactId>big-utils</artifactId>
    </dependency>
    
    <!-- 3. Web 通用配置（自动包含 big-common 和 big-utils） -->
    <dependency>
        <groupId>com.cqcnihao</groupId>
        <artifactId>big-web-common</artifactId>
    </dependency>
</dependencies>
```

### 2. 组件扫描配置

```java
// ✅ 推荐：明确指定扫描包
@SpringBootApplication(scanBasePackages = {
    "com.cqcnihao.web.common",
    "com.cqcnihao.auth"
})

// ❌ 不推荐：扫描所有包（可能导致冲突）
@SpringBootApplication
```

### 3. 配置文件管理

**业务模块配置文件**：
```yaml
# application.yml
spring:
  config:
    import:
      - classpath:mybatis-plus-defaults.yml
      - classpath:druid-defaults.yml
      - classpath:redis-defaults.yml
      - classpath:knife4j-defaults.yml
```

**不需要重复配置**：
- CORS 配置（big-web-common 已提供）
- 拦截器配置（big-web-common 已提供）
- TraceId 拦截器（big-utils 已提供）

---

## 🔍 故障排查

### Q1: 跨域不生效？

**检查清单**：
1. ✅ 确认引入了 `big-web-common` 依赖
2. ✅ 确认组件扫描包含了 `com.cqcnihao.web.common`
3. ✅ 确认没有其他 CORS 配置冲突

**解决方案**：
```java
// 查看是否有多个 CorsFilter Bean
@Bean
public CorsFilter corsFilter() {
    // 自定义配置
}
```

### Q2: 拦截器未执行？

**检查清单**：
1. ✅ 确认 `WebMvcConfig` 被扫描到
2. ✅ 确认请求路径不在排除列表中
3. ✅ 确认拦截器注册正确

**调试方法**：
```yaml
logging:
  level:
    com.cqcnihao.web.common.interceptor: DEBUG
```

### Q3: BaseController 接口404？

**检查清单**：
1. ✅ 确认启动类扫描了 `com.cqcnihao.web.common.controller`
2. ✅ 确认没有路径冲突
3. ✅ 确认请求路径正确：`/api/base/health`

---

## 📈 后续规划

### 短期（本周）

- [ ] 迁移文件上传配置
- [ ] 迁移静态资源配置
- [ ] 添加统一的错误页面配置

### 中期（本月）

- [ ] 集成 Sa-Token 认证配置
- [ ] 添加统一的权限拦截器
- [ ] 添加接口限流配置

### 长期（季度）

- [ ] 集成网关配置
- [ ] 添加多租户支持
- [ ] 添加国际化支持

---

## 📞 技术支持

**维护人员**：Bigweb Team  
**联系方式**：support@cqcnihao.com  
**文档版本**：v1.0.0  
**最后更新**：2026-04-27

---

## ✨ 总结

`big-web-common` 模块的创建实现了：

✅ **配置统一** - 所有 Web 通用配置集中管理  
✅ **代码复用** - 避免各模块重复配置  
✅ **易于维护** - 修改一处，全局生效  
✅ **规范开发** - 提供标准化的 Web 基础配置  

**让业务模块更专注于业务逻辑！** 🚀
