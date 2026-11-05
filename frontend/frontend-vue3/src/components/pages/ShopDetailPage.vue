<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { buyVoucher, getShop, listVouchers } from '../../api/catalog'
import { firstImage } from '../../utils/images'

const route = useRoute()
const router = useRouter()
const shop = ref(null)
const vouchers = ref([])
const loading = ref(true)
const error = ref('')
const now = ref(Date.now())
let timer

function asTime(value) {
  if (!value) return NaN
  return new Date(String(value).replace(' ', 'T')).getTime()
}

const visibleVouchers = computed(() => vouchers.value.filter(voucher => {
  if (voucher.type !== 1) return true
  const end = asTime(voucher.endTime)
  return Number.isFinite(end) && end > now.value
}))

function remaining(milliseconds) {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return [days ? `${days}天` : '', `${hours}时`, `${minutes}分`, `${rest}秒`].filter(Boolean).join('')
}

function countdown(voucher) {
  const start = asTime(voucher.beginTime)
  if (Number.isFinite(start) && start > now.value) return `距开始 ${remaining(start - now.value)}`
  return `剩余 ${remaining(asTime(voucher.endTime) - now.value)}`
}

function isUpcoming(voucher) {
  return Number.isFinite(asTime(voucher.beginTime)) && asTime(voucher.beginTime) > now.value
}

watch(() => route.params.id, async id => {
  loading.value = true
  error.value = ''
  try {
    const [shopData, voucherData] = await Promise.all([getShop(id), listVouchers(id)])
    shop.value = shopData
    vouchers.value = voucherData || []
    now.value = Date.now()
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}, { immediate: true })

async function buy(voucher) {
  if (isUpcoming(voucher) || asTime(voucher.endTime) <= Date.now()) return
  try {
    await buyVoucher(voucher.id)
    await router.push({ path: '/orders', query: { submitted: '1' } })
  } catch (e) { alert(e.message) }
}

onMounted(() => { timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div v-if="loading" class="state">正在加载…</div>
  <div v-else-if="error" class="state">{{ error }}</div>
  <div v-else-if="shop" class="detail-layout">
    <section class="detail-main">
      <img class="cover" :src="firstImage(shop.images)">
      <span class="eyebrow">SHOP DETAIL</span>
      <h1>{{ shop.name }}</h1>
      <p class="stars">★★★★★ <small>{{ shop.score }} 分</small></p>
      <p>{{ shop.address }}</p>
      <div class="panel">
        <h2>商户介绍</h2>
        <p>{{ shop.comments || '这里值得你亲自来看看。' }}</p>
        <p>营业时间：{{ shop.openHours || '以商户实际营业时间为准' }}</p>
      </div>
    </section>
    <aside>
      <div class="side-card">
        <span class="eyebrow">OFFERS</span>
        <h2>店铺优惠</h2>
        <div v-for="voucher in visibleVouchers" :key="voucher.id" class="voucher">
          <div>
            <strong>¥{{ (voucher.payValue / 100).toFixed(2) }}</strong>
            <p>{{ voucher.title }}</p>
            <small>抵扣 ¥{{ (voucher.actualValue / 100).toFixed(2) }}</small>
            <small v-if="voucher.type === 1" class="voucher-countdown">{{ countdown(voucher) }}</small>
          </div>
          <button v-if="voucher.type === 1" :disabled="isUpcoming(voucher)" @click="buy(voucher)">{{ isUpcoming(voucher) ? '未开始' : '立即抢购' }}</button>
          <span v-else class="voucher-ordinary-label">普通优惠券</span>
        </div>
        <p v-if="!visibleVouchers.length" class="muted">暂无可用优惠</p>
      </div>
    </aside>
  </div>
</template>
