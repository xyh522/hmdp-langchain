<script setup>
import { nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearCustomerSession } from '../utils/customerSession'
import { createThread, listMessages, listThreads, streamChat } from '../api/customer'
const router = useRouter()
const open = ref(false)
const sending = ref(false)
const input = ref('')
const messages = ref([])
const threads = ref([])
const activeThreadId = ref(null)
const greeting = () => [{ type: 'assistant', content: '你好，我是演唱会客服，有什么可以帮你？' }]

async function selectThread(thread) {
  if (sending.value) return
  try {
    activeThreadId.value = thread.threadId
    messages.value = await listMessages(thread.threadId) || []
    if (!messages.value.length) messages.value = greeting()
  } catch (error) { messages.value = [{ type: 'assistant', content: error.message }] }
}
async function newThread() {
  if (sending.value) return
  sending.value = true
  try {
    const thread = await createThread()
    threads.value.unshift(thread)
    activeThreadId.value = thread.threadId
    messages.value = greeting()
  } catch (error) { messages.value = [{ type: 'assistant', content: error.message || '新建对话失败' }] }
  finally { sending.value = false }
}
async function toggleChat() {
  open.value = !open.value
  if (!open.value) return
  sending.value = true
  try { threads.value = await listThreads() || [] }
  catch (error) { messages.value = [{ type: 'assistant', content: error.message || '客服加载失败' }]; sending.value = false; return }
  sending.value = false
  if (threads.value.length) await selectThread(threads.value[0])
  else await newThread()
}
async function sendMessage() {
  const message = input.value.trim()
  if (!message || sending.value || !activeThreadId.value) return
  messages.value.push({ type: 'user', content: message })
  input.value = ''
  sending.value = true
  const assistant = reactive({ type: 'assistant', content: '' })
  messages.value.push(assistant)
  try {
    await streamChat(activeThreadId.value, message, chunk => {
      assistant.content += chunk
      nextTick(() => { const panel = document.querySelector('.customer-messages'); if (panel) panel.scrollTop = panel.scrollHeight })
    })
    if (!assistant.content) assistant.content = '客服暂未返回内容。'
  } catch (error) {
    if (error.message === '登录状态已失效') { clearCustomerSession(); router.push('/login') }
    if (!assistant.content) assistant.content = error.message || '消息发送失败，请稍后重试。'
  } finally {
    sending.value = false
    if (sessionStorage.getItem('token')) {
      try { threads.value = await listThreads() || [] } catch {}
    }
  }
}
</script>
<template>
  <button class="customer-fab" aria-label="打开客服" @click="toggleChat"><span>◌</span>客服</button>
  <section v-if="open" class="customer-panel">
    <div class="customer-head"><div><strong>黑马客服</strong><small>在线服务</small></div><button @click="open=false">×</button></div>
    <div class="chat-session-bar"><button :disabled="sending" @click="newThread">＋ 新对话</button><div class="chat-session-list"><button v-for="thread in threads" :key="thread.threadId" :class="{ active: activeThreadId === thread.threadId }" :disabled="sending" @click="selectThread(thread)">{{ thread.title || '新对话' }}</button></div></div>
    <div class="customer-messages"><div v-for="(message,index) in messages" :key="message.id || index" :class="['chat-row',message.type]"><span>{{ message.content }}</span></div><div v-if="sending" class="chat-row assistant"><span>正在处理…</span></div></div>
    <div class="customer-input"><textarea v-model="input" placeholder="请输入消息" rows="2" @keydown.enter.exact.prevent="sendMessage"></textarea><button :disabled="sending || !input.trim() || !activeThreadId" @click="sendMessage">发送</button></div>
  </section>
</template>
