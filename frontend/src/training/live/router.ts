import { createRouter, createWebHashHistory } from 'vue-router'
import { currentUser, restoreSession } from './session'
export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/account-setup',
      component: () => import('./AccountSetup.vue'),
      meta: { title: '设置密码' }
    },
    { path: '/login', component: () => import('./Login.vue'), meta: { title: '登录' } },
    { path: '/dashboard', component: () => import('./Dashboard.vue'), meta: { title: '学习首页' } },
    { path: '/courses', component: () => import('./Courses.vue'), meta: { title: '课程中心' } },
    { path: '/learn/:id', component: () => import('./Learning.vue'), meta: { title: '课程学习' } },
    {
      path: '/exams',
      component: () => import('../../connected/AssessmentPanel.vue'),
      props: { admin: false },
      meta: { title: '我的考试' }
    },
    {
      path: '/plans',
      component: () => import('../../connected/PlanPanel.vue'),
      props: { admin: false, employeeLinks: true },
      meta: { title: '我的培训' }
    },
    { path: '/records', redirect: '/archives' },
    {
      path: '/archives',
      component: () => import('../../connected/ArchivePanel.vue'),
      props: { admin: false },
      meta: { title: '培训档案' }
    },
    {
      path: '/certificates',
      component: () => import('../../connected/CertificatePanel.vue'),
      props: { admin: false },
      meta: { title: '我的证书' }
    },
    { path: '/profile', component: () => import('./Profile.vue'), meta: { title: '账号与资料' } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach(async (to) => {
  if (to.path === '/account-setup') return
  await restoreSession()
  if (!currentUser.value && to.path !== '/login')
    return { path: '/login', query: { next: to.fullPath } }
  if (currentUser.value && to.path === '/login') return '/dashboard'
})
router.afterEach((to) => {
  document.title = `${to.meta.title || '首页'} · 知行企业学习平台`
})
