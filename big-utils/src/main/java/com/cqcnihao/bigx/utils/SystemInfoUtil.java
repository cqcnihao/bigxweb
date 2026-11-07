package com.cqcnihao.bigx.utils;



import lombok.extern.log4j.Log4j2;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 系统硬件信息采集工具类
 * 封装底层调用，提供静态方法供上层使用
 * 支持缓存、监控、历史数据等功能
 *
 * @version 2.0
 */
@Log4j2
public class SystemInfoUtil {
    // Log4j日志记录器
    //private static final Logger log = Logger.getLogger(SystemInfoUtil.class);
    //private static final Logger logger = LoggerFactory.getLogger(SystemInfoUtil.class);

    // 单例模式，避免频繁创建SystemInfo对象
    private static volatile SystemInfo SYSTEM_INFO = null;

    // 缓存配置
    private static final long DEFAULT_CACHE_DURATION = 3000; // 默认3秒缓存
    private static final Map<String, CacheEntry> CACHE_MAP = new ConcurrentHashMap<>();
    private static final ReentrantLock CACHE_LOCK = new ReentrantLock();

    // 监控配置
    private static final int DEFAULT_HISTORY_SIZE = 60; // 保留最近60个数据点
    private static final AtomicBoolean MONITOR_RUNNING = new AtomicBoolean(false);
    private static ScheduledExecutorService MONITOR_EXECUTOR = null;

    // 历史数据存储
    private static final List<Double> CPU_USAGE_HISTORY = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> MEMORY_USAGE_HISTORY = Collections.synchronizedList(new ArrayList<>());
    private static final List<SystemLoad> SYSTEM_LOAD_HISTORY = Collections.synchronizedList(new ArrayList<>());

    // 私有构造，禁止外部实例化
    private SystemInfoUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 获取SystemInfo单例（双重检查锁）
     */
    private static SystemInfo getSystemInfo() {
        if (SYSTEM_INFO == null) {
            synchronized (SystemInfoUtil.class) {
                if (SYSTEM_INFO == null) {
                    try {
                        SYSTEM_INFO = new SystemInfo();
                        log.info("SystemInfo工具类初始化成功");
                    } catch (Exception e) {
                        log.error("SystemInfo初始化失败", e);
                        throw new RuntimeException("系统信息工具初始化失败", e);
                    }
                }
            }
        }
        return SYSTEM_INFO;
    }

    // 缓存条目类
    private static class CacheEntry {
        private final Object data;
        private final long timestamp;
        private final long ttl;

