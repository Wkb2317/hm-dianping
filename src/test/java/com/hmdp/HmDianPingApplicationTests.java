package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisData() {
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(10L));
        redisData.setData(new Shop().setId(1L).setName("12312312312"));
        HashMap<Object, Object> objectObjectHashMap = new HashMap<>();
        objectObjectHashMap.put("expireTime", redisData.getExpireTime().toString());
        objectObjectHashMap.put("data", JSONUtil.toJsonStr(redisData.getData()));

        stringRedisTemplate.opsForHash().putAll(RedisConstants.CACHE_SHOP_KEY + "1", objectObjectHashMap);
    }
}
