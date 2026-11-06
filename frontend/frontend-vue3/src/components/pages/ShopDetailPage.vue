<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { buyVoucher, buyOrdinaryVoucher, getShop, listVouchers, listSeckillVouchers } from '../../api/catalog'
import { firstImage } from '../../utils/images'
import VoucherCard from '../VoucherCard.vue'

const route = useRoute()
const router = useRouter()
const shop = ref(null)
const ordinaryVouchers = ref([])
const seckillVouchers = ref([])
const loading = ref(true)
const error = ref('')
const ordinaryError = ref('')
const seckillError = ref('')
const purchaseError = ref('')
const ordinaryPurchaseError = ref('')
const pendingVoucherId = ref(null)
const now = ref(Date.now())
let timer
let requestVersion = 0

function requireList(value) {
  if (!Array.isArray(value)) throw new Error('优惠券列表返回格式异常')
  return value
}

async function loadShop(id) {
  const version = ++requestVersion
  loading.value = true
  shop.value = null
  error.value = ''
  ordinaryError.value = ''
  seckillError.value = ''
  purchaseError.value = ''
  ordinaryPurchaseError.value = ''
  ordinaryVouchers.value = []
  seckillVouchers.value = []
  const results = await Promise.allSettled([
    getShop(id),
    listVouchers(id).then(requireList),
    listSeckillVouchers(id).then(requireList)
  ])
  if (version !== requestVersion) return
  const [shopResult, ordinaryResult, seckillResult] = results
  if (shopResult.status === 'fulfilled' && shopResult.value) shop.value = shopResult.value
  else error.value = shopResult.reason?.message || '店铺不存在或加载失败'
  if (ordinaryResult.status === 'fulfilled') {
    ordinaryVouchers.value = ordinaryResult.value.filter(voucher => Number(voucher.type) === 0)
  } else ordinaryError.value = ordinaryResult.reason?.message || '普通券加载失败'
  if (seckillResult.status === 'fulfilled') {
    seckillVouchers.value = seckillResult.value
  } else seckillError.value = seckillResult.reason?.message || '秒杀券加载失败'
  now.value = Date.now()
  loading.value = false
}

watch(() => route.params.id, id => loadShop(id), { immediate: true })

async function buy(voucher) {
  const start = new Date(String(voucher.beginTime || '').replace(' ', 'T')).getTime()
  const end = new Date(String(voucher.endTime || '').replace(' ', 'T')).getTime()
  const currentTime = Date.now()
  if (!Number.isFinite(start) || !Number.isFinite(end) || currentTime < start || currentTime >= end) return
  await submitOrder(voucher, false)
}

async function buyOrdinary(voucher) {
  await submitOrder(voucher, true)
}

