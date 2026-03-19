package com.whomes.cache.hotkey;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热 Key 检测与限流
 * 基于 Redisson 实现本地缓存 + Redis 热点数据隔离
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeyDetector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // 热 Key 访问计数器
    private final Map<String, AtomicLong> keyAccessCount = new ConcurrentHashMap<>();

    // 热 Key 阈值：每分钟访问超过 1000 次
    private static final long HOT_KEY_THRESHOLD = 1000;

    // 限流配置：热 Key 查询 QPS 限制
    private static final long HOT_KEY_QPS_LIMIT = 5000;

    // 当前热 Key 集合
    private final Set<String> hotKeys = ConcurrentHashMap.newKeySet();

    /**
     * 记录 Key 访问
     */
    public void recordAccess(String key) {
        keyAccessCount.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 判断是否为热 Key
     */
    public boolean isHotKey(String key) {
        return hotKeys.contains(key);
    }

    /**
     * 检查是否允许访问（限流）
     */
    public boolean allowAccess(String key) {
        if (!hotKeys.contains(key)) {
            return true;
        }
        // 简单限流：基于计数器
        AtomicLong count = keyAccessCount.get(key);
        if (count == null) return true;

        // 每分钟重置计数
        if (count.get() > HOT_KEY_QPS_LIMIT) {
            meterRegistry.counter("hotkey.throttled", "key", key).increment();
            log.warn("[HotKeyDetector] 热 Key 限流触发，key={}", key);
            return false;
        }
        return true;
    }

    /**
     * 定时检测热 Key（每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    public void detectHotKeys() {
        log.debug("[HotKeyDetector] 开始检测热 Key...");

        Set<String> newHotKeys = ConcurrentHashMap.newKeySet();

        keyAccessCount.forEach((key, count) -> {
            long accessCount = count.getAndSet(0); // 重置计数器
            if (accessCount >= HOT_KEY_THRESHOLD) {
                newHotKeys.add(key);
                if (!hotKeys.contains(key)) {
                    log.warn("[HotKeyDetector] 发现新热 Key: {}，访问次数: {}", key, accessCount);
                    meterRegistry.gauge("hotkey.access", hotKeys, k -> (double) accessCount);
                }
            }
        });

        hotKeys.clear();
        hotKeys.addAll(newHotKeys);

        log.info("[HotKeyDetector] 检测完成，当前热 Key 数量: {}", hotKeys.size());
    }

    /**
     * 获取当前热 Key 列表
     */
    public Set<String> getHotKeys() {
        return hotKeys;
    }

    /**
     * 清除热 Key 标记（用于测试或手动干预）
     */
    public void clearHotKey(String key) {
        hotKeys.remove(key);
        keyAccessCount.remove(key);
        log.info("[HotKeyDetector] 手动清除热 Key: {}", key);
    }
}
