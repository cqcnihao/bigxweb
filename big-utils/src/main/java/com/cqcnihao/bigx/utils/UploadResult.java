package com.cqcnihao.bigx.utils;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {

    private String fileName;
    private String originalName;
    private String filePath;
    private String fileType;
    private long fileSize;
    private String fileSizeFormat;
    private String url;
    private String previewUrl;
    private String downloadUrl;
    private String md5;
    private String etag;
    private boolean success;
    private String message;
    private long uploadTime;

    public static UploadResult success(String fileName, String originalName,
                                       String fileType, long fileSize, String url) {
        return UploadResult.builder()
                .fileName(fileName)
                .originalName(originalName)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileSizeFormat(formatFileSize(fileSize))
                .url(url)
                .previewUrl(url)
                .downloadUrl(url + "?download=true")
                .success(true)
                .message("上传成功")
                .uploadTime(System.currentTimeMillis())
                .build();
    }

    public static UploadResult error(String originalName, String message) {
        return UploadResult.builder()
                .originalName(originalName)
                .success(false)
                .message(message)
                .uploadTime(System.currentTimeMillis())
                .build();
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + "B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f%s", (double)size / (1L << (z*10)), " KMGTPE".charAt(z));
    }
}