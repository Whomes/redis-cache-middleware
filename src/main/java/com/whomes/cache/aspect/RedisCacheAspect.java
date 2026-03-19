package com.whomes.cache.aspect;

import com.whomes.cache.annotation.RedisCache;
import com.whomes.cache.fallback.CacheFallbackHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @RedisCache 注解 AOP 切面
 * 实现缓存查询 + 更新 + 失效，支持本地缓存（热 Key）、空值缓存（防穿透）、多级降级
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisCacheAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager caffeineCacheManager;
    private final CacheFallbackHandler fallbackHandler;
    private final MeterRegistry meterRegistry;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer discoverer =
            new LocalVariableTableParameterNameDiscoverer();

    // 监控计数器
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    private static final String NULL_VALUE = "CACHE_NULL";

    @Around("@annotation(redisCache)")
    public Object around(ProceedingJoinPoint joinPoint, RedisCache redisCache) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 解析 SpEL Key
        String cacheKey = parseSpEL(redisCache.key(), method, joinPoint.getArgs());

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 1. 优先查本地缓存（热 Key 场景）
            if (redisCache.localCache()) {
                Cache localCache = caffeineCacheManager.getCache("hotkey");
                if (localCache != null) {
                    Cache.ValueWrapper localVal = localCache.get(cacheKey);
                    if (localVal != null) {
                        hitCount.incrementAndGet();
                        meterRegistry.counter("cache.hit", "level", "local", "key", cacheKey).increment();
                        log.debug("[Cache] 本地缓存命中，key={}", cacheKey);
                        return localVal.get();
                    }
                }
            }

            // 2. 查 Redis 缓存
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (NULL_VALUE.equals(cached)) {
                    log.debug("[Cache] 命中空值缓存，防止穿透，key={}", cacheKey);
                    return null;
                }
                hitCount.incrementAndGet();
                meterRegistry.counter("cache.hit", "level", "redis", "key", cacheKey).increment();
                log.debug("[Cache] Redis 缓存命中，key={}", cacheKey);

                // 回填本地缓存
                if (redisCache.localCache()) {
                    Cache localCache = caffeineCacheManager.getCache("hotkey");
                    if (localCache != null) localCache.put(cacheKey, cached);
                }
                return cached;
            }

            // 3. 缓存未命中，执行方法
            missCount.incrementAndGet();
            meterRegistry.counter("cache.miss", "key", cacheKey).increment();
            log.debug("[Cache] 缓存未命中，执行方法，key={}", cacheKey);

            Object result = joinPoint.proceed();

            // 4. 写入缓存
            if (result != null) {
                redisTemplate.opsForValue().set(cacheKey, result,
                        redisCache.expire(), redisCache.timeUnit());
                if (redisCache.localCache()) {
                    Cache localCache = caffeineCacheManager.getCache("hotkey");
                    if (localCache != null) localCache.put(cacheKey, result);
                }
            } else if (redisCache.cacheNull()) {
                // 缓存空值，防止缓存穿透
                redisTemplate.opsForValue().set(cacheKey, NULL_VALUE,
                        redisCache.nullExpire(), TimeUnit.SECONDS);
            }

            return result;

        } catch (Exception e) {
            log.error("[Cache] Redis 异常，启动降级策略，key={}", cacheKey, e);
            meterRegistry.counter("cache.error", "key", cacheKey).increment();
            // 降级：执行原方法
            return fallbackHandler.fallback(joinPoint, cacheKey, e);
        } finally {
            sample.stop(meterRegistry.timer("cache.response.time", "key", cacheKey));
        }
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpEL(String spEL, Method method, Object[] args) {
        String[] paramNames = discoverer.getParameterNames(method);
        if (paramNames == null || !spEL.contains("{#")) {
            return spEL;
        }
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        // 替换 {#param} 为 SpEL 表达式
        String expr = spEL.replaceAll("\\{(#[^}]+)}", "$1");
        try {
            return parser.parseExpression(expr).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("[Cache] SpEL 解析失败，使用原始 Key，spEL={}", spEL);
            return spEL;
        }
    }

    public long getHitCount() { return hitCount.get(); }
    public long getMissCount() { return missCount.get(); }
    public double getHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }
}
