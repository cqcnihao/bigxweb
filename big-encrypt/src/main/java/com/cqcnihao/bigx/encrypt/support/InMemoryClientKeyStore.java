package com.cqcnihao.bigx.encrypt.support;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的客户端公钥存储（单实例适用）
 * <p>
 * 与 {@link InMemoryReplayGuard} 同范式：惰性清理过期条目，不额外起线程。
 * 多实例部署请使用 Redis 实现，否则客户端在 A 实例登记的公钥在 B 实例上查不到，
 * 表现为「随机性验签失败」，排查成本极高。
 *
 * @author bigweb
 */
public class InMemoryClientKeyStore implements ClientKeyStore {

    private static final int MAX_ENTRIES = 20_000;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String publicKey, long expireAt) {
    }

    @Override
    public void save(String clientKeyId, String publicKey, long ttlSeconds) {
        if (clientKeyId == null || clientKeyId.isBlank() || publicKey == null) {
            return;
        }
        long now = System.currentTimeMillis();
        purge(now);
        if (store.size() >= MAX_ENTRIES) {
            store.clear();
        }
        store.put(clientKeyId, new Entry(publicKey, now + ttlSeconds * 1000L));
    }

    @Override
    public String find(String clientKeyId) {
        if (clientKeyId == null || clientKeyId.isBlank()) {
            return null;
        }
        Entry entry = store.get(clientKeyId);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() <= System.currentTimeMillis()) {
            store.remove(clientKeyId, entry);
            return null;
        }
        return entry.publicKey();
    }

    @Override
    public void remove(String clientKeyId) {
        if (clientKeyId != null) {
            store.remove(clientKeyId);
        }
    }

    private void purge(long now) {
        if (store.size() < 512) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (now >= e.getValue().expireAt()) {
                it.remove();
            }
        }
    }
}