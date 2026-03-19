package com.whomes.cache.bigkey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 大 Key 扫描与拆分工具
 * 基于 SCAN 命令 + 内存占用统计，定时检测并拆分大 Key
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BigKeyScanner {

    private final RedisTemplate<String, Object> redisTemplate;

    // 大 Key 阈值：List/Set/ZSet 元素数超过 1000，String 超过 10KB
    private static final int LIST_THRESHOLD = 1000;
    private static final int STRING_THRESHOLD = 10 * 1024; // 10KB

    /**
     * 定时扫描大 Key（每天凌晨 2 点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanBigKeys() {
        log.info("[BigKeyScanner] 开始扫描大 Key...");

        List<BigKeyInfo> bigKeys = new ArrayList<>();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .count(100)
                    .build();

            Cursor<byte[]> cursor = connection.scan(options);
            while (cursor.hasNext()) {
                byte[] keyBytes = cursor.next();
                String key = new String(keyBytes, StandardCharsets.UTF_8);

                BigKeyInfo info = analyzeKey(connection, key);
                if (info != null && info.isBigKey()) {
                    bigKeys.add(info);
                    log.warn("[BigKeyScanner] 发现大 Key: {}，类型: {}，大小: {} bytes",
                            key, info.getType(), info.getSize());
                }
            }
            return null;
        });

        log.info("[BigKeyScanner] 扫描完成，发现 {} 个大 Key", bigKeys.size());

        // 自动拆分大 Key
        for (BigKeyInfo bigKey : bigKeys) {
            if (shouldSplit(bigKey)) {
                splitBigKey(bigKey);
            }
        }
    }

    /**
     * 分析单个 Key 的大小
     */
    private BigKeyInfo analyzeKey(RedisConnection connection, String key) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            String type = connection.type(keyBytes);
            long size = 0;

            switch (type) {
                case "string":
                    size = connection.strLen(keyBytes);
                    break;
                case "list":
                    size = connection.lLen(keyBytes);
                    break;
                case "set":
                    size = connection.sCard(keyBytes);
                    break;
                case "zset":
                    size = connection.zCard(keyBytes);
                    break;
                case "hash":
                    size = connection.hLen(keyBytes);
                    break;
            }

            return new BigKeyInfo(key, type, size);
        } catch (Exception e) {
            log.error("[BigKeyScanner] 分析 Key 失败: {}", key, e);
            return null;
        }
    }

    /**
     * 判断是否为大 Key
     */
    private boolean shouldSplit(BigKeyInfo info) {
        if ("string".equals(info.getType())) {
            return info.getSize() > STRING_THRESHOLD;
        }
        return info.getSize() > LIST_THRESHOLD;
    }

    /**
     * 拆分大 Key
     * 示例：将 List 类型的优惠券列表拆分为多个 Hash Key
     */
    private void splitBigKey(BigKeyInfo info) {
        log.info("[BigKeyScanner] 开始拆分大 Key: {}", info.getKey());

        // 实际拆分逻辑根据业务场景实现
        // 例如：将大 List 拆分为多个小 List 或 Hash
        // 这里提供拆分策略示例

        String key = info.getKey();
        String type = info.getType();

        if ("list".equals(type)) {
            // 将 List 拆分为多个 Hash 分片
            // 如：merchant:coupons:1001 -> merchant:coupons:1001:0, merchant:coupons:1001:1, ...
            log.info("[BigKeyScanner] 建议将 List Key '{}' 拆分为 Hash 分片存储", key);
        } else if ("string".equals(type)) {
            // 将大 String 拆分为多个小 String
            log.info("[BigKeyScanner] 建议将 String Key '{}' 拆分为多个小 Key", key);
        }
    }

    /**
     * 大 Key 信息
     */
    public static class BigKeyInfo {
        private final String key;
        private final String type;
        private final long size;

        public BigKeyInfo(String key, String type, long size) {
            this.key = key;
            this.type = type;
            this.size = size;
        }

        public boolean isBigKey() {
            if ("string".equals(type)) {
                return size > STRING_THRESHOLD;
            }
            return size > LIST_THRESHOLD;
        }

        public String getKey() { return key; }
        public String getType() { return type; }
        public long getSize() { return size; }
    }
}
