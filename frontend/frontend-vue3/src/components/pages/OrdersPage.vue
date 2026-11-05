<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOrders } from '../../api/catalog'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const current = computed(() => Math.max(1, Number(route.query.page) || 1))
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
let refreshTimer

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const result = await listOrders(current.value)
    orders.value = result?.records || []
    total.value = Number(result?.total) || 0
  } catch (cause) {
    error.value = cause.message || '秒杀券订单加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => [route.query.page, route.query.submitted], () => {
  window.clearTimeout(refreshTimer)
  loadOrders()
  if (route.query.submitted === '1') {
    // 消息队列异步落库，仅自动刷新一次；之后由用户手动刷新。
    refreshTimer = window.setTimeout(loadOrders, 1500)
  }
}, { immediate: true })

onUnmounted(() => window.clearTimeout(refreshTimer))

function goToPage(page) {
  router.push({ path: '/orders', query: { page: String(page) } })
}
</script>

<template>
  <div class="page-title">
    <span class="eyebrow">SECKILL ORDERS</span>
    <h1>我的秒杀券订单</h1>
    <p>仅展示当前登录账号抢购的秒杀券，不包含支付流程。</p>
  </div>
  <div v-if="route.query.submitted === '1'" class="order-notice">
    秒杀请求已提交，订单可能稍后写入列表。
    <button @click="loadOrders">刷新列表</button>
  </div>
  <div v-if="error" class="state">{{ error }}</div>
  <div v-else-if="loading" class="state">正在加载秒杀券订单…</div>
  <div v-else-if="!orders.length" class="state">暂无秒杀券订单 <button @click="loadOrders">刷新</button></div>
  <div v-else class="order-list">
    <article v-for="order in orders" :key="order.id">
      <div>
        <span class="tag">秒杀订单 {{ order.id }}</span>
        <h3>{{ order.voucherTitle || '秒杀优惠券' }}</h3>
        <p>{{ order.shopName || '店铺' }} · {{ order.createTime }}</p>
      </div>
      <div class="order-amount">
        <strong>¥{{ order.payValue == null ? '--' : (order.payValue / 100).toFixed(2) }}</strong>
        <small>已抢购</small>
      </div>
    </article>
  </div>
  <div v-if="total > 10" class="search-pagination">
    <button :disabled="current <= 1" @click="goToPage(current - 1)">上一页</button>
    <span>第 {{ current }} / {{ pages }} 页 · 共 {{ total }} 单</span>
    <button :disabled="current >= pages" @click="goToPage(current + 1)">下一页</button>
  </div>
</template>
