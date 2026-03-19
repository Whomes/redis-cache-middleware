package com.whomes.cache.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Redis 全链路监控
 * 基于 Prometheus + Grafana 监控 QPS、内存使用率、命中率、槽位分布等指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMetricsCollector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // 缓存命中率告警阈值
    private static final double HIT_RATE_THRESHOLD = 0.70;

    @PostConstruct
    public void init() {
        // 注册自定义指标
        registerMetrics();
    }

    /**
     * 注册监控指标
     */
    private void registerMetrics() {
        // Redis 内存使用率
        Gauge.builder("redis.memory.usage", this, RedisMetricsCollector::getMemoryUsage)
                .description("Redis 内存使用率")
                .register(meterRegistry);

        // Redis 命中率
        Gauge.builder("redis.hit.rate", this, RedisMetricsCollector::getHitRate)
                .description("Redis 缓存命中率")
                .register(meterRegistry);

        // Redis 连接数
        Gauge.builder("redis.connected.clients", this, RedisMetricsCollector::getConnectedClients)
                .description("Redis 连接客户端数")
                .register(meterRegistry);

        // Redis QPS
        Gauge.builder("redis.ops.per.second", this, RedisMetricsCollector::getOpsPerSecond)
                .description("Redis 每秒操作数")
                .register(meterRegistry);
    }

    /**
     * 定时采集 Redis 指标（每 30 秒）
     */
    @Scheduled(fixedRate = 30000)
    public void collectMetrics() {
        try {
            Properties info = redisTemplate.execute(connection -> connection.info());
            if (info == null) return;

            // 解析 INFO 命令输出
            Map<String, String> stats = parseInfo(info);

            // 记录指标
            recordGauge("redis.memory.used", stats.get("used_memory"));
            recordGauge("redis.memory.peak", stats.get("used_memory_peak"));
            recordGauge("redis.keys.count", stats.get("db0:keys"));
            recordGauge("redis.expires.count", stats.get("db0:expires"));

            // 检查命中率告警
            double hitRate = getHitRate(stats);
            if (hitRate < HIT_RATE_THRESHOLD) {
                log.warn("[RedisMetrics] 缓存命中率低于阈值: {} < {}", hitRate, HIT_RATE_THRESHOLD);
                // 这里可以触发邮件/钉钉告警
            }

            log.debug("[RedisMetrics] 指标采集完成，命中率: {}", hitRate);

        } catch (Exception e) {
            log.error("[RedisMetrics] 指标采集失败", e);
        }
    }

    /**
     * 解析 INFO 命令输出
     */
    private Map<String, String> parseInfo(Properties info) {
        Map<String, String> map = new java.util.HashMap<>();
        info.forEach((k, v) -> map.put(String.valueOf(k), String.valueOf(v)));
        return map;
    }

    /**
     * 获取内存使用率
     */
    private double getMemoryUsage() {
        try {
            Properties info = redisTemplate.execute(connection -> connection.info("memory"));
            if (info == null) return 0;
            String used = info.getProperty("used_memory");
            return used != null ? Double.parseDouble(used) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取缓存命中率
     */
    private double getHitRate() {
        try {
            Properties info = redisTemplate.execute(connection -> connection.info("stats"));
            if (info == null) return 0;
            return getHitRate(parseInfo(info));
        } catch (Exception e) {
            return 0;
        }
    }

    private double getHitRate(Map<String, String> stats) {
        String hits = stats.get("keyspace_hits");
        String misses = stats.get("keyspace_misses");
        if (hits == null || misses == null) return 0;

        long hitCount = Long.parseLong(hits);
        long missCount = Long.parseLong(misses);
        long total = hitCount + missCount;

        return total == 0 ? 0 : (double) hitCount / total;
    }

    /**
     * 获取连接客户端数
     */
    private double getConnectedClients() {
        try {
            Properties info = redisTemplate.execute(connection -> connection.info("clients"));
            if (info == null) return 0;
            String clients = info.getProperty("connected_clients");
            return clients != null ? Double.parseDouble(clients) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取每秒操作数
     */
    private double getOpsPerSecond() {
        try {
            Properties info = redisTemplate.execute(connection -> connection.info("stats"));
            if (info == null) return 0;
            String ops = info.getProperty("instantaneous_ops_per_sec");
            return ops != null ? Double.parseDouble(ops) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void recordGauge(String name, String value) {
        if (value != null) {
            try {
                meterRegistry.gauge(name, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * 记录缓存操作耗时
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, String operation) {
        sample.stop(meterRegistry.timer("redis.operation", "type", operation));
    }
}
