package com.whomes.cache.middleware.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.whomes.cache.middleware.properties.LocalCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置类
 *
 * <p>配置本地缓存作为 L1 缓存层，用于热Key的快速访问</p>
 *
 * @author whomes
 * @version 1.0.0
 * @since 2024-03-19
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LocalCacheProperties.class)
@ConditionalOnProperty(prefix = "cache.middleware.local", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaffeineCacheConfig {

    @Resource
    private LocalCacheProperties localCacheProperties;

    /**
     * 配置 Caffeine CacheManager
     *
     * @return CaffeineCacheManager
     */
    @Bean("caffeineCacheManager")
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineConfig());
        cacheManager.setAllowNullValues(false);
        
        // 预定义缓存名称
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "hotKeyCache",
            "localFallbackCache",
            "degradeCache"
        ));
        
        log.info("[CaffeineCache] 本地缓存已启用，最大条目数: {}, 过期时间: {}秒", 
                localCacheProperties.getMaxSize(), 
                localCacheProperties.getExpireSeconds());
        
        return cacheManager;
    }

    /**
     * 配置 Caffeine 缓存参数
     *
     * @return Caffeine 配置
     */
    private Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                // 设置缓存最大条目数
                .maximumSize(localCacheProperties.getMaxSize())
                // 设置写入后过期时间
                .expireAfterWrite(localCacheProperties.getExpireSeconds(), TimeUnit.SECONDS)
                // 设置刷新时间（异步刷新）
                .refreshAfterWrite(localCacheProperties.getRefreshSeconds(), TimeUnit.SECONDS)
                // 开启统计功能
                .recordStats()
                // 移除监听器
                .removalListener((key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[CaffeineCache] Key: {} 被移除，原因: {}", key, cause);
                    }
                });
    }

    /**
     * 配置热Key专用缓存
     *
     * @return Caffeine 配置
     */
    @Bean("hotKeyCaffeine")
    public Caffeine<Object, Object> hotKeyCaffeine() {
        return Caffeine.newBuilder()
                .maximumSize(localCacheProperties.getMaxSize() / 2)
                .expireAfterWrite(300, TimeUnit.SECONDS)
                .recordStats();
    }
}
