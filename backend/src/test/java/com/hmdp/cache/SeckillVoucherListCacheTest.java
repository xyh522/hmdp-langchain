package com.hmdp.cache;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hmdp.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillVoucherListCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private VoucherMapper mapper;
    private RedissonClient redisson;
    private RLock lock;
    private SeckillVoucherListLocalCache local;
    private SeckillVoucherServiceImpl service;
    private static final String INDEX_KEY = SECKILL_VOUCHER_LIST_KEY + "1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        mapper = mock(VoucherMapper.class);
        redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        local = new SeckillVoucherListLocalCache();
        ReflectionTestUtils.setField(local, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(local, "redissonClient", redisson);
        service = new SeckillVoucherServiceImpl();
        when(redis.opsForValue()).thenReturn(values);
        when(redisson.getLock(LOCK_SECKILL_VOUCHER_LIST_KEY + "1")).thenReturn(lock);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        ReflectionTestUtils.setField(service, "voucherMapper", mapper);
        ReflectionTestUtils.setField(service, "listLocalCache", local);
    }

    @Test
    void databaseMissWritesFullDetailsWithIndependentTtlsAndIndex() {
        when(mapper.querySeckillVouchersOfShop(1L)).thenReturn(Arrays.asList(voucher(19L, 1), voucher(20L, 3)));
        List<SeckillVoucherDTO> result = service.querySeckillVouchersOfShop(1L);
        assertEquals(2, result.size());
        ArgumentCaptor<String> firstJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> firstTtl = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> secondTtl = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> indexTtl = ArgumentCaptor.forClass(Long.class);
        verify(values).set(eq(SECKILL_VOUCHER_KEY + "19"), firstJson.capture(), firstTtl.capture(), eq(TimeUnit.MILLISECONDS));
        verify(values).set(eq(SECKILL_VOUCHER_KEY + "20"), anyString(), secondTtl.capture(), eq(TimeUnit.MILLISECONDS));
        verify(values).set(eq(INDEX_KEY), eq("[19,20]"), indexTtl.capture(), eq(TimeUnit.MILLISECONDS));
        SeckillVoucherDTO detail = JSONUtil.toBean(firstJson.getValue(), SeckillVoucherDTO.class);
        assertEquals("Description 19", detail.getSubTitle());
        assertEquals("Rules 19", detail.getRules());
        assertEquals(Long.valueOf(9900L), detail.getPayValue());
        assertEquals(Long.valueOf(1L), detail.getShopId());
        assertNotNull(detail.getCreateTime());
        assertFalse(JSONUtil.parseObj(firstJson.getValue()).containsKey("stock"));
        assertTrue(firstTtl.getValue() > 0);
        assertTrue(secondTtl.getValue() > firstTtl.getValue() + TimeUnit.HOURS.toMillis(1));
        assertTrue(Math.abs(indexTtl.getValue() - secondTtl.getValue()) < 5000);
        result.get(0).setTitle("caller mutation");
        clearInvocations(redis, values, mapper, redisson);
        assertEquals("Voucher 19", service.querySeckillVouchersOfShop(1L).get(0).getTitle());
        verifyNoInteractions(redis, values, mapper, redisson);
    }

    @Test
    void redisIndexAndDetailsPopulateCaffeineWithoutQueryingDatabase() {
        when(values.get(INDEX_KEY)).thenReturn("[19,20]");
        when(values.multiGet(Arrays.asList(SECKILL_VOUCHER_KEY + "19", SECKILL_VOUCHER_KEY + "20")))
                .thenReturn(Arrays.asList(JSONUtil.toJsonStr(dto(19L, 1)), JSONUtil.toJsonStr(dto(20L, 3))));
        List<SeckillVoucherDTO> result = service.querySeckillVouchersOfShop(1L);
        assertEquals(2, result.size());
        assertEquals("Rules 20", result.get(1).getRules());
        verifyNoInteractions(mapper);
        verify(values, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        clearInvocations(redis, values, redisson);
        service.querySeckillVouchersOfShop(1L);
        verifyNoInteractions(redis, values, redisson);
    }

    @Test
    void missingOrLegacyDetailRebuildsTheWholeListOnce() {
        when(values.get(INDEX_KEY)).thenReturn("[19,20]");
        when(values.multiGet(anyCollection())).thenReturn(Arrays.asList("{\"voucherId\":19}", null));
        when(mapper.querySeckillVouchersOfShop(1L)).thenReturn(Collections.singletonList(voucher(20L, 3)));
        List<SeckillVoucherDTO> result = service.querySeckillVouchersOfShop(1L);
        assertEquals(Collections.singletonList(20L), Collections.singletonList(result.get(0).getId()));
        verify(mapper, times(1)).querySeckillVouchersOfShop(1L);
        verify(values).set(eq(INDEX_KEY), eq("[20]"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void redisEmptyIndexIsAHitAndDoesNotQueryTheDatabase() {
        when(values.get(INDEX_KEY)).thenReturn("[]");
        assertTrue(service.querySeckillVouchersOfShop(1L).isEmpty());
        assertTrue(service.querySeckillVouchersOfShop(1L).isEmpty());
        verify(values, times(1)).get(INDEX_KEY);
        verifyNoInteractions(mapper);
        verify(values, never()).multiGet(anyCollection());
    }

    @Test
    void databaseEmptyListIsCachedAsAnEmptyIndex() {
        when(mapper.querySeckillVouchersOfShop(1L)).thenReturn(Collections.emptyList());
        assertTrue(service.querySeckillVouchersOfShop(1L).isEmpty());
        verify(values).set(INDEX_KEY, "[]", 60_000L, TimeUnit.MILLISECONDS);
        service.querySeckillVouchersOfShop(1L);
        verify(mapper, times(1)).querySeckillVouchersOfShop(1L);
    }

    @Test
    void concurrentRequestsInOneJvmOnlyLoadTheShopOnce() throws Exception {
        when(mapper.querySeckillVouchersOfShop(1L)).thenAnswer(invocation -> {
            Thread.sleep(50);
            return Collections.singletonList(voucher(19L, 1));
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<SeckillVoucherDTO>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> { start.await(); return service.querySeckillVouchersOfShop(1L); }));
            }
            start.countDown();
            for (Future<List<SeckillVoucherDTO>> future : futures) assertEquals(1, future.get(5, TimeUnit.SECONDS).size());
            verify(mapper, times(1)).querySeckillVouchersOfShop(1L);
            verify(values, times(1)).get(INDEX_KEY);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void writeInvalidationRunsAfterCommitButNotRollback() {
        AtomicInteger loads = new AtomicInteger();
        java.util.function.Function<Long, List<SeckillVoucherDTO>> loader = id -> { loads.incrementAndGet(); return Collections.emptyList(); };
        local.get(1L, loader);
        TransactionTemplate transactions = new TransactionTemplate(new TestTransactions());
        transactions.execute(status -> {
            local.invalidateAfterCommit(1L, 19L);
            local.get(1L, loader);
            assertEquals(1, loads.get());
            verify(redis, never()).delete(anyCollection());
            return null;
        });
        verify(redis).delete(Arrays.asList(INDEX_KEY, SECKILL_VOUCHER_KEY + "19"));
        local.get(1L, loader);
        assertEquals(2, loads.get());
        clearInvocations(redis, redisson, lock);
        transactions.execute(status -> { local.invalidateAfterCommit(1L, 19L); status.setRollbackOnly(); return null; });
        local.get(1L, loader);
        assertEquals(2, loads.get());
        verifyNoInteractions(redis, redisson, lock);
    }

    private Voucher voucher(Long id, int hours) {
        LocalDateTime now = LocalDateTime.now();
        return new Voucher().setId(id).setShopId(1L).setTitle("Voucher " + id)
                .setSubTitle("Description " + id).setRules("Rules " + id)
                .setPayValue(9900L).setActualValue(12000L).setType(1).setStatus(1)
                .setBeginTime(now.minusHours(1)).setEndTime(now.plusHours(hours))
                .setCreateTime(now.minusDays(1)).setUpdateTime(now);
    }

    private SeckillVoucherDTO dto(Long id, int hours) {
        SeckillVoucherDTO result = new SeckillVoucherDTO();
        org.springframework.beans.BeanUtils.copyProperties(voucher(id, hours), result);
        result.setVoucherId(id);
        return result;
    }

    private static class TestTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}