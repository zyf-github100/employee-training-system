import { createRouter, createWebHashHistory } from 'vue-router'
import { useTraining } from './store'
export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/courses/:id/manage',
      component: () => import('./pages/CourseManagement.vue'),
      meta: { title: '章节与资源', admin: true }
    },
    {
      path: '/plans/:id/participants',
      component: () => import('./pages/PlanParticipants.vue'),
      meta: { title: '参训名单', admin: true }
    },
    {
      path: '/question-bank',
      component: () => import('./pages/QuestionBank.vue'),
      meta: { title: '题库管理', admin: true }
    },
    {
      path: '/exams/compose/:id?',
      component: () => import('./pages/PaperBuilder.vue'),
      meta: { title: '组卷工作台', admin: true }
    },
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      component: () => import('./pages/Dashboard.vue'),
      meta: { title: '学习工作台' }
    },
    {
      path: '/courses',
      component: () => import('./pages/Courses.vue'),
      meta: { title: '课程中心' }
    },
    {
      path: '/learn/:id',
      component: () => import('./pages/Learning.vue'),
      meta: { title: '课程学习' }
    },
    { path: '/plans', component: () => import('./pages/Plans.vue'), meta: { title: '培训计划' } },
    {
      path: '/profile',
      component: () => import('./pages/Profile.vue'),
      meta: { title: '个人中心' }
    },
    {
      path: '/records',
      component: () => import('./pages/LearningRecords.vue'),
      meta: { title: '学习记录', employee: true }
    },
    { path: '/exams', component: () => import('./pages/Exams.vue'), meta: { title: '考试中心' } },
    {
      path: '/exam/:id',
      component: () => import('./pages/ExamRoom.vue'),
      meta: { title: '在线考试', employee: true }
    },
    {
      path: '/employees',
      component: () => import('./pages/Employees.vue'),
      meta: { title: '组织与员工', admin: true }
    },
    {
      path: '/analytics',
      component: () => import('./pages/Analytics.vue'),
      meta: { title: '数据分析', admin: true }
    },
    {
      path: '/archives',
      component: () => import('./pages/Archives.vue'),
      meta: { title: '学习档案' }
    },
    {
      path: '/feedback',
      component: () => import('./pages/Feedback.vue'),
      meta: { title: '交流与反馈' }
    },
    {
      path: '/settings',
      component: () => import('./pages/Settings.vue'),
      meta: { title: '平台设置', admin: true }
    },
    {
      path: '/login',
      component: () => import('./pages/Login.vue'),
      meta: { title: '欢迎来到知行' }
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach((to) => {
  const s = useTraining()
  if (!s.isAdmin && s.profile.status !== '在职') s.signedIn = false
  if (!s.signedIn && to.path !== '/login') return '/login'
  if (to.meta.admin && !s.isAdmin) return '/dashboard'
  if (to.meta.employee && s.isAdmin) return '/dashboard'
})
router.afterEach((to, _from, failure) => {
  if (failure) return
  document.title = `${to.meta.title || '首页'} · 知行企业学习平台`
})
