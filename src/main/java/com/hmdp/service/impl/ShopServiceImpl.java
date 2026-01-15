package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Override
    public Result queryById(Long id) {
//        缓存穿透
//        Shop shop=cacheClient.queryWithPassTrough(CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);

//        互斥锁解决缓存击穿
//        Shop shop=queryWithMutex(id);

//        逻辑过期解决缓存击穿
        Shop shop=cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),20L,TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
//        返回
        return Result.ok(shop);
    }


/*
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
//        从redis查询商铺缓存
        String shopJson= stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
//        存在
//          判断是否过期
            RedisData redisData=JSONUtil.toBean(shopJson,RedisData.class);
            JSONObject data = (JSONObject) redisData.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);
            LocalDateTime expireTime=redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
//            未过期，直接返回店铺信息
                return shop;
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
                    this.saveShop2Redis(id, 20L);
                    } catch (Exception e){
                        throw new RuntimeException();
                    } finally {
//                        释放锁
                        unlock(lockKey);
                    }
                });
            }
//            返回过期的商铺信息
            return shop;
        }
//        判断redis是否为空值
        if(shopJson!=null){
//            返回错误信息
            return null;
        }
//        不存在，根据id查询数据库
        Shop shop=getById(id);
//        数据库中不存在
        if (shop == null) {
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            返回错误
            return null;
        }
//        数据库中存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        返回
        return shop;
    }
*/

    public Shop queryWithMutex(Long id) {
//        从redis查询商铺缓存
        String shopJson= stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
//        存在，直接返回
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
//        判断redis是否为空值
        if(shopJson!=null){
//            返回错误信息
            return null;
        }
//        不存在
//        获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop=null;
        try {
        boolean isLock=tryLock(lockKey);
//        判断是否获取成功
        if (isLock){
//        失败，休眠并重试
            Thread.sleep(50);
            return queryWithMutex(id);
            }

//       成功，根据id查询数据库
         shop=getById(id);
//        模拟重建延时
        Thread.sleep(200);
//        数据库中不存在
        if (shop == null) {
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            返回错误
            return null;
        }
//        数据库中存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally{
//        释放互斥锁
            unlock(lockKey);
        }
//        返回
        return shop;
    }

/*
    public Shop queryWithPassTrough(Long id) {
//        从redis查询商铺缓存
        String shopJson= stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
//        存在，直接返回
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
//        判断redis是否为空值
        if(shopJson!=null){
//            返回错误信息
            return null;
        }
//        不存在，根据id查询数据库
        Shop shop=getById(id);
//        数据库中不存在
        if (shop == null) {
//            将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            返回错误
            return null;
        }
//        数据库中存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        返回
        return shop;
    }
*/
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

/*
    public void saveShop2Redis(Long id,Long expireSeconds){
//        查询店铺数据
        Shop shop=getById(id);
//        封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
*/
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
//        更新数据库
        updateById(shop);
//        删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
