<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import CustomerChat from './CustomerChat.vue'
const router = useRouter()
const route = useRoute()
const go = path => router.push(path)
const keyword = ref('')
watch(() => route.query.q, value => { keyword.value = String(value || '') }, { immediate: true })
function search() {
  const q = keyword.value.trim()
  if (q) router.push({ path: '/search', query: { q, page: 1 } })
}
</script>
<template>
  <div class="shell">
    <header><div class="header-inner">
      <button class="brand" @click="go('/')"><span class="logo">点</span>黑马点评</button>
      <nav>
        <button :class="{ active: route.path === '/' }" @click="go('/')">发现</button>
        <button :class="{ active: route.path === '/shops' }" @click="go('/shops')">好店</button>
        <button :class="{ active: route.path === '/orders' }" @click="go('/orders')">订单</button>
      </nav>
      <div class="actions"><form class="header-search" @submit.prevent="search"><input v-model="keyword" aria-label="搜索商户、商圈或地址" placeholder="搜索商户、商圈或地址"><button type="submit" aria-label="搜索">搜索</button></form><button class="user-btn" @click="go('/profile')">我的</button></div>
    </div></header>
    <main :class="{ 'home-main': route.path === '/' }"><RouterView /></main>
    <CustomerChat />
    <footer>黑马点评 · 发现值得去的城市好店</footer>
  </div>
</template>
