//package com.hmdp.service.impl;
//
//import cn.hutool.core.bean.BeanUtil;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.hmdp.dto.Result;
//import com.hmdp.entity.VoucherOrder;
//import com.hmdp.mapper.VoucherOrderMapper;
//import com.hmdp.service.ISeckillVoucherService;
//import com.hmdp.service.IVoucherOrderService;
//import com.hmdp.utils.RedisIdWorker;
//import com.hmdp.utils.UserHolder;
//import lombok.extern.slf4j.Slf4j;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
//import org.springframework.aop.framework.AopContext;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.data.redis.connection.stream.*;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.PreDestroy;
//import javax.annotation.Resource;
//import java.time.Duration;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.*;
//
///**
// * <p>
// * 服务实现类
// * </p>
// *
// * @author 虎哥
// * @since 2021-12-22
// */
//@Slf4j
//@Service
//public class befo_VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
//
//    @Resource
//    private ISeckillVoucherService seckillVoucherService;
//
//    @Resource
//    private RedisIdWorker redisIdWorker;
//
//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Resource
//    private RedissonClient redissonClient;
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//    //    初始化lua脚本对象和设置初始值
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
////    private BlockingQueue<VoucherOrder> orderTasks= new ArrayBlockingQueue<>(1024*1024);
//    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//
//    private volatile boolean running = true;
//
//    private IVoucherOrderService proxy;
//
//    @PostConstruct  //这个注解让这个类一提交就去做这个函数，这个函数开启线程死循环读取阻塞队列。
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//    @PreDestroy
//    public void destroy() {
//        running = false;
//        SECKILL_ORDER_EXECUTOR.shutdownNow();
//        try {
//            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
//                log.warn("订单消费线程未在规定时间内退出");
//            }
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private boolean shouldRun() {
//        return running && !Thread.currentThread().isInterrupted();
//    }
//
//    private class VoucherOrderHandler implements  Runnable {
//        String queueName = "stream.orders";
//
//        @Override
//        public void run() {
//
//            while (shouldRun()) {
//                try {
////                  //获取redis队列信息
//                    // XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
//                    );
//                    if (list == null || list.isEmpty()) {
//                        continue;
//                    }
//                    //解析消息中的订单信息
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    //获取成功，可以下单
//                    handleVoucherOrder(voucherOrder);
//                    //sack确认 SACK stream.orders g1 id
//                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
//
//                } catch (Exception e) {
//                    //出现异常，消息会放到pedding list中
//                    if (!shouldRun()) {
//                        return;
//                    }
//                    log.error("处理订单异常", e);
//                    handlePendingList();
//                }
//
//            }
//        }
//
//        private void handlePendingList() {
//            while (shouldRun()) {
//                try {
////                  //获取redis队列信息
//                    // XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.order 0
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.from("0"))
//                    );
//                    if (list == null || list.isEmpty()) {
//                        break;
//                    }
//                    //解析消息中的订单信息
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    //获取成功，可以下单
//                    handleVoucherOrder(voucherOrder);
//                    //sack确认 SACK stream.orders g1 id
//                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
//
//                } catch (Exception e) {
//                    //出现异常
//                    if (!shouldRun()) {
//                        return;
//                    }
//                    log.error("处理 pending list 异常", e);
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException interrupted) {
//                        Thread.currentThread().interrupt();
//                        return;
//                    }
//
//                }
//
//            }
//        }
//    }
//
//
//        //    private class VoucherOrderHandler implements  Runnable{
////        @Override
////        public void run(){
////            while(true){
////                try {
//////                    获取出信息
////                    VoucherOrder voucherOrder = orderTasks.take();
//////                    创建订单  新线程里的东西从这个类里取娶不到的
////                    handleVoucherOrder(voucherOrder);
////
////                }catch (Exception e){
////                    log.error("处理订单异常",e);
////                }
////
////            }
////        }
////    }
//        private void handleVoucherOrder(VoucherOrder voucherOrder) {
//            Long userId = voucherOrder.getUserId();
//            RLock lock = redissonClient.getLock("lock:order:" + userId);
//            boolean isLock = lock.tryLock();
//            if (!isLock) {
//                log.error("不允许重复下单");
//                return;
//            }
////        防止意外
//            try {
//                proxy.createVoucherOrder(voucherOrder);
//
//            } finally {
//                lock.unlock();
//
//            }
//        }
//
//        //redis 实现阻塞队列
//        @Override
//        public Result seckillVoucher(Long voucherId) {
////        获取用户信息
//            Long userId = UserHolder.getUser().getId();
//            long orderId = redisIdWorker.nextId("order");
////        1执行lua脚本
//            Long result = stringRedisTemplate.execute(
//                    SECKILL_SCRIPT,
//                    Collections.emptyList(),
//                    voucherId.toString(), userId.toString(), String.valueOf(orderId)
//            );
//
////        2 判断结果是否为0
//            int r = result.intValue();
//            //        不为0 没有资格
//
//            if (r != 0) {
//                return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//            }
//            proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return Result.ok(orderId);
//
//        }
////    阻塞队列写法
////    @Override
////    public Result seckillVoucher(Long voucherId) {
//////        获取用户信息
////        Long userId=UserHolder.getUser().getId();
//////        1执行lua脚本
////        Long result=stringRedisTemplate.execute(
////                SECKILL_SCRIPT,
////                Collections.emptyList(),
////                voucherId.toString(),userId.toString()
////        );
////
//////        2 判断结果是否为0
////        int r = result.intValue();
////        //        不为0 没有资格
////
////        if(r!=0){
////            return Result.fail(r==1?"库存不足":"不能重复下单");
////        }
////        long orderId=redisIdWorker.nextId("order");
////
//////        为0 需要保存到阻塞队列
////        VoucherOrder voucherOrder = new VoucherOrder();
////        voucherOrder.setId(orderId);
////        // 7.2.用户id
////        voucherOrder.setUserId(userId);
////        // 7.3.代金券id
////        voucherOrder.setVoucherId(voucherId);
////
////
////
////        orderTasks.add(voucherOrder);
////
////        proxy=(IVoucherOrderService) AopContext.currentProxy();
////
////        return Result.ok(orderId);
////
////    }
//
//        //    synchronized为互斥锁，在一个jvm里只允许一个线程访问这个函数，但是防不住分布式场景
//        @Transactional
//        public void createVoucherOrder(VoucherOrder voucherOrder) {
//            Long userId = voucherOrder.getUserId();
////        5 一人一单
//            int count = query().eq("user_id", userId)
//                    .eq("voucher_id", voucherOrder.getVoucherId()).count();
//            if (count > 0) {
//                return;
//            }
//
//            // 6.扣减库存  乐观锁，判断此时的stock是否大于0，大于的话可以继续。性能好但是成功率低
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1") // set stock = stock - 1
//                    .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
//                    .update();
//            if (!success) {
//                // 扣减失败
//                return;
//            }
//
//
//            save(voucherOrder);
//
//
//        }
//
//
////    @Override
////    public Result seckillVoucher(Long voucherId) {
////        // 1.查询优惠券
////        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        // 2.判断秒杀是否开始
////        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
////            // 尚未开始
////            return Result.fail("秒杀尚未开始！");
////        }
////        // 3.判断秒杀是否已经结束
////        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
////            // 尚未开始
////            return Result.fail("秒杀已经结束！");
////        }
////        // 4.判断库存是否充足
////        if (voucher.getStock() < 1) {
////            // 库存不足
////            return Result.fail("库存不足！");
////        }
////        Long userId = UserHolder.getUser().getId();
////
//////        创建锁对象
//////        SimpleRedisLock lock=new SimpleRedisLock("order:"+userId,stringRedisTemplate);
////////        获取锁
//////
//////        boolean isLock=lock.tryLock(1200);
////////        锁是否创建成功
////        RLock lock=redissonClient.getLock("lock:order"+userId);
////        boolean isLock=lock.tryLock();
////
////
////        if(!isLock){
////            return Result.fail("不允许重复下单");
////        }
////
//////        防止意外
////        try {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }finally {
////            lock.unlock();
////
////        }
////
//////            单节点的操作
//////        这里需要是拿到transactional对象，但是这个注解是基于代理的，所以this不行，要使用aop拿到ioc容器中真正的代理对象才行
//////        synchronized (userId.toString().intern()) {
//////            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
//////
//////            // 7.返回订单id
//////            return proxy.createVoucherOrder(voucherId);
//////        }
////
////
////    }
//////    synchronized为互斥锁，在一个jvm里只允许一个线程访问这个函数，但是防不住分布式场景
////    @Transactional
////    public Result createVoucherOrder(Long voucherId){
////        Long userId = UserHolder.getUser().getId();
//////        5 一人一单
////        int count=query().eq("user_id",userId).eq("voucher_id",voucherId).count();
////        if(count>0){
////            return Result.fail("用户已经购买过了");
////        }
////
////        // 6.扣减库存  乐观锁，判断此时的stock是否大于0，大于的话可以继续。性能好但是成功率低
////        boolean success = seckillVoucherService.update()
////                .setSql("stock = stock - 1") // set stock = stock - 1
////                .eq("voucher_id", voucherId).gt("stock", 0)
////                .update();
////        if (!success) {
////            // 扣减失败
////            return Result.fail("库存不足！");
////        }
////
////        // 7.创建订单
////        VoucherOrder voucherOrder = new VoucherOrder();
////        // 7.1.订单id
////        long orderId = redisIdWorker.nextId("order");
////        voucherOrder.setId(orderId);
////        // 7.2.用户id
////        voucherOrder.setUserId(userId);
////        // 7.3.代金券id
////        voucherOrder.setVoucherId(voucherId);
////        save(voucherOrder);
////
////        // 7.返回订单id
////        return Result.ok(orderId);
////    }
//
//
//}
