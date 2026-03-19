package com.whomes.cache.controller;

import com.whomes.cache.annotation.RedisCache;
import com.whomes.cache.annotation.RedisCacheEvict;
import com.whomes.cache.annotation.RedisLock;
import com.whomes.cache.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 示例控制器：展示注解使用方式
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final RedisUtil redisUtil;

    /**
     * 示例 1：基础缓存注解
     * 一行注解实现缓存查询 + 更新
     */
    @GetMapping("/merchant/{id}")
    @RedisCache(key = "biz:merchant:{#id}:info", expire = 300)
    public Map<String, Object> getMerchantInfo(@PathVariable Long id) {
        log.info("[Demo] 查询商家信息，id={}", id);
        // 模拟数据库查询
        Map<String, Object> merchant = new HashMap<>();
        merchant.put("id", id);
        merchant.put("name", "商家" + id);
        merchant.put("address", "北京市朝阳区");
        return merchant;
    }

    /**
     * 示例 2：热 Key 场景（本地缓存 + Redis）
     * 秒杀商品库存，高并发访问
     */
    @GetMapping("/seckill/{goodsId}/stock")
    @RedisCache(key = "biz:seckill:{#goodsId}:stock", expire = 60, localCache = true)
    public Map<String, Object> getSeckillStock(@PathVariable Long goodsId) {
        log.info("[Demo] 查询秒杀库存，goodsId={}", goodsId);
        Map<String, Object> stock = new HashMap<>();
        stock.put("goodsId", goodsId);
        stock.put("stock", 100);
        stock.put("version", System.currentTimeMillis());
        return stock;
    }

    /**
     * 示例 3：分布式锁（扣减库存）
     */
    @PostMapping("/seckill/{goodsId}/deduct")
    @RedisLock(key = "lock:seckill:{#goodsId}", waitTime = 3, leaseTime = 10)
    public String deductStock(@PathVariable Long goodsId) {
        log.info("[Demo] 扣减库存，goodsId={}", goodsId);
        // 模拟扣减逻辑
        return "库存扣减成功";
    }

    /**
     * 示例 4：缓存失效（更新商家信息后清除缓存）
     */
    @PostMapping("/merchant/{id}/update")
    @RedisCacheEvict(key = "biz:merchant:{#id}:*")
    public String updateMerchant(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        log.info("[Demo] 更新商家信息，id={}", id);
        // 模拟更新数据库
        return "商家信息更新成功，相关缓存已清除";
    }

    /**
     * 示例 5：批量操作
     */
    @PostMapping("/batch/set")
    public String batchSet() {
        for (int i = 0; i < 100; i++) {
            redisUtil.set("demo:key:" + i, "value" + i, 300, TimeUnit.SECONDS);
        }
        return "批量写入 100 条数据完成";
    }

    /**
     * 示例 6：Hash 操作（商家优惠券列表）
     */
    @PostMapping("/merchant/{id}/coupon")
    public String addCoupon(@PathVariable Long id, @RequestParam String couponId, @RequestParam String couponInfo) {
        String key = RedisUtil.buildKey("merchant", id, "coupons");
        redisUtil.hSet(key, couponId, couponInfo);
        return "优惠券添加成功";
    }

    @GetMapping("/merchant/{id}/coupon/{couponId}")
    public Object getCoupon(@PathVariable Long id, @PathVariable String couponId) {
        String key = RedisUtil.buildKey("merchant", id, "coupons");
        return redisUtil.hGet(key, couponId);
    }
}