        CacheEntry(Object data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    // 系统负载记录
    public static class SystemLoad {
        private final Instant timestamp;
        private final double cpuUsage;
        private final double memoryUsage;
        private final int threadCount;

        public SystemLoad(double cpuUsage, double memoryUsage, int threadCount) {
            this.timestamp = Instant.now();
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.threadCount = threadCount;
        }

        public Instant getTimestamp() { return timestamp; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getThreadCount() { return threadCount; }

        @Override
        public String toString() {
            return String.format("[%s] CPU:%.1f%% MEM:%.1f%% Threads:%d",
                    DateTimeFormatter.ISO_INSTANT.format(timestamp),
                    cpuUsage, memoryUsage, threadCount);
        }
    }

    /**
     * 获取操作系统信息
     */
    public static OperatingSystem getOsInfo() {
        return getOsInfo(false);
    }

    /**
     * 获取操作系统信息（可选缓存）
     */
    public static OperatingSystem getOsInfo(boolean useCache) {
        if (!useCache) {
            return getSystemInfo().getOperatingSystem();
        }

        String key = "osInfo";
        CacheEntry entry = CACHE_MAP.get(key);

        if (entry == null || entry.isExpired()) {
            CACHE_LOCK.lock();
            try {
                // 双重检查
                entry = CACHE_MAP.get(key);
                if (entry == null || entry.isExpired()) {
                    OperatingSystem os = getSystemInfo().getOperatingSystem();
                    CACHE_MAP.put(key, new CacheEntry(os, DEFAULT_CACHE_DURATION));
                    return os;
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        }

        return (OperatingSystem) entry.data;
    }

    /**
     * 获取CPU信息
     */
    public static CentralProcessor getCpuInfo() {
        return getCpuInfo(false);
    }

    /**
     * 获取CPU信息（可选缓存）
     */
    public static CentralProcessor getCpuInfo(boolean useCache) {
        if (!useCache) {
            return getSystemInfo().getHardware().getProcessor();
        }

        String key = "cpuInfo";
        CacheEntry entry = CACHE_MAP.get(key);

        if (entry == null || entry.isExpired()) {
            CACHE_LOCK.lock();
            try {
                entry = CACHE_MAP.get(key);
                if (entry == null || entry.isExpired()) {
                    CentralProcessor cpu = getSystemInfo().getHardware().getProcessor();
                    CACHE_MAP.put(key, new CacheEntry(cpu, DEFAULT_CACHE_DURATION));
                    return cpu;
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        }

        return (CentralProcessor) entry.data;
    }

    /**
     * 获取内存信息
     */
    public static GlobalMemory getMemoryInfo() {
        return getMemoryInfo(false);
    }

    /**
     * 获取内存信息（可选缓存）
     */
    public static GlobalMemory getMemoryInfo(boolean useCache) {
        if (!useCache) {
            return getSystemInfo().getHardware().getMemory();
        }

        String key = "memoryInfo";
        CacheEntry entry = CACHE_MAP.get(key);

        if (entry == null || entry.isExpired()) {
            CACHE_LOCK.lock();
            try {
                entry = CACHE_MAP.get(key);
                if (entry == null || entry.isExpired()) {
                    GlobalMemory memory = getSystemInfo().getHardware().getMemory();
                    CACHE_MAP.put(key, new CacheEntry(memory, DEFAULT_CACHE_DURATION));
                    return memory;
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        }

        return (GlobalMemory) entry.data;
    }

    /**
     * 获取磁盘信息（所有挂载点）
     */
    public static List<OSFileStore> getDiskInfo() {
        return getDiskInfo(false);
    }

    /**
     * 获取磁盘信息（所有挂载点，可选缓存）
     */
    public static List<OSFileStore> getDiskInfo(boolean useCache) {
        if (!useCache) {
            return getSystemInfo().getOperatingSystem().getFileSystem().getFileStores();
        }

        String key = "diskInfo";
        CacheEntry entry = CACHE_MAP.get(key);

        if (entry == null || entry.isExpired()) {
            CACHE_LOCK.lock();
            try {
                entry = CACHE_MAP.get(key);
                if (entry == null || entry.isExpired()) {
                    List<OSFileStore> disks = getSystemInfo().getOperatingSystem().getFileSystem().getFileStores();
                    CACHE_MAP.put(key, new CacheEntry(new ArrayList<>(disks), DEFAULT_CACHE_DURATION));
                    return disks;
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        }

        return new ArrayList<>((List<OSFileStore>) entry.data);
    }

    /**
     * 获取网络接口信息
     */
    public static List<NetworkIF> getNetworkInfo() {
        return getNetworkInfo(false);
    }

    /**
     * 获取网络接口信息（可选缓存）
     */
    public static List<NetworkIF> getNetworkInfo(boolean useCache) {
        if (!useCache) {
            return getSystemInfo().getHardware().getNetworkIFs();
        }

        String key = "networkInfo";
        CacheEntry entry = CACHE_MAP.get(key);

        if (entry == null || entry.isExpired()) {
            CACHE_LOCK.lock();
            try {
                entry = CACHE_MAP.get(key);
                if (entry == null || entry.isExpired()) {
                    List<NetworkIF> networks = getSystemInfo().getHardware().getNetworkIFs();
                    CACHE_MAP.put(key, new CacheEntry(new ArrayList<>(networks), DEFAULT_CACHE_DURATION));
                    return networks;
                }
            } finally {
                CACHE_LOCK.unlock();
            }
        }

        return new ArrayList<>((List<NetworkIF>) entry.data);
    }

    /**
     * 获取CPU使用率（百分比）
     */
    public static double getCpuUsage() {
        return getCpuUsage(1000);
    }

    /**
     * 获取CPU使用率（指定采样间隔）
     */
    public static double getCpuUsage(long intervalMillis) {
        CentralProcessor processor = getCpuInfo();
        long[] prevTicks = processor.getSystemCpuLoadTicks();

        if (intervalMillis > 0) {
            Util.sleep(intervalMillis);
        }

        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;

        // 限制在0-100之间
        return Math.max(0, Math.min(100, cpuLoad));
    }

    /**
     * 获取CPU详细使用率
     */
    public static Map<String, Double> getCpuDetailedUsage() {
        CentralProcessor processor = getCpuInfo();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Util.sleep(1000);
        long[] ticks = processor.getSystemCpuLoadTicks();

        Map<String, Double> usage = new LinkedHashMap<>();
        long total = Arrays.stream(ticks).sum() - Arrays.stream(prevTicks).sum();

        if (total > 0) {
            usage.put("user", 100d * (ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()]) / total);
            usage.put("nice", 100d * (ticks[CentralProcessor.TickType.NICE.getIndex()] - prevTicks[CentralProcessor.TickType.NICE.getIndex()]) / total);
            usage.put("system", 100d * (ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()]) / total);
            usage.put("idle", 100d * (ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()]) / total);
            usage.put("iowait", 100d * (ticks[CentralProcessor.TickType.IOWAIT.getIndex()] - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()]) / total);
            usage.put("irq", 100d * (ticks[CentralProcessor.TickType.IRQ.getIndex()] - prevTicks[CentralProcessor.TickType.IRQ.getIndex()]) / total);
            usage.put("softirq", 100d * (ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()]) / total);
            usage.put("steal", 100d * (ticks[CentralProcessor.TickType.STEAL.getIndex()] - prevTicks[CentralProcessor.TickType.STEAL.getIndex()]) / total);
        }

        return usage;
    }

    /**
     * 获取内存使用率（百分比）
     */
    public static double getMemoryUsage() {
        GlobalMemory memory = getMemoryInfo();
        long total = memory.getTotal();
        if (total == 0) return 0;

        long available = memory.getAvailable();
        return (double) (total - available) * 100 / total;
    }

    /**
     * 获取内存详细信息
     */
    public static Map<String, Object> getMemoryDetailedInfo() {
        GlobalMemory memory = getMemoryInfo();
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("total", memory.getTotal());
        info.put("totalFormatted", formatBytes(memory.getTotal()));
        info.put("available", memory.getAvailable());
        info.put("availableFormatted", formatBytes(memory.getAvailable()));
        info.put("used", memory.getTotal() - memory.getAvailable());
        info.put("usedFormatted", formatBytes(memory.getTotal() - memory.getAvailable()));
        info.put("swapTotal", memory.getVirtualMemory().getSwapTotal());
        info.put("swapTotalFormatted", formatBytes(memory.getVirtualMemory().getSwapTotal()));
        info.put("swapUsed", memory.getVirtualMemory().getSwapUsed());
        info.put("swapUsedFormatted", formatBytes(memory.getVirtualMemory().getSwapUsed()));
        info.put("usage", getMemoryUsage());

        return info;
    }

    /**
     * 获取系统运行时间
     */
    public static String getSystemUptime() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return formatDuration(uptime);
    }

    /**
     * 获取系统启动时间
     */
    public static LocalDateTime getSystemBootTime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long startTime = rb.getStartTime();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), TimeZone.getDefault().toZoneId());
    }

    /**
     * 获取磁盘使用情况
     */
    public static List<Map<String, Object>> getDiskUsage() {
        List<OSFileStore> fileStores = getDiskInfo();
        List<Map<String, Object>> diskList = new ArrayList<>();

        for (OSFileStore fs : fileStores) {
            Map<String, Object> diskInfo = new LinkedHashMap<>();
            diskInfo.put("name", fs.getName());
            diskInfo.put("mount", fs.getMount());
            diskInfo.put("type", fs.getType());
            diskInfo.put("total", fs.getTotalSpace());
            diskInfo.put("totalFormatted", formatBytes(fs.getTotalSpace()));
            diskInfo.put("free", fs.getFreeSpace());
            diskInfo.put("freeFormatted", formatBytes(fs.getFreeSpace()));
            diskInfo.put("usable", fs.getUsableSpace());
            diskInfo.put("usableFormatted", formatBytes(fs.getUsableSpace()));
            diskInfo.put("used", fs.getTotalSpace() - fs.getFreeSpace());
            diskInfo.put("usedFormatted", formatBytes(fs.getTotalSpace() - fs.getFreeSpace()));

            if (fs.getTotalSpace() > 0) {
                double usage = 100.0 * (fs.getTotalSpace() - fs.getFreeSpace()) / fs.getTotalSpace();
                diskInfo.put("usage", String.format("%.1f%%", usage));
            } else {
                diskInfo.put("usage", "0%");
            }

            diskList.add(diskInfo);
        }

        return diskList;
    }

    /**
     * 获取网络流量信息
     */
    public static List<Map<String, Object>> getNetworkTraffic() {
        List<NetworkIF> networks = getNetworkInfo();
        List<Map<String, Object>> networkList = new ArrayList<>();

        for (NetworkIF net : networks) {
            if (!net.isConnectorPresent()) continue;

            Map<String, Object> netInfo = new LinkedHashMap<>();
            netInfo.put("name", net.getName());
            netInfo.put("displayName", net.getDisplayName());
            netInfo.put("mac", net.getMacaddr());
            netInfo.put("ipv4", String.join(", ", net.getIPv4addr()));
            netInfo.put("ipv6", String.join(", ", net.getIPv6addr()));
            netInfo.put("mtu", net.getMTU());
            netInfo.put("speed", net.getSpeed() > 0 ? formatBytes(net.getSpeed()) + "/s" : "Unknown");
            netInfo.put("bytesRecv", net.getBytesRecv());
            netInfo.put("bytesRecvFormatted", formatBytes(net.getBytesRecv()));
            netInfo.put("bytesSent", net.getBytesSent());
            netInfo.put("bytesSentFormatted", formatBytes(net.getBytesSent()));
            netInfo.put("packetsRecv", net.getPacketsRecv());
            netInfo.put("packetsSent", net.getPacketsSent());
            netInfo.put("inErrors", net.getInErrors());
            netInfo.put("outErrors", net.getOutErrors());

            networkList.add(netInfo);
        }

        return networkList;
    }

    /**
     * 获取系统信息摘要
     */
    public static Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        try {
            // 操作系统信息
            OperatingSystem os = getOsInfo();
            summary.put("os", Map.of(
                    "family", os.getFamily(),
                    "manufacturer", os.getManufacturer(),
                    "version", os.getVersionInfo().toString(),
                    "bitness", os.getBitness() + "位"
            ));

            // CPU信息
            CentralProcessor cpu = getCpuInfo();
            summary.put("cpu", Map.of(
                    "name", cpu.getProcessorIdentifier().getName(),
                    "vendor", cpu.getProcessorIdentifier().getVendor(),
                    "model", cpu.getProcessorIdentifier().getModel(),
                    "frequency", String.format("%.2f GHz", cpu.getProcessorIdentifier().getVendorFreq() / 1e9),
                    "physicalCores", cpu.getPhysicalProcessorCount(),
                    "logicalCores", cpu.getLogicalProcessorCount(),
                    "usage", String.format("%.1f%%", getCpuUsage(500))
            ));

            // 内存信息
            Map<String, Object> memoryInfo = getMemoryDetailedInfo();
            summary.put("memory", memoryInfo);

            // 磁盘信息
            summary.put("disks", getDiskUsage());

            // 网络信息
            summary.put("networks", getNetworkTraffic());

            // 系统信息
            summary.put("system", Map.of(
                    "uptime", getSystemUptime(),
                    "bootTime", getSystemBootTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ));

            // JVM信息
            Runtime runtime = Runtime.getRuntime();
            summary.put("jvm", Map.of(
                    "version", System.getProperty("java.version"),
                    "vendor", System.getProperty("java.vendor"),
                    "totalMemory", formatBytes(runtime.totalMemory()),
                    "freeMemory", formatBytes(runtime.freeMemory()),
                    "maxMemory", formatBytes(runtime.maxMemory()),
                    "availableProcessors", runtime.availableProcessors()
            ));

        } catch (Exception e) {
            log.error("获取系统摘要信息失败", e);
            summary.put("error", "获取系统信息失败: " + e.getMessage());
        }

        return summary;
    }

