<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logout } from '../../api/auth'
import { adminTypes, adminShops, adminShop, updateAdminShop, adminVouchers, addAdminVoucher, deleteAdminVoucher } from '../../api/admin'

const router = useRouter()
const types = ref([])
const typeId = ref(null)
const shops = ref([])
const page = ref(1)
const total = ref(0)
const size = 10
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const selectedShop = ref(null)
const modal = ref('')
const shopForm = reactive({})
const vouchers = ref([])
const voucherKind = ref(0)
const voucherForm = reactive({ title: '', subTitle: '', rules: '', payValue: '', actualValue: '', stock: '', beginTime: '', endTime: '' })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const modalTitle = computed(() => modal.value === 'shop' ? '编辑店铺'
  : modal.value === 'vouchers' ? '优惠券管理'
  : voucherKind.value === 1 ? '新增秒杀券' : '新增普通券')

function report(e) { error.value = e?.message || '操作失败'; notice.value = '' }
function resetMessage() { error.value = ''; notice.value = '' }
function money(fen) { return (Number(fen || 0) / 100).toFixed(2) }

async function loadShops() {
  if (!typeId.value) return
  loading.value = true
  resetMessage()
  try {
    const result = await adminShops(typeId.value, page.value, size)
    shops.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch (e) { report(e) }
  finally { loading.value = false }
}

async function chooseType(id) {
  typeId.value = id
  page.value = 1
  selectedShop.value = null
  modal.value = ''
  await loadShops()
}

async function changePage(next) {
  if (next < 1 || next > pages.value || next === page.value) return
  page.value = next
  selectedShop.value = null
  modal.value = ''
  await loadShops()
}

function closeModal() {
  if (saving.value) return
  modal.value = ''
  error.value = ''
}

async function openShop(id, action, kind = 0) {
  resetMessage()
  try {
    const shop = await adminShop(id)
    selectedShop.value = shop
    Object.assign(shopForm, shop)
    if (action === 'vouchers') vouchers.value = await adminVouchers(id) || []
    if (action === 'create') {
      voucherKind.value = kind
      Object.assign(voucherForm, { title: '', subTitle: '', rules: '', payValue: '', actualValue: '', stock: '', beginTime: '', endTime: '' })
    }
    modal.value = action
  } catch (e) { report(e) }
}

async function saveShop() {
  if (!selectedShop.value || saving.value) return
  saving.value = true
  resetMessage()
  try {
    const data = {
      name: shopForm.name, typeId: Number(shopForm.typeId), area: shopForm.area,
      address: shopForm.address, images: shopForm.images, openHours: shopForm.openHours,
      avgPrice: Number(shopForm.avgPrice), x: Number(shopForm.x), y: Number(shopForm.y)
    }
    await updateAdminShop(selectedShop.value.id, data)
    selectedShop.value = await adminShop(selectedShop.value.id)
    Object.assign(shopForm, selectedShop.value)
    await loadShops()
    modal.value = ''
    notice.value = '店铺信息已保存'
  } catch (e) { report(e) }
  finally { saving.value = false }
}

function localDateTime(value) { return value ? `${value}:00` : null }
async function addVoucher() {
  if (!selectedShop.value || saving.value) return
  saving.value = true
  resetMessage()
  try {
    const payValue = Math.round(Number(voucherForm.payValue) * 100)
    const actualValue = Math.round(Number(voucherForm.actualValue) * 100)
    if (!voucherForm.title.trim() || !Number.isFinite(payValue) || !Number.isFinite(actualValue)
        || payValue < 0 || actualValue <= 0) throw new Error('请填写有效的标题和金额')
    const data = {
      title: voucherForm.title.trim(), subTitle: voucherForm.subTitle, rules: voucherForm.rules,
      payValue, actualValue, type: voucherKind.value
    }
    if (voucherKind.value === 1) {
      data.stock = Number(voucherForm.stock)
      data.beginTime = localDateTime(voucherForm.beginTime)
      data.endTime = localDateTime(voucherForm.endTime)
      if (!Number.isInteger(data.stock) || data.stock <= 0 || !data.beginTime || !data.endTime) {
        throw new Error('请填写有效的秒杀库存和起止时间')
      }
    }
    await addAdminVoucher(selectedShop.value.id, data)
    vouchers.value = await adminVouchers(selectedShop.value.id) || []
    Object.assign(voucherForm, { title: '', subTitle: '', rules: '', payValue: '', actualValue: '', stock: '', beginTime: '', endTime: '' })
    modal.value = ''
    notice.value = voucherKind.value === 1 ? '秒杀券已创建并写入库存' : '普通优惠券已创建'
  } catch (e) { report(e) }
  finally { saving.value = false }
}

async function removeVoucher(voucher) {
  if (!selectedShop.value || saving.value || !window.confirm(`确定删除“${voucher.title}”吗？`)) return
  saving.value = true
  resetMessage()
  try {
    await deleteAdminVoucher(selectedShop.value.id, voucher.id)
    vouchers.value = await adminVouchers(selectedShop.value.id) || []
    notice.value = '优惠券已删除'
  } catch (e) { report(e) }
  finally { saving.value = false }
}

async function signOut() {
  try { await logout() } catch {}
  router.push('/login/admin')
}

onMounted(async () => {
  try {
    types.value = await adminTypes() || []
    if (types.value.length) await chooseType(types.value[0].id)
  } catch (e) { report(e) }
})
function onKeydown(event) { if (event.key === 'Escape') closeModal() }
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="admin-shell">
    <header class="admin-top"><strong>黑马点评 <span>管理后台</span></strong><button @click="signOut">退出登录</button></header>
    <div class="admin-layout">
      <aside class="admin-sidebar">
        <h2>店铺分类</h2>
        <button v-for="type in types" :key="type.id" :class="{ active: typeId === type.id }" @click="chooseType(type.id)">{{ type.name }}</button>
      </aside>
      <main class="admin-content">
        <div class="admin-title"><div><span class="eyebrow">SHOP MANAGEMENT</span><h1>店铺管理</h1></div><span>共 {{ total }} 家店铺</span></div>
        <p v-if="error" class="admin-feedback error">{{ error }}</p>
        <p v-if="notice" class="admin-feedback success">{{ notice }}</p>
        <section class="admin-panel">
          <div class="admin-panel-head"><h2>{{ types.find(t => t.id === typeId)?.name || '分类' }}店铺</h2><span>第 {{ page }} 页 / 共 {{ pages }} 页</span></div>
          <p v-if="loading" class="muted">正在加载店铺…</p>
          <div v-else-if="!shops.length" class="admin-empty">当前分类没有店铺</div>
          <div v-else class="admin-shop-list">
            <article v-for="shop in shops" :key="shop.id" class="admin-shop-row">
              <div class="admin-shop-summary"><span class="admin-shop-id">#{{ shop.id }}</span><strong>{{ shop.name }}</strong><small>{{ shop.area || '未填写商圈' }} · {{ shop.address }}</small></div>
              <div class="admin-shop-actions">
                <button @click="openShop(shop.id, 'shop')">编辑店铺</button>
                <button @click="openShop(shop.id, 'vouchers')">查看优惠券</button>
                <button @click="openShop(shop.id, 'create', 0)">新增普通券</button>
                <button class="accent" @click="openShop(shop.id, 'create', 1)">新增秒杀券</button>
              </div>
            </article>
          </div>
          <div class="admin-pagination"><button :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} 页，共 {{ pages }} 页</span><button :disabled="page >= pages || loading" @click="changePage(page + 1)">下一页</button></div>
        </section>

      </main>
    </div>

    <div v-if="modal && selectedShop" class="admin-modal-backdrop" @click.self="closeModal">
      <section class="admin-modal" role="dialog" aria-modal="true" :aria-label="modalTitle">
        <header class="admin-modal-header">
          <div><span class="eyebrow">#{{ selectedShop.id }} · {{ selectedShop.name }}</span><h2>{{ modalTitle }}</h2></div>
          <button class="admin-modal-close" aria-label="关闭弹窗" :disabled="saving" @click="closeModal">×</button>
        </header>
        <div class="admin-modal-body">
          <p v-if="error" class="admin-feedback error">{{ error }}</p>
          <template v-if="modal === 'shop'">
            <div class="admin-form-grid">
              <label>店铺名称<input v-model.trim="shopForm.name"></label>
              <label>店铺分类<select v-model.number="shopForm.typeId"><option v-for="type in types" :key="type.id" :value="type.id">{{ type.name }}</option></select></label>
              <label>商圈<input v-model.trim="shopForm.area"></label>
              <label>地址<input v-model.trim="shopForm.address"></label>
              <label>营业时间<input v-model.trim="shopForm.openHours"></label>
              <label>人均价格（元）<input v-model.number="shopForm.avgPrice" type="number" min="0"></label>
              <label>经度<input v-model.number="shopForm.x" type="number" step="any"></label>
              <label>纬度<input v-model.number="shopForm.y" type="number" step="any"></label>
              <label class="wide">图片地址（多个用英文逗号分隔）<textarea v-model.trim="shopForm.images" rows="2"></textarea></label>
            </div>
            <div class="admin-modal-footer"><button class="admin-cancel" :disabled="saving" @click="closeModal">取消</button><button class="admin-primary" :disabled="saving" @click="saveShop">保存店铺信息</button></div>
          </template>
          <template v-else-if="modal === 'vouchers'">
            <p class="muted">共 {{ vouchers.length }} 张优惠券</p>
            <div v-if="!vouchers.length" class="admin-empty">暂无优惠券</div>
            <div v-for="voucher in vouchers" :key="voucher.id" class="admin-voucher">
              <div><strong>{{ voucher.title }}</strong><span :class="voucher.type === 1 ? 'seckill-tag' : 'normal-tag'">{{ voucher.type === 1 ? '秒杀券' : '普通券' }}</span><small>支付 ¥{{ money(voucher.payValue) }} · 抵扣 ¥{{ money(voucher.actualValue) }} <template v-if="voucher.type === 1">· 库存 {{ voucher.stock }}</template></small></div>
              <button :disabled="saving" @click="removeVoucher(voucher)">删除</button>
            </div>
          </template>
          <template v-else>
            <div class="admin-form-grid">
              <label>标题<input v-model.trim="voucherForm.title" placeholder="例如：50元代金券"></label>
              <label>副标题<input v-model.trim="voucherForm.subTitle"></label>
              <label>支付金额（元）<input v-model="voucherForm.payValue" type="number" min="0" step="0.01"></label>
              <label>抵扣金额（元）<input v-model="voucherForm.actualValue" type="number" min="0.01" step="0.01"></label>
              <label v-if="voucherKind === 1">秒杀库存<input v-model="voucherForm.stock" type="number" min="1" step="1"></label>
              <label v-if="voucherKind === 1">开始时间<input v-model="voucherForm.beginTime" type="datetime-local"></label>
              <label v-if="voucherKind === 1">结束时间<input v-model="voucherForm.endTime" type="datetime-local"></label>
              <label class="wide">使用规则<textarea v-model.trim="voucherForm.rules" rows="3"></textarea></label>
            </div>
            <div class="admin-modal-footer"><button class="admin-cancel" :disabled="saving" @click="closeModal">取消</button><button class="admin-primary" :disabled="saving" @click="addVoucher">保存{{ voucherKind === 1 ? '秒杀券' : '普通券' }}</button></div>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>
