package com.whomes.cache.controller;

import com.whomes.cache.aspect.RedisCacheAspect;
import com.whomes.cache.bigkey.BigKeyScanner;
import com.whomes.cache.hotkey.HotKeyDetector;
import com.whomes.cache.monitor.RedisMetricsCollector;
import com.whomes.cache.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缓存管理 API
 * 提供缓存监控、热 Key 管理、大 Key 扫描等接口
 */
@Slf4j
@RestController
@RequestMapping("/cache/admin")
@RequiredArgsConstructor
public class CacheAdminController {

    private final RedisCacheAspect cacheAspect;
    private final HotKeyDetector hotKeyDetector;
    private final BigKeyScanner bigKeyScanner;
    private final RedisMetricsCollector metricsCollector;
    private final RedisUtil redisUtil;

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("hitCount", cacheAspect.getHitCount());
        stats.put("missCount", cacheAspect.getMissCount());
        stats.put("hitRate", String.format("%.2f%%", cacheAspect.getHitRate() * 100));
        return stats;
    }

    /**
     * 获取热 Key 列表
     */
    @GetMapping("/hotkeys")
    public Set<String> getHotKeys() {
        return hotKeyDetector.getHotKeys();
    }

    /**
     * 清除热 Key
     */
    @PostMapping("/hotkeys/{key}/clear")
    public String clearHotKey(@PathVariable String key) {
        hotKeyDetector.clearHotKey(key);
        return "热 Key 已清除: " + key;
    }

    /**
     * 手动触发大 Key 扫描
     */
    @PostMapping("/bigkeys/scan")
    public String scanBigKeys() {
        new Thread(() -> bigKeyScanner.scanBigKeys()).start();
        return "大 Key 扫描已启动";
    }

    /**
     * 删除指定 Key
     */
    @DeleteMapping("/keys/{key}")
    public String deleteKey(@PathVariable String key) {
        redisUtil.delete(key);
        return "Key 已删除: " + key;
    }

    /**
     * 批量删除 Key（支持通配符）
     */
    @DeleteMapping("/keys")
    public String deleteKeys(@RequestParam String pattern) {
        redisUtil.deleteByPattern(pattern);
        return "Pattern 匹配的 Key 已删除: " + pattern;
    }

    /**
     * 获取 Key 信息
     */
    @GetMapping("/keys/{key}")
    public Map<String, Object> getKeyInfo(@PathVariable String key) {
        Map<String, Object> info = new HashMap<>();
        info.put("key", key);
        info.put("exists", redisUtil.hasKey(key));
        info.put("expire", redisUtil.getExpire(key));
        info.put("value", redisUtil.get(key));
        return info;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "redis-cache-middleware");
        return health;
    }
}
