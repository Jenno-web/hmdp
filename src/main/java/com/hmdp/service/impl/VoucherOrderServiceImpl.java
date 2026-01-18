package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
//                获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
//                创建订单
                    handleVoucherOrder(voucherOrder);
                }catch (Exception e){
                    log.error("处理订单异常:{}",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        获取用户
        Long userId = voucherOrder.getUserId();
//        创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        获取锁
        boolean isLock = lock.tryLock();
        if(!isLock) {
//            获取锁失败
            log.error("不允许重复下单");
            return ;
        }
        try { //可能有异常，try
         o.createVoucherOrder(voucherOrder);
        }finally {
        //            释放锁
            lock.unlock();
    }
}


    IVoucherOrderService o;

    @Override
    public Result seckillVoucher(Long voucherId) {
//        执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());
//        判断结果是否为0
        int r = result.intValue();
        if(r!=0){
//        不为0，没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
//        为0，可以购买，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
//        订单id
        long orderId=redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
//        用户ID
        voucherOrder.setUserId(userId);
//        优惠卷ID
        voucherOrder.setVoucherId(voucherId);
//        放入阻塞队列
        orderTasks.add(voucherOrder);

//        获取代理对象（事务）
        o = (IVoucherOrderService) AopContext.currentProxy();

//        返回订单id
        return Result.ok(orderId);
    }




//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        根据id查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        判断是否在秒杀时间
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())
//                || voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("不在秒杀时间");
//        }
////        判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
////        充足
//
//        Long userId=UserHolder.getUser().getId();
////        创建锁对象
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
////        获取锁
//        boolean isLock = lock.tryLock();
//        if(!isLock) {
////            获取锁失败
//            return Result.fail("一个人只能买一单");
//        }
//        try { //可能有异常，try
////            获取代理对象（事务）
//            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
//            return o.createVoucherOrder(voucherId);
//        }finally {
/// /            释放锁
//            lock.unlock();
//        }
//    }

    @Transactional
        public void createVoucherOrder(VoucherOrder voucherOrder){
//        一人一单
//        查询订单
        Long userId = voucherOrder.getUserId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
//        判断是否存在
        if (count > 0) {
            log.error("用户已经买过一次");
            return ;
        }

//        扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!success) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);


    }
}
