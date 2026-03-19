package com.whomes.cache.middleware.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 核心配置类
 *
 * <p>配置 RedisTemplate 和 CacheManager，支持 JSON 序列化和自定义缓存配置</p>
 *
 * @author whomes
 * @version 1.0.0
 * @since 2024-03-19
 */
@Configuration
public class RedisConfig extends CachingConfigurerSupport {

    /**
     * 默认缓存过期时间（1小时）
     */
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * 短时效缓存过期时间（5分钟）
     */
    private static final Duration SHORT_TTL = Duration.ofMinutes(5);

    /**
     * 长时效缓存过期时间（24小时）
     */
    private static final Duration LONG_TTL = Duration.ofHours(24);

    /**
     * 配置 RedisTemplate
     *
     * <p>使用 Jackson2JsonRedisSerializer 进行 JSON 序列化，支持复杂对象</p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 配置 Jackson2JsonRedisSerializer
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = createJacksonSerializer();

        // 配置 StringRedisSerializer
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Key 采用 String 序列化方式
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // Value 采用 Jackson JSON 序列化方式
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 CacheManager
     *
     * <p>支持多个缓存区域，每个区域可以配置不同的过期时间</p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 CacheManager
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = createCacheConfiguration(DEFAULT_TTL);

        // 自定义缓存配置映射
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>(8);
        configMap.put("short", createCacheConfiguration(SHORT_TTL));
        configMap.put("long", createCacheConfiguration(LONG_TTL));
        configMap.put("user", createCacheConfiguration(Duration.ofMinutes(30)));
        configMap.put("order", createCacheConfiguration(Duration.ofMinutes(10)));
        configMap.put("product", createCacheConfiguration(Duration.ofHours(2)));
        configMap.put("session", createCacheConfiguration(Duration.ofMinutes(30)));
        configMap.put("lock", createCacheConfiguration(Duration.ofSeconds(30)));
        configMap.put("rateLimit", createCacheConfiguration(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .transactionAware()
                .build();
    }

    /**
     * 创建 Jackson2JsonRedisSerializer
     *
     * @return 配置好的 Jackson2JsonRedisSerializer
     */
    private Jackson2JsonRedisSerializer<Object> createJacksonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 指定要序列化的域，field、get、set 以及修饰符范围，ANY 表示包括 private 和 public
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 指定序列化输入的类型，类必须是非 final 修饰的
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        // 禁用日期时间戳写入
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new Jackson2JsonRedisSerializer<>(Object.class);
    }

    /**
     * 创建缓存配置
     *
     * @param ttl 过期时间
     * @return RedisCacheConfiguration
     */
    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = createJacksonSerializer();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
n                        .fromSerializer(jackson2JsonRedisSerializer))
                .disableCachingNullValues();
    }

    /**
     * 自定义 Key 生成器
     *
     * <p>格式：类名:方法名:参数值</p>
     *
     * @return KeyGenerator
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(":");
            sb.append(method.getName()).append(":");
            for (Object param : params) {
                if (param != null) {
                    sb.append(param.toString());
                } else {
                    sb.append("null");
                }
                sb.append("-");
            }
            // 移除最后一个分隔符
            if (sb.charAt(sb.length() - 1) == '-') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        };
    }
}
