<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, sendCode } from '../../api/auth'
const router = useRouter()
const form = reactive({ phone: '', code: '' })
const error = ref('')
const busy = ref(false)
async function requestCode() {
  error.value = ''
  try { await sendCode(form.phone); alert('验证码已发送') }
  catch (e) { error.value = e.message }
}
async function submit() {
  busy.value = true
  error.value = ''
  try {
    await login({ phone: form.phone, code: form.code, role: 'USER' })
    router.push('/')
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <div class="login-page">
    <section class="login-copy"><span class="logo">点</span><h1>欢迎来到黑马点评</h1><p>登录后继续发现附近的好味道。</p></section>
    <section class="login-card">
      <span class="eyebrow">USER LOGIN</span><h2>用户登录</h2>
      <label>手机号<input v-model.trim="form.phone" type="tel" placeholder="请输入手机号"></label>
      <label>验证码<div class="input-action"><input v-model.trim="form.code" placeholder="6 位验证码"><button type="button" @click="requestCode">获取验证码</button></div></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary full" :disabled="busy" @click="submit">登录用户端</button>
      <button class="link full" @click="router.push('/login')">返回选择登录入口</button>
    </section>
  </div>
</template>
