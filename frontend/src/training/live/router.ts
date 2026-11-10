import { createRouter, createWebHashHistory } from 'vue-router'
import { currentUser, restoreSession } from './session'
export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/verify-certificate',
      component: () => import('./CertificateVerify.vue'),
      meta: { title: '证书验真' }
    },
    { path: '/feedback', component: () => import('./Feedback.vue'), meta: { title: '我的反馈' } },
    {
      path: '/announcements',
      component: () => import('./Announcements.vue'),
      meta: { title: '通知公告' }
    },
    {
      path: '/admin/feedback',
      component: () => import('./Feedback.vue'),
      props: { admin: true },
      meta: { title: '交流与反馈', admin: true }
    },
    {
      path: '/admin/announcements',
      component: () => import('./Announcements.vue'),
      props: { admin: true },
      meta: { title: '公告管理', admin: true }
    },
    {
      path: '/admin/settings',
      component: () => import('./admin/Settings.vue'),
      meta: { title: '平台设置', admin: true }
    },
    { path: '/admin', redirect: '/admin/dashboard' },
    {
      path: '/admin/dashboard',
      component: () => import('./admin/Dashboard.vue'),
      meta: { title: '管理工作台', admin: true }
    },
    {
      path: '/admin/courses',
      component: () => import('./admin/Courses.vue'),
      meta: { title: '课程管理', admin: true }
    },
    {
      path: '/admin/courses/:id',
      component: () => import('./admin/CourseManagement.vue'),
      meta: { title: '章节与资源', admin: true }
    },
    {
      path: '/admin/questions',
      component: () => import('./admin/QuestionBank.vue'),
      meta: { title: '题库管理', admin: true }
    },
    {
      path: '/admin/exams',
      component: () => import('./admin/Exams.vue'),
      meta: { title: '考试管理', admin: true }
    },
    {
      path: '/admin/plans',
      component: () => import('../../connected/PlanPanel.vue'),
      props: { admin: true },
      meta: { title: '培训计划', admin: true }
    },
    {
      path: '/admin/employees',
      component: () => import('../../connected/OrganizationPanel.vue'),
      meta: { title: '组织与员工', admin: true }
    },
    {
      path: '/admin/archives',
      component: () => import('./admin/Archives.vue'),
      meta: { title: '员工培训档案', admin: true }
    },
    {
      path: '/admin/analytics',
      component: () => import('../../connected/ArchivePanel.vue'),
      props: { admin: true },
      meta: { title: '数据分析', admin: true }
    },
    {
      path: '/admin/certificates',
      component: () => import('../../connected/CertificatePanel.vue'),
      props: { admin: true },
      meta: { title: '证书管理', admin: true }
    },
    {
      path: '/admin/profile',
      component: () => import('./Profile.vue'),
      meta: { title: '账号与资料', admin: true }
    },
    { path: '/', component: () => import('./Dashboard.vue') },
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
  if (to.path === '/account-setup' || to.path === '/verify-certificate') return
  await restoreSession()
  if (to.path === '/' && currentUser.value)
    return currentUser.value.roleCode === 'ADMIN' ? '/admin/dashboard' : '/dashboard'
  if (!currentUser.value && to.path !== '/login')
    return { path: '/login', query: { next: to.fullPath } }
  if (currentUser.value?.mustChangePassword && !['/profile', '/login'].includes(to.path))
    return { path: currentUser.value.roleCode === 'ADMIN' ? '/admin/profile' : '/profile', query: { force: '1' } }
  if (to.meta.admin && currentUser.value?.roleCode !== 'ADMIN') return '/dashboard'
  if (currentUser.value && to.path === '/login')
    return currentUser.value.roleCode === 'ADMIN' ? '/admin/dashboard' : '/dashboard'
})
router.afterEach((to) => {
  document.title = `${to.meta.title || '首页'} · 知行企业学习平台`
})
