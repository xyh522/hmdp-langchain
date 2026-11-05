<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { currentUser, logout } from '../../api/auth'
import { myBlogs } from '../../api/blog'
import { firstImage, imageUrl } from '../../utils/images'
const router = useRouter()
const user = ref(null), blogs = ref([]), loading = ref(true), error = ref('')
onMounted(async () => {
  try { [user.value, blogs.value] = await Promise.all([currentUser(), myBlogs()]); blogs.value ||= [] }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
})
async function signOut() {
  try { await logout() }
  finally { router.push('/login') }
}
</script>
<template>
  <div v-if="loading" class="state">正在加载…</div><div v-else-if="error" class="state">{{error}}</div>
  <template v-else-if="user"><section class="profile-hero"><img :src="imageUrl(user.icon)"><div><span class="eyebrow">MY PROFILE</span><h1>{{user.nickName}}</h1><p>记录每一次值得分享的城市体验。</p></div><button class="secondary" @click="signOut">退出登录</button></section><div class="section-head"><h2>我的笔记</h2></div><div class="blog-grid"><article v-for="blog in blogs" :key="blog.id" class="blog-card" @click="router.push('/blog/'+blog.id)"><img :src="firstImage(blog.images)"><div class="card-body"><h3>{{blog.title}}</h3><p>{{blog.content}}</p></div></article></div></template>
</template>
