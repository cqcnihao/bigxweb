package com.cqcnihao.bigx.utils;

import com.cqcnihao.bigx.utils.FileUploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class FileValidator {

    @Autowired
    private FileUploadProperties fileUploadProperties;

    // 允许的图片扩展名
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    ));

    // 允许的文档扩展名
    private static final Set<String> DOCUMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"
    ));

    // 允许的视频扩展名
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "wmv", "flv", "mkv"
    ));

    // 允许的音频扩展名
    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "ogg", "flac", "aac"
    ));

    /**
     * 验证文件类型
     */
    public void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 获取文件扩展名
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();

        // 检查是否在黑名单中
        if (isInBlacklist(extension)) {
            log.warn("上传文件类型在黑名单中: {}", extension);
            throw new IllegalArgumentException("不允许上传此类型的文件: " + extension);
        }

        // 如果配置了允许的类型，优先使用配置
        if (!fileUploadProperties.getAllowedTypes().isEmpty()) {
            String contentType = file.getContentType();
            if (!fileUploadProperties.getAllowedTypes().contains(contentType) &&
                    !fileUploadProperties.getAllowedTypes().contains("." + extension)) {
                log.warn("上传文件类型不在允许列表中: contentType={}, extension={}", contentType, extension);
                throw new IllegalArgumentException("不允许上传此类型的文件: " + extension);
            }
        }
    }

    /**
     * 验证文件大小
     */
    public void validateFileSize(MultipartFile file) {
        if (file == null) {
            return;
        }

        long maxSize = fileUploadProperties.getMaxSingleFileSize();
        if (file.getSize() > maxSize) {
            long maxSizeMB = maxSize / (1024 * 1024);
            log.warn("上传文件大小超出限制: {}MB > {}MB", file.getSize() / (1024 * 1024), maxSizeMB);
            throw new IllegalArgumentException("文件大小超出限制，最大允许 " + maxSizeMB + "MB");
        }
    }

    /**
     * 生成安全的文件名
     */
    public String generateSafeFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename);
        String baseName = FilenameUtils.getBaseName(originalFilename);

        // 清理文件名，移除特殊字符
        String safeBaseName = baseName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");

        // 添加时间戳和随机数防止冲突
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int) ((Math.random() * 9 + 1) * 1000));

        return String.format("%s_%s_%s.%s",
                safeBaseName, timestamp, random, extension.toLowerCase());
    }

    /**
     * 获取文件分类
     */
    public String getFileCategory(String extension) {
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        } else if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return "document";
        } else if (VIDEO_EXTENSIONS.contains(extension)) {
            return "video";
        } else if (AUDIO_EXTENSIONS.contains(extension)) {
            return "audio";
        } else {
            return "other";
        }
    }

    /**
     * 检查是否为图片文件
     */
    public boolean isImageFile(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension(String filename) {
        return FilenameUtils.getExtension(filename).toLowerCase();
    }

    /**
     * 危险文件类型黑名单
     */
    private boolean isInBlacklist(String extension) {
        Set<String> blacklist = new HashSet<>(Arrays.asList(
                "exe", "bat", "cmd", "sh", "php", "asp", "jsp",
                "js", "html", "htm", "vbs", "scr", "pif", "com"
        ));
        return blacklist.contains(extension);
    }
}
