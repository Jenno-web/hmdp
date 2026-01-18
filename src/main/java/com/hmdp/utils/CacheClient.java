package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassTrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time, TimeUnit unit) {
//        从redis查询商铺缓存
        String Json= stringRedisTemplate.opsForValue().get(keyPrefix+id);
//        判断是否存在
        if(StrUtil.isNotBlank(Json)){
//        存在，直接返回
            R r=JSONUtil.toBean(Json,type);
            return r;
        }
//        判断redis是否为空值
        if(Json!=null){
//            返回错误信息
            return null;
        }
//        不存在，根据id查询数据库
        R r=dbFallBack.apply(id);
//        数据库中不存在
        if (r == null) {
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(keyPrefix+id,"",time,unit);
//            返回错误
            return null;
        }
//        数据库中存在，写入redis
        this.set(keyPrefix+id,r,time,unit);
//        返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);


    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit) {
//        从redis查询商铺缓存
        String Json= stringRedisTemplate.opsForValue().get(keyPrefix+id);
//        判断是否存在
        if(StrUtil.isNotBlank(Json)){
//        存在
//          判断是否过期
            RedisData redisData=JSONUtil.toBean(Json,RedisData.class);
            JSONObject data = (JSONObject) redisData.getData();
            R r = JSONUtil.toBean(data, type);
            LocalDateTime expireTime=redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
//            未过期，直接返回店铺信息
                return r;
            }
//            已过期，需要缓存重建
//            获取互斥锁
            String lockKey=LOCK_SHOP_KEY+id;
            boolean isLock=tryLock(lockKey);
//            判断是否获取锁成功
            if(isLock){
//                成功，开独立线程重建
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
//                    重建缓存
//                        查数据库
                        R rl=dbFallback.apply(id);

//                        存redis
                        this.setWithLogicalExpire(keyPrefix+id,rl,time,unit);
                    } catch (Exception e){
                        throw new RuntimeException();
                    } finally {
//                        释放锁
                        unlock(lockKey);
                    }
                });
            }
//            返回过期的商铺信息
            return r;
        }
//        判断redis是否为空值
        if(Json!=null){
//            返回错误信息
            return null;
        }
//        不存在，根据id查询数据库
        R r=dbFallback.apply(id);
//        数据库中不存在
        if (r == null) {
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(keyPrefix+id,"",time,unit);
//            返回错误
            return null;
        }

//        数据库中存在，写入redis
        this.setWithLogicalExpire(keyPrefix+id,r,time,unit);
//        返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
