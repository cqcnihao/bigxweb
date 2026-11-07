package com.cqcnihao.bigx.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UploadProgressMonitor {

    private final Map<String, UploadProgress> progressMap = new ConcurrentHashMap<>();

    @Data
    public static class UploadProgress {
        private String uploadId;
        private String fileName;
        private String originalName;
        private long totalSize;
        private long uploadedSize;
        private int totalParts;
        private int completedParts;
        private String status; // INITIALIZING, UPLOADING, MERGING, COMPLETED, FAILED, CANCELLED
        private double progress; // 0-100
        private Date startTime;
        private Date lastUpdateTime;
        private String errorMessage;
        private String fileUrl;
        private String md5;

        public double calculateProgress() {
            if (totalSize <= 0) return 0;
            return (uploadedSize * 100.0) / totalSize;
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime.getTime();
        }

        public long getRemainingTime() {
            if (progress <= 0) return -1;
            long elapsed = getElapsedTime();
            return (long) (elapsed * (100 - progress) / progress);
        }

        public String getSpeed() {
            long elapsed = getElapsedTime() / 1000;
            if (elapsed <= 0) return "0 B/s";
            double speed = uploadedSize / (double) elapsed;
            return formatSpeed(speed);
        }

        private String formatSpeed(double bytesPerSecond) {
            if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
            if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024);
            return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
        }
    }

    /**
     * 开始上传任务
     */
    public String startUpload(String fileName, String originalName, long totalSize, int totalParts, String md5) {
        String uploadId = generateUploadId();

        UploadProgress progress = new UploadProgress();
        progress.setUploadId(uploadId);
        progress.setFileName(fileName);
        progress.setOriginalName(originalName);
        progress.setTotalSize(totalSize);
        progress.setTotalParts(totalParts);
        progress.setStatus("INITIALIZING");
        progress.setStartTime(new Date());
        progress.setLastUpdateTime(new Date());
        progress.setMd5(md5);

        progressMap.put(uploadId, progress);
        log.info("开始上传任务: {}, 文件: {}, 大小: {} bytes", uploadId, fileName, totalSize);

        return uploadId;
    }

    /**
     * 更新上传进度
     */
    public void updateProgress(String uploadId, long partSize, int completedParts) {
        UploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.setUploadedSize(progress.getUploadedSize() + partSize);
            progress.setCompletedParts(completedParts);
            progress.setStatus("UPLOADING");
            progress.setProgress(progress.calculateProgress());
            progress.setLastUpdateTime(new Date());

            log.debug("上传进度更新: {}, 进度: {}%, 速度: {}",
                    uploadId, String.format("%.1f", progress.getProgress()), progress.getSpeed());
        }
    }

    /**
     * 标记为合并中
     */
    public void markAsMerging(String uploadId) {
        UploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.setStatus("MERGING");
            progress.setLastUpdateTime(new Date());
        }
    }

    /**
     * 完成上传
     */
    public void completeUpload(String uploadId, String fileUrl) {
        UploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.setStatus("COMPLETED");
            progress.setProgress(100);
            progress.setUploadedSize(progress.getTotalSize());
            progress.setCompletedParts(progress.getTotalParts());
            progress.setFileUrl(fileUrl);
            progress.setLastUpdateTime(new Date());

            log.info("上传完成: {}, 文件: {}, 耗时: {}ms",
                    uploadId, progress.getFileName(), progress.getElapsedTime());

            // 30分钟后清理完成的记录
            new Thread(() -> {
                try {
                    Thread.sleep(30 * 60 * 1000);
                    progressMap.remove(uploadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    /**
     * 标记为失败
     */
    public void markAsFailed(String uploadId, String errorMessage) {
        UploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.setStatus("FAILED");
            progress.setErrorMessage(errorMessage);
            progress.setLastUpdateTime(new Date());

            log.error("上传失败: {}, 原因: {}", uploadId, errorMessage);
        }
    }

    /**
     * 取消上传
     */
    public void cancelUpload(String uploadId) {
        UploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.setStatus("CANCELLED");
            progress.setLastUpdateTime(new Date());

            log.info("上传取消: {}", uploadId);
        }
    }

    /**
     * 获取上传进度
     */
    public UploadProgress getProgress(String uploadId) {
        return progressMap.get(uploadId);
    }

    /**
     * 清理过期任务（24小时以上）
     */
    public void cleanupExpiredTasks() {
        long now = System.currentTimeMillis();
        long expireTime = 24 * 60 * 60 * 1000; // 24小时

        progressMap.entrySet().removeIf(entry -> {
            UploadProgress progress = entry.getValue();
            long lastUpdate = progress.getLastUpdateTime().getTime();
            boolean expired = now - lastUpdate > expireTime;
            if (expired) {
                log.info("清理过期上传任务: {}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 生成唯一的上传ID
     */
    private String generateUploadId() {
        return "upload_" + System.currentTimeMillis() + "_" +
                (int) ((Math.random() * 9 + 1) * 1000);
    }
}
