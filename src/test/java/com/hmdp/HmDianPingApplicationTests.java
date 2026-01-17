package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;
    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);

        Runnable task=()->{
            for(int x=0;x<100;x++){
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int x=0;x<300;x++){
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time="+(end-begin));
    }

    @Test
    void lock(){
        SimpleRedisLock simpleRedisLock=new SimpleRedisLock("u1",stringRedisTemplate);
        simpleRedisLock.tryLock(100);
    }

    @Test
    void testSaveShop(){

    }
}
