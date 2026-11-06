<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOrders, payOrder, cancelOrder } from '../../api/catalog'

const route = useRoute()
const router = useRouter()
const orders = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const submitting = ref(false)
const now = ref(Date.now())
const timeoutNotice = ref('')
const paymentWindow = 60 * 1000
let checkingTimeout = false
let lastTimeoutCheck = 0

function remainingSeconds(order) {
  const createdAt = Date.parse(String(order.createTime).replace(' ', 'T'))
  if (!Number.isFinite(createdAt)) return null
  return Math.max(0, Math.ceil((createdAt + paymentWindow - now.value) / 1000))
}

function countdownText(order) {
  const seconds = remainingSeconds(order)
  if (seconds === null) return '--:--'
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

async function refreshExpiredOrders() {
  checkingTimeout = true
  lastTimeoutCheck = now.value
  try {
    // 到零只读取状态，不由前端取消订单；队列消费完成后再显示支付失败。
    const result = await listOrders(current.value)
    if (!submitting.value) {
      orders.value = result?.records || []
      total.value = Number(result?.total) || 0
    }
    timeoutNotice.value = ''
  } catch (cause) {
    timeoutNotice.value = '订单状态读取失败，稍后自动重试，也可手动刷新。'
  } finally {
    checkingTimeout = false
  }
}

async function handleOrder(order, action) {
  if (submitting.value || (action === 'pay' && remainingSeconds(order) === 0)) return
  if (!window.confirm(action === 'pay' ? '确认模拟支付？不会实际扣款。' : '确认取消订单？')) return
  submitting.value = true
  try {
    await (action === 'pay' ? payOrder(order.id) : cancelOrder(order.id))
    await loadOrders()
  } catch (cause) {
    error.value = cause.message || '操作失败'
  } finally {
    submitting.value = false
  }
}
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
    now.value = Date.now()
    timeoutNotice.value = ''
  } catch (cause) {
    error.value = cause.message || '优惠券订单加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => [route.query.page, route.query.submitted, route.query.created], () => {
  window.clearTimeout(refreshTimer)
  loadOrders()
  if (route.query.submitted === '1') {
    // 消息队列异步落库，仅自动刷新一次；之后由用户手动刷新。
    refreshTimer = window.setTimeout(loadOrders, 1500)
  }
}, { immediate: true })

const countdownTimer = window.setInterval(() => {
  now.value = Date.now()
  const hasExpiredOrder = orders.value.some(order => Number(order.status) === 1 && remainingSeconds(order) === 0)
  if (hasExpiredOrder && !checkingTimeout && !loading.value && !submitting.value
      && now.value - lastTimeoutCheck >= 3000) {
    refreshExpiredOrders()
  }
}, 1000)

onUnmounted(() => {
  window.clearTimeout(refreshTimer)
  window.clearInterval(countdownTimer)
})

function goToPage(page) {
  router.push({ path: '/orders', query: { page: String(page) } })
}
</script>

<template>
  <div class="page-title">
    <span class="eyebrow">VOUCHER ORDERS</span>
    <h1>我的优惠券订单</h1>
    <p>查看当前账号的普通券与秒杀券订单，支持模拟支付，未支付订单一分钟后自动取消。</p>
  </div>
  <div v-if="route.query.submitted === '1'" class="order-notice">
    秒杀请求已提交，订单可能稍后写入列表。
    <button @click="loadOrders">刷新列表</button>
  </div>
  <div v-if="route.query.created === '1'" class="order-notice">普通券订单已创建，支持模拟支付，未支付订单一分钟后自动取消。</div>
  <p v-if="timeoutNotice" class="timeout-notice" role="status">{{ timeoutNotice }}</p>
  <div v-if="error" class="state">{{ error }}</div>
  <div v-else-if="loading" class="state">正在加载优惠券订单…</div>
  <div v-else-if="!orders.length" class="state">暂无优惠券订单 <button @click="loadOrders">刷新</button></div>
  <div v-else class="order-list">
    <article v-for="order in orders" :key="order.id">
      <div>
        <span class="tag">订单 {{ order.id }}</span>
        <h3>{{ order.voucherTitle || '优惠券' }}</h3>
        <p>{{ order.shopName || '店铺' }} · {{ order.createTime }}</p>
      </div>
      <div class="order-amount">
        <strong>¥{{ order.payValue == null ? '--' : (order.payValue / 100).toFixed(2) }}</strong>
        <small>{{ ({ 1: '待支付', 2: '已支付', 3: '已核销', 4: '支付失败（已取消）', 5: '退款中', 6: '已退款' })[order.status] || '状态待确认' }}</small>
        <p v-if="Number(order.status) === 1" class="payment-countdown" role="timer" aria-label="支付剩余时间">
          <template v-if="remainingSeconds(order) !== 0">支付剩余 {{ countdownText(order) }}</template>
          <template v-else>支付时间已结束，正在确认订单状态…</template>
        </p>
        <div v-if="Number(order.status) === 1" class="order-actions">
          <button :disabled="submitting || remainingSeconds(order) === 0" @click="handleOrder(order, 'pay')">模拟支付</button>
          <button :disabled="submitting" @click="handleOrder(order, 'cancel')">取消订单</button>
        </div>
      </div>
    </article>
  </div>
  <div v-if="total > 10" class="search-pagination">
    <button :disabled="current <= 1" @click="goToPage(current - 1)">上一页</button>
    <span>第 {{ current }} / {{ pages }} 页 · 共 {{ total }} 单</span>
    <button :disabled="current >= pages" @click="goToPage(current + 1)">下一页</button>
  </div>
</template>

<style scoped>
.payment-countdown { margin: 8px 0 0; color: #a65d22; font-size: 13px; font-variant-numeric: tabular-nums; }
.timeout-notice { color: #a65d22; font-size: 13px; }
.order-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; }
.order-actions button { cursor: pointer; padding: 6px 12px; }
</style>
