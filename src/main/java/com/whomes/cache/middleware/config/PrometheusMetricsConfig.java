package com.whomes.cache.middleware.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus 监控配置类
 *
 * <p>配置 Micrometer 指标采集，支持 Prometheus 格式导出</p>
 *
 * @author whomes
 * @version 1.0.0
 * @since 2024-03-19
 */
@Slf4j
@Configuration
public class PrometheusMetricsConfig {

    /**
     * 配置 MeterRegistry 自定义标签
     *
     * @return MeterRegistryCustomizer
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        Tags.of(
                                "application", "redis-cache-middleware",
                                "version", "1.0.0"
                        )
                );
    }

    /**
     * 注册 Caffeine 缓存指标
     *
     * @param meterRegistry MeterRegistry
     * @return CaffeineCacheMetrics
     */
    @Bean
    public CaffeineCacheMetrics caffeineCacheMetrics(MeterRegistry meterRegistry) {
        log.info("[PrometheusMetrics] 缓存指标监控已启用");
        return new CaffeineCacheMetrics(null, "caffeine.cache", Tags.empty());
    }
}