    /**
     * 格式化字节数（B→KB→MB→GB）
     */
    public static String formatBytes(long bytes) {
        return FormatUtil.formatBytes(bytes);
    }

    /**
     * 格式化时间间隔
     */
    public static String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%d天%d小时%d分%d秒", days, hours, minutes, secs);
        } else if (hours > 0) {
            return String.format("%d小时%d分%d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d分%d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }

    /**
     * 清空缓存
     */
    public static void clearCache() {
        CACHE_MAP.clear();
        log.debug("系统信息缓存已清空");
    }

    /**
     * 开始性能监控
     */
    public static synchronized void startMonitor() {
        startMonitor(1, TimeUnit.SECONDS, DEFAULT_HISTORY_SIZE);
    }

    /**
     * 开始性能监控
     */
    public static synchronized void startMonitor(long period, TimeUnit unit, int historySize) {
        if (MONITOR_RUNNING.get()) {
            log.warn("性能监控已在运行中");
            return;
        }

        MONITOR_RUNNING.set(true);
        MONITOR_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SystemMonitor-Thread");
            thread.setDaemon(true);
            return thread;
        });

        MONITOR_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                double cpuUsage = getCpuUsage(0);
                double memoryUsage = getMemoryUsage();
                int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

                CPU_USAGE_HISTORY.add(cpuUsage);
                MEMORY_USAGE_HISTORY.add(memoryUsage);
                SYSTEM_LOAD_HISTORY.add(new SystemLoad(cpuUsage, memoryUsage, threadCount));

                // 保持历史数据大小
                if (CPU_USAGE_HISTORY.size() > historySize) {
                    CPU_USAGE_HISTORY.remove(0);
                    MEMORY_USAGE_HISTORY.remove(0);
                    SYSTEM_LOAD_HISTORY.remove(0);
                }

            } catch (Exception e) {
                log.error("性能监控采集失败", e);
            }
        }, 0, period, unit);

        log.info("系统性能监控已启动，采集间隔: {} {}", period, unit);
    }

    /**
     * 停止性能监控
     */
    public static synchronized void stopMonitor() {
        if (!MONITOR_RUNNING.get()) {
            return;
        }

        MONITOR_RUNNING.set(false);
        if (MONITOR_EXECUTOR != null) {
            MONITOR_EXECUTOR.shutdown();
            try {
                if (!MONITOR_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    MONITOR_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                MONITOR_EXECUTOR.shutdownNow();
            }
            MONITOR_EXECUTOR = null;
        }

        log.info("系统性能监控已停止");
    }

    /**
     * 获取CPU使用历史
     */
    public static List<Double> getCpuUsageHistory() {
        return new ArrayList<>(CPU_USAGE_HISTORY);
    }

    /**
     * 获取内存使用历史
     */
    public static List<Double> getMemoryUsageHistory() {
        return new ArrayList<>(MEMORY_USAGE_HISTORY);
    }

    /**
     * 获取系统负载历史
     */
    public static List<SystemLoad> getSystemLoadHistory() {
        return new ArrayList<>(SYSTEM_LOAD_HISTORY);
    }

    /**
     * 清空监控历史
     */
    public static void clearMonitorHistory() {
        CPU_USAGE_HISTORY.clear();
        MEMORY_USAGE_HISTORY.clear();
        SYSTEM_LOAD_HISTORY.clear();
        log.debug("性能监控历史已清空");
    }

    /**
     * 获取监控状态
     */
    public static boolean isMonitorRunning() {
        return MONITOR_RUNNING.get();
    }

    /**
     * 健康检查
     */
    public static Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();

        try {
            // 检查各组件
            getCpuInfo();
            getMemoryInfo();
            getDiskInfo();

            health.put("status", "HEALTHY");
            health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            health.put("monitorRunning", isMonitorRunning());
            health.put("cacheSize", CACHE_MAP.size());
            health.put("cpuHistorySize", CPU_USAGE_HISTORY.size());
            health.put("memoryHistorySize", MEMORY_USAGE_HISTORY.size());

        } catch (Exception e) {
            health.put("status", "UNHEALTHY");
            health.put("error", e.getMessage());
            log.error("系统健康检查失败", e);
        }

        return health;
    }

    /**
     * 获取警告信息
     */
    public static List<String> getWarnings() {
        List<String> warnings = new ArrayList<>();

        try {
            // CPU警告
            double cpuUsage = getCpuUsage(1000);
            if (cpuUsage > 90) {
                warnings.add(String.format("CPU使用率过高: %.1f%%", cpuUsage));
            } else if (cpuUsage > 80) {
                warnings.add(String.format("CPU使用率较高: %.1f%%", cpuUsage));
            }

            // 内存警告
            double memoryUsage = getMemoryUsage();
            if (memoryUsage > 90) {
                warnings.add(String.format("内存使用率过高: %.1f%%", memoryUsage));
            } else if (memoryUsage > 80) {
                warnings.add(String.format("内存使用率较高: %.1f%%", memoryUsage));
            }

            // 磁盘警告
            List<Map<String, Object>> disks = getDiskUsage();
            for (Map<String, Object> disk : disks) {
                String usageStr = (String) disk.get("usage");
                if (usageStr != null) {
                    double usage = Double.parseDouble(usageStr.replace("%", ""));
                    if (usage > 90) {
                        warnings.add(String.format("磁盘[%s]空间不足: %s", disk.get("name"), usageStr));
                    } else if (usage > 80) {
                        warnings.add(String.format("磁盘[%s]空间紧张: %s", disk.get("name"), usageStr));
                    }
                }
            }

        } catch (Exception e) {
            warnings.add("获取系统警告信息失败: " + e.getMessage());
        }

        return warnings;
    }

    /**
     * 优雅关闭
     */
    public static void shutdown() {
        stopMonitor();
        clearCache();
        clearMonitorHistory();
        SYSTEM_INFO = null;
        log.info("系统信息工具已关闭");
    }

    /**
     * 静态内部类实现安全的单例延迟加载
     */
    private static class SystemInfoHolder {
        private static final SystemInfo INSTANCE = new SystemInfo();
    }

    /**
     * 获取SystemInfo实例（替代双重检查锁的方案）
     */
    public static SystemInfo getSystemInfoLazy() {
        return SystemInfoHolder.INSTANCE;
    }
}