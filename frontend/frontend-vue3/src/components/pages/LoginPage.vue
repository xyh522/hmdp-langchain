<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="login-page">
    <section class="login-copy"><span class="logo">点</span><h1>发现城市里的好味道</h1><p>真实评价、附近好店与限时优惠。</p></section>
    <section class="login-card login-choice">
      <span class="eyebrow">WELCOME</span><h2>选择登录入口</h2>
      <p class="muted">根据你的身份进入对应页面</p>
      <button class="login-entry" @click="router.push('/login/user')"><strong>用户登录</strong><span>浏览店铺、美食与订单 →</span></button>
      <button class="login-entry admin-entry" @click="router.push('/login/admin')"><strong>管理员登录</strong><span>进入管理后台 →</span></button>
      <button class="link full" @click="router.push('/')">先逛逛</button>
    </section>
  </div>
</template>
