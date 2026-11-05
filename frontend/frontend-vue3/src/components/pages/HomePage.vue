<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listTypes } from '../../api/catalog'
import { getBlog, hotBlogs, likeBlog } from '../../api/blog'
import { firstImage, imageUrl } from '../../utils/images'
const router = useRouter()
const types = ref([]), blogs = ref([]), loading = ref(true), error = ref('')
const go = path => router.push(path)
onMounted(async () => {
  try { [types.value, blogs.value] = await Promise.all([listTypes(), hotBlogs()]); types.value ||= []; blogs.value ||= [] }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
})
async function like(blog) {
  try { await likeBlog(blog.id); Object.assign(blog, await getBlog(blog.id)) }
  catch (e) { error.value = e.message }
}
</script>
<template>
  <div v-if="loading" class="state">正在加载…</div><div v-else-if="error" class="state">{{error}}</div>
  <template v-else><section class="hero"><div><span class="eyebrow">EXPLORE HANGZHOU</span><h1>把城市里值得去的地方<br><em>收进你的周末。</em></h1><p>真实体验、精选好店和限时优惠，都在这里。</p><button class="primary" @click="go('/shops')">开始探索</button></div><div class="hero-art"><span>今日推荐</span><strong>发现生活<br>附近的好味道</strong></div></section><section><div class="section-head"><div><span class="eyebrow">CATEGORIES</span><h2>按分类发现</h2></div><button class="link" @click="go('/shops')">查看全部 →</button></div><div class="category-grid"><button v-for="type in types" :key="type.id" @click="go('/shops?type='+type.id)"><img :src="imageUrl(type.icon)"><span>{{type.name}}</span></button></div></section><section><div class="section-head"><div><span class="eyebrow">CITY STORIES</span><h2>达人正在分享</h2></div></div><div class="blog-grid"><article v-for="blog in blogs" :key="blog.id" class="blog-card"><img :src="firstImage(blog.images)" @click="go('/blog/'+blog.id)"><div class="card-body"><h3>{{blog.title}}</h3><div class="card-meta"><span><img :src="imageUrl(blog.icon)">{{blog.name}}</span><button :class="{liked:blog.isLike}" @click="like(blog)">♥ {{blog.liked||0}}</button></div></div></article></div></section></template>
</template>
