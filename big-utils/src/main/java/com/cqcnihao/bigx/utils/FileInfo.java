package com.cqcnihao.bigx.utils;


import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    private String fileName;
    private String originalName;
    private String filePath;
    private String fileType;
    private long fileSize;
    private String fileSizeFormat;
    private String etag;
    private ZonedDateTime lastModified;
    private String url;
    private String thumbnailUrl;
    private String md5;

    public static FileInfo fromMinioItem(Item item, String baseUrl) {
        String fileName = item.objectName();
        String fileSize = formatFileSize(item.size());

        return FileInfo.builder()
                .fileName(fileName)
                .filePath(fileName)
                .fileSize(item.size())
                .fileSizeFormat(fileSize)
                .lastModified(item.lastModified())
                .etag(item.etag())
                .url(baseUrl + "/" + fileName)
                .build();
    }

    private static String formatFileSize(long size) {
        if (size <= 0) return "0B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}