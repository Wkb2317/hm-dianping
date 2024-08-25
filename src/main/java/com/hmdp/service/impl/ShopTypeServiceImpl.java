package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;

import cn.hutool.json.JSONUtil;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    public Result queryShopTypeList() {
        Long size = stringRedisTemplate.opsForList().size(CACHE_SHOP_TYPE_KEY);
        // 命中缓存
        if (size != 0) {
            List<String> typeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
            List<ShopType> shopTypeList =
                typeList.stream().map(item -> JSONUtil.toBean(item, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }
        // 查库
        List<ShopType> orderShopTypeList = shopTypeMapper.selectList(new QueryWrapper<>()).stream()
            .sorted(Comparator.comparing(ShopType::getSort)).collect(Collectors.toList());
        // 更新缓存
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,
            orderShopTypeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList()));
        stringRedisTemplate.opsForList().getOperations().expire(CACHE_SHOP_TYPE_KEY, CACHE_SHOP_TYPE_TTL,
            TimeUnit.MINUTES);
        return Result.ok(orderShopTypeList);
    }
}
