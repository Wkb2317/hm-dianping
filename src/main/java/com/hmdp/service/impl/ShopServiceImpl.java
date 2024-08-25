package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Override
    @Transactional
    public Result updateShopById(Shop shop) {
        if (shop.getId() == null)
            return Result.fail("商户id为空");
        // 更新数据库
        shopMapper.updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryThoughCache(id);
        // 缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null)
            return Result.fail("不存在");
        return Result.ok(shop);
    }

    /**
     * 缓存穿透
     * 
     * @param id
     * @return
     */
    public Shop queryThoughCache(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 查询商户缓存
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);
        // 命中缓存
        if (!shopMap.isEmpty()) {
            if (shopMap.get("isNull").equals("true"))
                return null;
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // 没缓存，查库
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            stringRedisTemplate.opsForHash().put(shopKey, "isNull", "true");
            stringRedisTemplate.opsForHash().getOperations().expire(shopKey, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 更新缓存
        Map<String, Object> shopMapNew =
            BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? "" : fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(shopKey, shopMapNew);
        stringRedisTemplate.opsForHash().getOperations().expire(shopKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
    }

    /**
     * 缓存击穿
     * 
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 查询商户缓存
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);
        // 命中缓存
        if (!shopMap.isEmpty()) {
            if (shopMap.get("isNull") != null && shopMap.get("isNull").equals("true"))
                return null;
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // 没缓存，查库
        // 获取锁
        String lockKey = "lock:shop:" + id;
        try {
            if (tryLock(lockKey)) {
                // 获取锁成功
                // 查库
                Shop shop = shopMapper.selectById(id);
                // 模拟延迟
                Thread.sleep(100);
                if (shop == null) {
                    stringRedisTemplate.opsForHash().put(shopKey, "isNull", "true");
                    stringRedisTemplate.opsForHash().getOperations().expire(shopKey, CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                // 更新缓存
                Map<String,
                    Object> shopMapNew = BeanUtil.beanToMap(shop, new HashMap<>(),
                        CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                            (fieldName, fieldValue) -> fieldValue == null ? "" : fieldValue.toString()));
                stringRedisTemplate.opsForHash().putAll(shopKey, shopMapNew);
                stringRedisTemplate.opsForHash().getOperations().expire(shopKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            } else {
                // 已经有线程在更新缓存了
                // 等待
                Thread.sleep(50);
                // 重试
                return queryWithMutex(id);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            delLock(lockKey);
        }

    }

    /**
     * 获取锁
     * 
     * @param key
     * @return
     */
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * 
     * @param key
     */
    public void delLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
