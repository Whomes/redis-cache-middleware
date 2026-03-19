package com.whomes.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解（基于 Redisson RedLock）
 *
 * 使用示例：
 * {@code @RedisLock(key = "lock:order:{#orderId}", waitTime = 3, leaseTime = 10)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisLock {

    /**
     * 锁 Key，支持 SpEL 表达式
     */
    String key();

    /**
     * 等待锁的最大时间，默认 3 秒
     */
    long waitTime() default 3;

    /**
     * 锁持有时间，默认 10 秒（-1 表示看门狗自动续期）
     */
    long leaseTime() default 10;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的提示信息
     */
    String failMessage() default "系统繁忙，请稍后重试";
}
