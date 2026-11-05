<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchShops } from '../../api/catalog'
import { firstImage } from '../../utils/images'

const route = useRoute()
const router = useRouter()
const shops = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = computed(() => String(route.query.q || '').trim())
const current = computed(() => Math.max(1, Number(route.query.page) || 1))
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
let requestId = 0

watch([query, current], async ([q, page]) => {
  const id = ++requestId
  shops.value = []
  total.value = 0
  error.value = ''
  if (!q) return
  loading.value = true
  try {
    const result = await searchShops(q, page)
    if (id !== requestId) return
    shops.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch (e) { if (id === requestId) error.value = e.message }
  finally { if (id === requestId) loading.value = false }
}, { immediate: true })

function goPage(page) {
  if (page >= 1 && page <= pages.value) router.push({ path: '/search', query: { q: query.value, page } })
}
</script>

<template>
  <div class="page-title"><span class="eyebrow">SHOP SEARCH</span><h1>搜索店铺</h1><p v-if="query">“{{ query }}” · 共 {{ total }} 家店铺</p><p v-else>在上方输入商户、商圈或地址</p></div>
  <div v-if="loading" class="state">正在搜索…</div>
  <div v-else-if="error" class="state error">{{ error }}</div>
  <div v-else-if="query && !shops.length" class="state">没有找到相关店铺</div>
  <template v-else-if="shops.length">
    <div class="shop-grid">
      <article v-for="shop in shops" :key="shop.id" class="shop-card" @click="router.push('/shop/' + shop.id)">
        <img :src="firstImage(shop.images)" :alt="shop.name">
        <div><span class="eyebrow">{{ shop.area || '好店' }}</span><h3>{{ shop.name }}</h3><p>{{ shop.address }}</p><p>人均 ¥{{ shop.avgPrice || 0 }}</p></div>
      </article>
    </div>
    <div class="search-pagination"><button :disabled="current <= 1" @click="goPage(current - 1)">上一页</button><span>第 {{ current }} 页 / 共 {{ pages }} 页</span><button :disabled="current >= pages" @click="goPage(current + 1)">下一页</button></div>
  </template>
</template>
