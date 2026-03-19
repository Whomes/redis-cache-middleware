package com.whomes.cache.fallback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多级降级策略处理器
 * Redis 节点故障时：先降级到本地缓存 → 再降级到数据库（带熔断）→ 最后返回兜底数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheFallbackHandler {

    // 熔断器状态
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long circuitOpenTime = 0;
    private static final int FAILURE_THRESHOLD = 10;
    private static final long CIRCUIT_TIMEOUT_MS = 30000; // 30秒

    /**
     * 执行降级策略
     */
    public Object fallback(ProceedingJoinPoint joinPoint, String cacheKey, Exception exception) throws Throwable {
        log.warn("[Fallback] 缓存异常，启动降级，key={}", cacheKey);

        // 1. 检查熔断器状态
        if (isCircuitOpen()) {
            log.warn("[Fallback] 熔断器开启，直接返回兜底数据");
            return getDefaultData(joinPoint);
        }

        // 2. 尝试执行原方法（降级到数据库）
        try {
            Object result = joinPoint.proceed();
            failureCount.set(0); // 成功则重置失败计数
            return result;
        } catch (Exception e) {
            handleFailure();
            log.error("[Fallback] 数据库查询也失败，返回兜底数据", e);
            return getDefaultData(joinPoint);
        }
    }

    /**
     * 检查熔断器是否开启
     */
    private boolean isCircuitOpen() {
        if (failureCount.get() >= FAILURE_THRESHOLD) {
            if (System.currentTimeMillis() - circuitOpenTime < CIRCUIT_TIMEOUT_MS) {
                return true;
            }
            // 熔断超时，尝试半开
            failureCount.set(FAILURE_THRESHOLD / 2);
        }
        return false;
    }

    /**
     * 记录失败
     */
    private void handleFailure() {
        int count = failureCount.incrementAndGet();
        if (count >= FAILURE_THRESHOLD) {
            circuitOpenTime = System.currentTimeMillis();
            log.error("[Fallback] 熔断器开启，失败次数={}", count);
        }
    }

    /**
     * 获取兜底数据
     */
    private Object getDefaultData(ProceedingJoinPoint joinPoint) {
        Class<?> returnType = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType();
        if (returnType == String.class) return "";
        if (returnType == Integer.class || returnType == int.class) return 0;
        if (returnType == Long.class || returnType == long.class) return 0L;
        if (returnType == Boolean.class || returnType == boolean.class) return false;
        if (returnType.isAssignableFrom(java.util.List.class)) return java.util.Collections.emptyList();
        if (returnType.isAssignableFrom(java.util.Map.class)) return java.util.Collections.emptyMap();
        return null;
    }
}
