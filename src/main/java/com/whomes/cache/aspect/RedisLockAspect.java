package com.whomes.cache.aspect;

import com.whomes.cache.annotation.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @RedisLock 注解 AOP 切面
 * 基于 Redisson RedLock 实现分布式锁，支持自动续期（看门狗）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer discoverer =
            new LocalVariableTableParameterNameDiscoverer();

    @Around("@annotation(redisLock)")
    public Object around(ProceedingJoinPoint joinPoint, RedisLock redisLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String lockKey = parseSpEL(redisLock.key(), method, joinPoint.getArgs());

        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(
                    redisLock.waitTime(),
                    redisLock.leaseTime(),
                    redisLock.timeUnit()
            );

            if (!acquired) {
                log.warn("[RedisLock] 获取锁失败，key={}", lockKey);
                throw new RuntimeException(redisLock.failMessage());
            }

            log.debug("[RedisLock] 获取锁成功，key={}", lockKey);
            return joinPoint.proceed();

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[RedisLock] 释放锁，key={}", lockKey);
            }
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
