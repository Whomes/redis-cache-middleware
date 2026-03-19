package com.whomes.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Redis 分布式缓存中间件启动类
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class RedisCacheMiddlewareApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisCacheMiddlewareApplication.class, args);
    }
}
