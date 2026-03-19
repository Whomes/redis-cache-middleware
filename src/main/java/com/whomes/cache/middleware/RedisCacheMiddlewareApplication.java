package com.whomes.cache.middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Redis分布式缓存中间件启动类
 *
 * <p>本中间件提供以下核心能力：</p>
 * <ul>
 *   <li>Redis Sentinel + Cluster 混合架构支持</li>
 *   <li>大Key扫描与拆分</li>
 *   <li>热Key本地缓存隔离</li>
 *   <li>多级降级策略</li>
 *   <li>分布式锁</li>
 *   <li>Prometheus + Grafana 监控</li>
 * </ul>
 *
 * @author whomes
 * @version 1.0.0
 * @since 2024-03-19
 */
@SpringBootApplication
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class RedisCacheMiddlewareApplication {

    /**
     * 应用入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RedisCacheMiddlewareApplication.class, args);
    }
}
