package com.cqcnihao.bigx.web.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 通用配置
 * 所有 Web 模块自动继承，生成标准化 API 文档
 *
 * 访问地址：http://localhost:端口/swagger-ui.html
 *
 * @author bigweb
 */
@Configuration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
public class Knife4jConfig {

    @Value("${spring.application.name:bigweb}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API 文档")
                        .description("Bigweb 项目 RESTful API 接口文档\n\n" +
                                "## 功能说明\n" +
                                "- 所有接口均返回统一的 Result 格式\n" +
                                "- 支持 Token 认证（通过 Authorization 请求头传递）\n" +
                                "- 接口路径以 /api 为前缀\n\n" +
                                "## 响应格式\n" +
                                "```json\n" +
                                "{\n" +
                                "  \"success\": true,\n" +
                                "  \"code\": \"200\",\n" +
                                "  \"message\": \"操作成功\",\n" +
                                "  \"data\": {},\n" +
                                "  \"timestamp\": 1234567890\n" +
                                "}\n" +
                                "```")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bigweb Team")
                                .email("support@cqcnihao.com")
                                .url("https://cqcnihao.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .schemaRequirement("Authorization", new SecurityScheme()
                        .name("Authorization")
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .description("请输入 Token，格式：Bearer {token}"));
    }
}