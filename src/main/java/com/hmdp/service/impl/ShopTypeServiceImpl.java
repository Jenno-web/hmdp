package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        List<String> shopType = stringRedisTemplate.opsForList().range(CACHE_SHOP_KEY+"type", 0, -1);
        if(!shopType.isEmpty()){
            List<ShopType> types = shopType.stream()
                    .map(s -> JSONUtil.toBean(s, ShopType.class))
                    .collect(Collectors.toList());

            return Result.ok(types);
        }
        List<ShopType> sort = query().orderByAsc("sort").list();
        if(sort.isEmpty()){
            return Result.fail("分类不存在");
        }
        for (ShopType type : sort) {
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_KEY+"type", JSONUtil.toJsonStr(type));
        }
        return Result.ok(sort);
    }
}
