package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
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
    private CacheClient cacheClient;

//    使用redis的setnx实现互斥锁
//    private boolean tryLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.MINUTES);
//        return BooleanUtil.isTrue(flag);
//    }
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }
////    缓存穿透问题的  已封装到cacheclient
//    public Shop queryWithPassThrough(Long id){
//
//        //     1 查缓存
//        String key=CACHE_SHOP_KEY+id;
////        2 判断
//        String shopJson=stringRedisTemplate.opsForValue().get(key);
////        3存在返回
//        if(StrUtil.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson,Shop.class);
//
//        }
////        防止缓存穿透用的，查询出来是“”认为空。
//        if(shopJson!=null){
//            return null;
//        }
////        4 不存在 查数据库
//        Shop shop =getById(id);
//        //        5 不存在 返回错误 返回redis空值，避免缓存穿透
//        if(shop==null) {
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//
//        }
////        6 存在，写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
//        return shop;
//    }
//    互斥锁实现缓存击穿
//    private Shop queryWithMutex(Long id){
//        //     1 查缓存
//        String key=CACHE_SHOP_KEY+id;
////        2 判断
//        String shopJson=stringRedisTemplate.opsForValue().get(key);
////        3存在返回
//        if(StrUtil.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson,Shop.class);
//
//        }
////        防止缓存穿透用的，查询出来是“”认为空。
//        if(shopJson!=null){
//            return null;
//        }
////        4 不存在 查数据库  加锁实现
////        4.1 获取互斥锁
//        String lockkey="lock:shop"+id;
//        try {
//            boolean isLock=tryLock(lockkey);
//            //        判断是否成功
//            //        失败后休眠
//            if(!isLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
////        成功写回和释放
//            Shop shop=getById(id);
//            if(shop==null){
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
////        6 存在，写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
//            unLock(lockkey);
//            return shop;
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally{
//            unLock(lockkey);
//        }
//
//    }
    @Override
    public Result queryById(Long id) {
//        缓存穿透
          Shop shop=cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

//        缓存击穿  此时是基于逻辑过期时间的，热点key需要提前注入.
//        Shop shop=cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

// 互斥锁解决缓存击穿
        // Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop==null){
            return Result.fail("店铺不存在，查询失败");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(
            Integer typeId,
            Integer current,
            Double x,
            Double y
    ) {
        // 1.x或y为空：不进行附近查询，直接查询数据库
        if(x ==null||y==null){
            Page<Shop> page =query().eq("type_id",typeId)
                    .page(new Page<>(current,SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2.计算分页范围
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end =current*SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.拼接当前店铺类型对应的GEO键
        String key= SHOP_GEO_KEY+typeId;
        // 4.判断Redis中是否存在该类型的GEO索引
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            // 4.1 查询数据库中该类型的全部店铺
            List<Shop> allShops = query().eq("type_id", typeId).list();

            // 4.2 数据库也没有数据，直接返回空集合，避免后续无意义操作
            if (allShops == null || allShops.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 4.3 将店铺转换为Redis GEO结构，同时过滤掉坐标为空的无效数据
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(allShops.size());
            for (Shop shop : allShops) {
                if (shop.getX() == null || shop.getY() == null) {
                    continue;
                }
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }

            // 4.4 如果所有店铺都无有效坐标，退化为普通分页查询，避免空写入
            if (locations.isEmpty()) {
                Page<Shop> page = query().eq("type_id", typeId)
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
                return Result.ok(page.getRecords());
            }

            // 4.5 批量写入Redis GEO，完成初始化
            stringRedisTemplate.opsForGeo().add(key, locations);
        }



        // 5.查询5公里以内的店铺
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x,y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 6.没有附近店铺
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list =results.getContent();
        if(list.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids =new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());

        list.stream().skip(from).forEach(result->{
            String shopIdStr=result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance =result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });

        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list();
        for(Shop shop:shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);



        // 7.当前页没有数据


        // 8.提取当前页的店铺ID和距离


        // 9.根据ID从数据库查询店铺详情，并保持Redis距离顺序


        // 10.设置每个店铺距离当前位置的距离



    }

}
