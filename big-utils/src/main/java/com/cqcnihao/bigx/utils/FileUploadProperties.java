package com.cqcnihao.bigx.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadProperties {

    private List<String> allowedTypes = new ArrayList<>();

    private long maxSizeMb = 50;

    private long maxSingleFileSize = 20 * 1024 * 1024; // 20MB

}
