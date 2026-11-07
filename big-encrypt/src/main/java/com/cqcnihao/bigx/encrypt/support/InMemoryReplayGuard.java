package com.cqcnihao.bigx.encrypt.support;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的防重放实现（单实例适用）
 * <p>
 * 采用惰性清理：每次登记时顺带扫描并剔除过期条目，避免引入额外线程。
 * 注意 nonce 量级很大时扫描成本会上升，生产多实例请换成 Redis 实现。
 *
 * @author bigweb
 */
public class InMemoryReplayGuard implements ReplayGuard {

    private static final int MAX_ENTRIES = 100_000;

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    /** nonce 存活时长（毫秒），应大于 timestamp 有效期 */
    private final long ttlMillis;

    public InMemoryReplayGuard(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    @Override
    public boolean accept(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        purge(now);
        if (seen.size() >= MAX_ENTRIES) {
            // 兜底：容量触顶直接清理，避免内存无限增长
            seen.clear();
        }
        return seen.putIfAbsent(nonce, now) == null;
    }

    private void purge(long now) {
        if (seen.size() < 1024) {
            return;
        }
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > ttlMillis) {
                it.remove();
            }
        }
    }
}
