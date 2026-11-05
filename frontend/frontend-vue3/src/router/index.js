import { createRouter, createWebHashHistory } from 'vue-router'
import SiteLayout from '../components/SiteLayout.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../components/pages/LoginPage.vue') },
    { path: '/login/user', component: () => import('../components/pages/UserLoginPage.vue') },
    { path: '/login/admin', component: () => import('../components/pages/AdminLoginPage.vue') },
    { path: '/admin', component: () => import('../components/pages/AdminPage.vue'), meta: { role: 'ADMIN' } },
    { path: '/', component: SiteLayout, children: [
      { path: '', component: () => import('../components/pages/HomePage.vue') },
      { path: 'shops', component: () => import('../components/pages/ShopsPage.vue') },
      { path: 'search', component: () => import('../components/pages/SearchPage.vue') },
      { path: 'shop/:id', component: () => import('../components/pages/ShopDetailPage.vue') },
      { path: 'blog/:id', component: () => import('../components/pages/BlogDetailPage.vue') },
      { path: 'profile', component: () => import('../components/pages/ProfilePage.vue') },
      { path: 'orders', component: () => import('../components/pages/OrdersPage.vue') }
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
router.beforeEach(async to => {
  if (to.meta.role !== 'ADMIN') return true
  if (!sessionStorage.getItem('token')) return '/login/admin'
  try {
    const { currentUser } = await import('../api/auth')
    const user = await currentUser()
    return user?.role === 'ADMIN' ? true : '/login/admin'
  } catch { return '/login/admin' }
})

export default router
