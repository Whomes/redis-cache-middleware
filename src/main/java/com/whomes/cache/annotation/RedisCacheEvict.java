package com.whomes.cache.annotation;

import java.lang.annotation.*;

/**
 * 缓存失效注解
 *
 * 使用示例：
 * {@code @RedisCacheEvict(key = "biz:merchant:{#id}:*")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisCacheEvict {

    /**
     * 要删除的缓存 Key，支持 SpEL 表达式和通配符
     */
    String key();

    /**
     * 是否在方法执行前删除（默认方法执行后删除）
     */
    boolean beforeInvocation() default false;
}
