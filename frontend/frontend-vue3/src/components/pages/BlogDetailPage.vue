<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getBlog, likeBlog } from '../../api/blog'
import { imageUrl } from '../../utils/images'
const route = useRoute()
const blog = ref(null), loading = ref(true), error = ref('')
watch(() => route.params.id, async id => {
  loading.value = true; error.value = ''
  try { blog.value = await getBlog(id) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}, { immediate: true })
async function like() {
  try { await likeBlog(blog.value.id); blog.value = await getBlog(blog.value.id) }
  catch (e) { error.value = e.message }
}
</script>
<template>
  <div v-if="loading" class="state">正在加载…</div><div v-else-if="error" class="state">{{error}}</div>
  <article v-else-if="blog" class="article"><div class="author"><img :src="imageUrl(blog.icon)"><div><strong>{{blog.name}}</strong><small>城市体验官</small></div></div><h1>{{blog.title}}</h1><p class="lead">{{blog.content}}</p><div class="article-images"><img v-for="image in blog.images?.split(',')" :key="image" :src="imageUrl(image)"></div><button class="like-action" :class="{liked:blog.isLike}" @click="like">♥ {{blog.liked||0}} 人喜欢</button></article>
</template>
