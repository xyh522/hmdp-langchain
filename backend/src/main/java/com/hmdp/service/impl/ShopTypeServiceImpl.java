package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl
        extends ServiceImpl<ShopTypeMapper, ShopType>
        implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.查询 Redis List 缓存
        List<String> typeJsonList = stringRedisTemplate.opsForList()
                .range(CACHE_SHOP_TYPE_KEY, 0, -1);

        // 2.缓存存在：反序列化并返回
        if (typeJsonList != null && !typeJsonList.isEmpty()) {
            List<ShopType> typeList = typeJsonList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());

            return Result.ok(typeList);
        }

        // 3.缓存不存在：查询数据库
        List<ShopType> typeList = query()
                .orderByAsc("sort")
                .list();

        // 4.数据库中也没有数据
        if (typeList == null || typeList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 5.序列化后写入 Redis List
        List<String> jsonList = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList()
                .rightPushAll(CACHE_SHOP_TYPE_KEY, jsonList);

        return Result.ok(typeList);
    }
}