<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listTypes, listShops } from '../../api/catalog'
import { firstImage } from '../../utils/images'

const PAGE_SIZE = 5
const route = useRoute()
const router = useRouter()
const types = ref([])
const shops = ref([])
const selectedType = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref('')
let currentPage = 0
let requestVersion = 0

onMounted(async () => {
  try { types.value = await listTypes() || [] }
  catch (e) { error.value = e.message }
})

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  const version = requestVersion
  const typeId = selectedType.value
  const nextPage = currentPage + 1
  loadingMore.value = true
  error.value = ''
  try {
    const records = await listShops(typeId, nextPage) || []
    if (version !== requestVersion) return
    shops.value.push(...records)
    currentPage = nextPage
    hasMore.value = records.length === PAGE_SIZE
  } catch (e) {
    if (version === requestVersion) error.value = e.message
  } finally {
    if (version === requestVersion) {
      loadingMore.value = false
      loading.value = false
    }
  }
}

async function chooseType(typeId) {
  if (selectedType.value === typeId) return
  await router.push({ path: '/shops', query: { type: typeId } })
}

watch(() => route.query.type, value => {
  requestVersion += 1
  selectedType.value = Number(value) || 1
  shops.value = []
  currentPage = 0
  hasMore.value = true
  loadingMore.value = false
  loading.value = true
  error.value = ''
  loadMore()
}, { immediate: true })
</script>

<template>
  <div class="page-title"><span class="eyebrow">LOCAL GUIDE</span><h1>附近好店</h1><p>从真实评分与距离出发，找到下一家值得到访的店。</p></div>
  <div class="filters"><button v-for="type in types" :key="type.id" :class="{active:selectedType===type.id}" @click="chooseType(type.id)">{{type.name}}</button></div>
  <div v-if="loading" class="state">正在加载…</div>
  <template v-else>
    <div class="shop-grid"><article v-for="shop in shops" :key="shop.id" class="shop-card" @click="router.push('/shop/'+shop.id)"><img :src="firstImage(shop.images)"><div><span class="tag">精选商户</span><h3>{{shop.name}}</h3><p class="stars">★★★★★ <small>{{shop.score||'暂无评分'}}</small></p><p>{{shop.area||''}} · {{shop.address}}</p><strong>人均 ¥{{shop.avgPrice||'--'}}</strong></div></article></div>
    <div v-if="!shops.length && !error" class="state">暂无商户</div>
    <div v-if="error" class="state">{{error}} <button class="secondary" @click="loadMore">重试</button></div>
    <div v-else-if="hasMore" class="shop-load-more"><button class="secondary" :disabled="loadingMore" @click="loadMore">{{loadingMore?'加载中…':'加载更多'}}</button></div>
  </template>
</template>
