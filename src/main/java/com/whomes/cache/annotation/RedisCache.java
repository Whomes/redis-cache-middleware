package com.whomes.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 自定义缓存注解
 * 一行注解实现缓存查询 + 更新 + 失效，减少重复代码 60%
 *
 * 使用示例：
 * {@code @RedisCache(key = "biz:merchant:{#id}:info", expire = 300)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisCache {

    /**
     * 缓存 Key，支持 SpEL 表达式，如 "biz:merchant:{#id}:coupon"
     */
    String key();

    /**
     * 过期时间，默认 300 秒
     */
    long expire() default 300;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 是否开启本地缓存（热 Key 场景）
     */
    boolean localCache() default false;

    /**
     * 本地缓存过期时间（秒），默认 30 秒
     */
    long localExpire() default 30;

    /**
     * 缓存空值，防止缓存穿透
     */
    boolean cacheNull() default true;

    /**
     * 空值过期时间（秒），默认 60 秒
     */
    long nullExpire() default 60;
}