async function submitOrder(voucher, ordinary) {
  if (pendingVoucherId.value !== null) return
  const targetError = ordinary ? ordinaryPurchaseError : purchaseError
  const voucherId = voucher.id ?? voucher.voucherId
  if (voucherId == null) {
    targetError.value = '优惠券信息异常，请刷新后重试'
    return
  }
  const shopId = route.params.id
  targetError.value = ''
  pendingVoucherId.value = voucherId
  try {
    const orderId = await (ordinary ? buyOrdinaryVoucher(voucherId) : buyVoucher(voucherId))
    if (route.params.id === shopId) {
      const query = ordinary ? { created: '1', orderId: String(orderId) } : { submitted: '1' }
      await router.push({ path: '/orders', query })
    }
  } catch (e) {
    if (route.params.id === shopId) targetError.value = e.message || (ordinary ? '购买失败，请稍后重试' : '抢购失败，请稍后重试')
  } finally {
    pendingVoucherId.value = null
  }
}
onMounted(() => { timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onUnmounted(() => { requestVersion++; window.clearInterval(timer) })
</script>

<template>
  <div v-if="loading" class="state">正在加载店铺与优惠券…</div>
  <div v-else-if="error" class="state" role="alert">{{ error }}</div>
  <div v-else-if="shop" class="detail-layout shop-offers-layout">
    <section class="detail-main">
      <img class="cover" :src="firstImage(shop.images)" :alt="shop.name">
      <span class="eyebrow">SHOP DETAIL</span>
      <h1>{{ shop.name }}</h1>
      <p class="stars">★★★★★ <small>{{ shop.score }} 分</small></p>
      <p>{{ shop.address }}</p>
      <div class="panel">
        <h2>商户介绍</h2>
        <p>{{ shop.comments || '暂无商户介绍' }}</p>
        <p>营业时间：{{ shop.openHours || '以商户实际营业时间为准' }}</p>
      </div>
    </section>
    <aside class="offers-sections">
      <section class="side-card offers-section" aria-labelledby="ordinary-heading">
        <div class="offers-heading"><div><span class="eyebrow">REGULAR OFFERS</span><h2 id="ordinary-heading">普通优惠券</h2></div><span v-if="!ordinaryError" class="offers-count">{{ ordinaryVouchers.length }} 张</span></div>
        <div v-if="ordinaryError" class="offers-error" role="alert"><p>{{ ordinaryError }}</p><button type="button" @click="loadShop(route.params.id)">重新加载</button></div>
        <div v-else-if="ordinaryVouchers.length" class="offers-list">
          <p v-if="ordinaryPurchaseError" class="offers-error" role="alert">{{ ordinaryPurchaseError }}</p>
          <VoucherCard v-for="voucher in ordinaryVouchers" :key="voucher.id" :voucher="voucher" :now="now" :busy="pendingVoucherId !== null" :submitting="pendingVoucherId !== null && pendingVoucherId === voucher.id" @buy="buyOrdinary" />
        </div>
        <p v-else class="offers-empty">商家暂未提供普通优惠券</p>
      </section>
      <section class="side-card offers-section" aria-labelledby="seckill-heading">
        <div class="offers-heading"><div><span class="eyebrow">LIMITED-TIME OFFERS</span><h2 id="seckill-heading">秒杀优惠券</h2></div><span v-if="!seckillError" class="offers-count">{{ seckillVouchers.length }} 场</span></div>
        <p class="offers-intro">查看活动时间与使用规则，开始后即可抢购。</p>
        <div v-if="seckillError" class="offers-error" role="alert"><p>秒杀券列表加载失败：{{ seckillError }}</p><button type="button" @click="loadShop(route.params.id)">重新加载</button></div>
        <p v-if="purchaseError" class="offers-error" role="alert">{{ purchaseError }}</p>
        <div v-if="!seckillError && seckillVouchers.length" class="offers-list">
          <VoucherCard v-for="voucher in seckillVouchers" :key="voucher.id ?? voucher.voucherId" :voucher="voucher" :now="now" seckill :busy="pendingVoucherId !== null" :submitting="pendingVoucherId !== null && pendingVoucherId === (voucher.id ?? voucher.voucherId)" @buy="buy" />
        </div>
        <p v-else-if="!seckillError" class="offers-empty">暂无秒杀活动</p>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.shop-offers-layout{grid-template-columns:minmax(0,1fr) minmax(380px,480px);align-items:start}
.offers-sections{display:grid;gap:24px;min-width:0}.offers-section{padding:24px}.offers-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.offers-heading h2{margin:6px 0 0;font-size:22px}.offers-count{font-size:12px;color:#788278;background:#f1f4ef;padding:5px 9px;border-radius:20px;white-space:nowrap}.offers-intro{font-size:13px;line-height:1.7;color:#7a857b;margin:10px 0 0}.offers-list{display:grid;gap:14px;margin-top:20px}.offers-empty{margin:20px 0 0;padding:20px 12px;border:1px dashed #dfe5dc;border-radius:10px;text-align:center;color:#879084;font-size:13px}.offers-error{font-size:13px;line-height:1.7;padding:12px;background:#fff3ef;border-radius:8px;color:#a6472e;margin-top:16px}.offers-error p{margin:0}.offers-error button{margin-top:8px;background:#fff;border:1px solid #e7c2b7;color:#a6472e;padding:5px 10px;border-radius:6px}@media(max-width:1100px){.shop-offers-layout{grid-template-columns:minmax(0,1fr) minmax(350px,420px)}}
</style>