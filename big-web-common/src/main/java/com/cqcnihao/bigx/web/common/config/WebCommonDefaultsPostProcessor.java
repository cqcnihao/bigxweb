package com.cqcnihao.bigx.web.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;

import java.util.Properties;

@Slf4j
public class WebCommonDefaultsPostProcessor implements EnvironmentPostProcessor {

    private static final String DEFAULTS_PROPERTY_SOURCE_NAME = "webCommonDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(DEFAULTS_PROPERTY_SOURCE_NAME)) {
            return;
        }

        Resource resource = new ClassPathResource("web-common-defaults.yml");
        if (!resource.exists()) {
            return;
        }

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);

        Properties properties;
        try {
            properties = yaml.getObject();
        } catch (Exception e) {
            log.warn("[web-common] Failed to load web-common-defaults.yml: {}", e.getMessage());
            return;
        }

        if (properties == null || properties.isEmpty()) {
            return;
        }

        sources.addLast(new PropertiesPropertySource(DEFAULTS_PROPERTY_SOURCE_NAME, properties));
        log.info("[web-common] Injected web-common-defaults.yml with {} entries", properties.size());
    }
}