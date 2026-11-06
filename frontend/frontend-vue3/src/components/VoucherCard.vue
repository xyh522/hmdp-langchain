<script setup>
import { computed } from 'vue'

const props = defineProps({
  voucher: { type: Object, required: true },
  seckill: { type: Boolean, default: false },
  now: { type: Number, required: true },
  busy: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false }
})
const emit = defineEmits(['buy'])
const asTime = value => value ? new Date(String(value).replace(' ', 'T')).getTime() : NaN
const money = value => Number.isFinite(Number(value)) && value != null ? (Number(value) / 100).toFixed(2) : '—'
const dateText = value => value ? String(value).replace('T', ' ').replace(/\.\d+$/, '') : '未提供'
const state = computed(() => {
  if (!props.seckill) return 'ordinary'
  const start = asTime(props.voucher.beginTime)
  const end = asTime(props.voucher.endTime)
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 'invalid'
  if (props.now >= end) return 'ended'
  return props.now < start ? 'upcoming' : 'active'
})
const statusText = computed(() => ({ ordinary: '普通券', upcoming: '未开始', active: '抢购中', ended: '已结束', invalid: '时间待确认' }[state.value]))
const countdown = computed(() => {
  if (!['upcoming', 'active'].includes(state.value)) return ''
  const target = state.value === 'upcoming' ? props.voucher.beginTime : props.voucher.endTime
  const seconds = Math.max(0, Math.ceil((asTime(target) - props.now) / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor(seconds % 86400 / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  const rest = seconds % 60
  return `${state.value === 'upcoming' ? '距开始' : '距结束'} ${days ? `${days}天 ` : ''}${hours}时 ${minutes}分 ${rest}秒`
})
</script>

<template>
  <article class="offer-card" :class="{ 'offer-card-seckill': seckill }">
    <div class="offer-card-top">
      <h3>{{ voucher.title || '优惠券' }}</h3>
      <span class="offer-status" :class="state">{{ statusText }}</span>
    </div>
    <p class="offer-description">{{ voucher.subTitle || '商家暂未提供优惠描述' }}</p>
    <div class="offer-price"><strong><small>¥</small>{{ money(voucher.payValue) }}</strong><span>抵扣 ¥{{ money(voucher.actualValue) }}</span></div>
    <dl v-if="seckill" class="offer-times">
      <div><dt>开始时间</dt><dd>{{ dateText(voucher.beginTime) }}</dd></div>
      <div><dt>截止时间</dt><dd>{{ dateText(voucher.endTime) }}</dd></div>
    </dl>
    <div class="offer-rules"><span>使用规则</span><p>{{ voucher.rules || '商家暂未提供使用规则' }}</p></div>
    <div v-if="seckill" class="offer-footer">
      <span class="offer-countdown">{{ countdown || statusText }}</span>
      <button type="button" :disabled="state !== 'active' || busy" @click="emit('buy', voucher)">
        {{ submitting ? '提交中…' : state === 'active' ? '立即抢购' : statusText }}
      </button>
    </div>
    <div v-else class="offer-footer">
      <span class="offer-ordinary-note">使用条件以券面规则为准</span>
      <button type="button" :disabled="busy" @click="emit('buy', voucher)">
        {{ submitting ? '提交中…' : '立即购买' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.offer-card{padding:20px;border:1px solid #e5e9e4;border-radius:12px;background:#fff}
.offer-card-seckill{border-color:#f1dece;background:linear-gradient(145deg,#fffaf6,#fff 55%)}
.offer-card-top{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.offer-card-top h3{margin:0;font-size:17px;line-height:1.5;overflow-wrap:anywhere}
.offer-status{flex-shrink:0;padding:4px 8px;border-radius:5px;font-size:12px;color:#647368;background:#eef2ee}
.offer-status.active{background:#fff0e2;color:#b85722}.offer-status.upcoming{background:#eaf1fb;color:#456a9c}
.offer-description{margin:9px 0 16px;color:#68756f;font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
.offer-price{display:flex;align-items:baseline;flex-wrap:wrap;gap:14px}.offer-price strong{font-size:29px;color:#cf6433;font-variant-numeric:tabular-nums}.offer-price strong small{font-size:16px;margin-right:3px}.offer-price>span{font-size:13px;color:#69756c}
.offer-times{display:grid;gap:7px;margin:16px 0;padding:12px;background:#f7f8f5;border-radius:8px;font-size:12px}.offer-times>div{display:flex;gap:14px}.offer-times dt{flex-shrink:0;color:#788278}.offer-times dd{margin:0;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
.offer-rules{margin-top:16px;font-size:12px;line-height:1.7}.offer-rules>span{font-weight:600;color:#4c5b51}.offer-rules p{margin:4px 0 0;color:#728075;white-space:pre-wrap;overflow-wrap:anywhere}
.offer-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:18px;padding-top:14px;border-top:1px dashed #ead9cc}.offer-countdown{font-size:12px;color:#ba5b28;font-variant-numeric:tabular-nums}.offer-footer button{flex-shrink:0;border:0;border-radius:8px;padding:10px 16px;background:#e46c35;color:#fff;font-weight:600}.offer-footer button:disabled{background:#e7e9e5;color:#8b938c;cursor:not-allowed}.offer-ordinary-note{margin:15px 0 0;color:#7e897f;font-size:12px}
</style>