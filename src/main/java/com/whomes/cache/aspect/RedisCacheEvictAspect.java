package com.whomes.cache.aspect;

import com.whomes.cache.annotation.RedisCacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * @RedisCacheEvict 注解 AOP 切面
 * 支持通配符批量删除缓存
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisCacheEvictAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer discoverer =
            new LocalVariableTableParameterNameDiscoverer();

    @Before("@annotation(cacheEvict) && args(..) && @annotation(cacheEvict)")
    public void beforeEvict(JoinPoint joinPoint, RedisCacheEvict cacheEvict) {
        if (cacheEvict.beforeInvocation()) {
            doEvict(joinPoint, cacheEvict);
        }
    }

    @After("@annotation(cacheEvict)")
    public void afterEvict(JoinPoint joinPoint, RedisCacheEvict cacheEvict) {
        if (!cacheEvict.beforeInvocation()) {
            doEvict(joinPoint, cacheEvict);
        }
    }

    private void doEvict(JoinPoint joinPoint, RedisCacheEvict cacheEvict) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String keyPattern = parseSpEL(cacheEvict.key(), method, joinPoint.getArgs());

        if (keyPattern.contains("*")) {
            Set<String> keys = redisTemplate.keys(keyPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[CacheEvict] 批量删除缓存，pattern={}, count={}", keyPattern, keys.size());
            }
        } else {
            redisTemplate.delete(keyPattern);
            log.info("[CacheEvict] 删除缓存，key={}", keyPattern);
        }
    }

    private String parseSpEL(String spEL, Method method, Object[] args) {
        String[] paramNames = discoverer.getParameterNames(method);
        if (paramNames == null || !spEL.contains("{#")) return spEL;
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        String expr = spEL.replaceAll("\\{(#[^}]+)}", "$1");
        try {
            return parser.parseExpression(expr).getValue(context, String.class);
        } catch (Exception e) {
            return spEL;
        }
    }
}
