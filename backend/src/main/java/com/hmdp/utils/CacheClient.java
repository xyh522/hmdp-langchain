package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
     private final StringRedisTemplate stringRedisTemplate;
     public CacheClient(StringRedisTemplate stringRedisTemplate){
         this.stringRedisTemplate=stringRedisTemplate;
     }
     public void set(String key,Object value,Long time,TimeUnit unit){
         stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,unit);
     }
     public  void setWithLogicalExpire(String key,Object value, Long time ,TimeUnit unit){
         RedisData redisData=new RedisData();
         redisData.setData(value);
         redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
         stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
     }
    //    使用redis的setnx实现互斥锁
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    //    缓存穿透问题的
    public<R,ID> R queryWithPassThrough(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){

        //     1 查缓存
        String key=keyPrefix+id;
//        2 判断
        String Json=stringRedisTemplate.opsForValue().get(key);
//        3存在返回
        if(StrUtil.isNotBlank(Json)){
            return JSONUtil.toBean(Json,type);

        }
//        防止缓存穿透用的，查询出来是“”认为空。
        if(Json!=null){
            return null;
        }
//        4 不存在 查数据库
        R r=dbFallback.apply(id);
        //        5 不存在 返回错误 返回redis空值，避免缓存穿透
        if(r==null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;

        }
//        6 存在，写入redis
        this.set(key,r,time,unit);
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR =Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){
        //     1 查缓存
        String key=keyPrefix+id;
//        2 判断
        String json=stringRedisTemplate.opsForValue().get(key);
//        3存在返回
        if(StrUtil.isBlank(json)){
            return null;

        }
        RedisData redisData=JSONUtil.toBean(json,RedisData.class);
        R r=JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime=redisData.getExpireTime();
//        判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
//        已经过期了
        String lockkey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockkey);
//        如果过期，需要启动异步线程并且获取锁来读取数据库写回redis，此时用的还是旧数据直接return r.
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                   R r1=dbFallback.apply(id);
                   this.setWithLogicalExpire(key,r1,time,unit);
                }catch(Exception e){
                   throw new RuntimeException(e);
                }finally{
                    unLock(lockkey);
                }
            });
        }
       return r;
    }
//    基于逻辑锁解决缓存击穿问题
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.获取锁成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7.释放锁
            unLock(lockKey);
        }
        // 8.返回
        return r;
    }
}
