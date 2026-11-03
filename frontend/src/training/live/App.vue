<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  import { ElMessage } from 'element-plus'
  import { logout } from '../../connected/api'
  import { currentUser, errorMessage } from './session'
  import { platformSettings, syncPlatform, unreadAnnouncements } from './platform'
  const route = useRoute(),
    router = useRouter()
  const mobileOpen = ref(false),
    collapsed = ref(false),
    leaving = ref(false)
  const isManagement = computed(
    () => route.path.startsWith('/admin') && currentUser.value?.roleCode === 'ADMIN'
  )
  const home = computed(() => (isManagement.value ? '/admin/dashboard' : '/dashboard'))
  const profilePath = computed(() => (isManagement.value ? '/admin/profile' : '/profile'))
  const links = computed(() =>
    isManagement.value
      ? [
          { path: '/admin/dashboard', name: '管理工作台', icon: 'dashboard' },
          { path: '/admin/courses', name: '课程管理', icon: 'book' },
          { path: '/admin/plans', name: '培训计划', icon: 'calendar' },
          { path: '/admin/questions', name: '题库管理', icon: 'collection' },
          { path: '/admin/exams', name: '考试管理', icon: 'exam' },
          { path: '/admin/employees', name: '组织与员工', icon: 'user' },
          { path: '/admin/archives', name: '培训档案', icon: 'medal' },
          { path: '/admin/analytics', name: '数据分析', icon: 'chart' },
          { path: '/admin/certificates', name: '证书管理', icon: 'medal' },
          { path: '/admin/feedback', name: '交流与反馈', icon: 'chat' },
          { path: '/admin/announcements', name: '公告管理', icon: 'bell' },
          { path: '/admin/settings', name: '平台设置', icon: 'settings' },
          { path: '/admin/profile', name: '账号与资料', icon: 'settings' }
        ]
      : [
          { path: '/dashboard', name: '学习首页', icon: 'dashboard' },
          { path: '/courses', name: '课程中心', icon: 'book' },
          { path: '/plans', name: '我的培训', icon: 'calendar' },
          { path: '/exams', name: '我的考试', icon: 'exam' },
          { path: '/archives', name: '培训档案', icon: 'chart' },
          { path: '/certificates', name: '我的证书', icon: 'medal' },
          { path: '/feedback', name: '我的反馈', icon: 'chat' },
          { path: '/announcements', name: '通知公告', icon: 'bell' },
          { path: '/profile', name: '账号与资料', icon: 'user' }
        ]
  )
  watch(
    () => route.path,
    () => {
      mobileOpen.value = false
    }
  )
  watch(currentUser, (user) => {
    if (!user && !['/account-setup', '/verify-certificate'].includes(route.path))
      void router.replace('/login')
  })
  watch(
    [() => currentUser.value?.id, () => route.path],
    () => {
      void syncPlatform().catch(() => {})
    },
    { immediate: true }
  )
  async function signOut() {
    leaving.value = true
    try {
      if (route.path !== home.value && (await router.push(home.value))) return
      await logout()
      await router.replace('/login')
    } catch (error) {
      ElMessage.error(errorMessage(error))
    } finally {
      leaving.value = false
    }
  }
</script>
<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="['/login', '/account-setup', '/verify-certificate'].includes(route.path)" />
    <div v-else-if="!currentUser" class="live-boot" role="status">正在恢复登录状态…</div>
    <div
      v-else
      class="app-shell"
      :class="{
        'employee-mode': !isManagement,
        'management-mode': isManagement,
        'sidebar-collapsed': collapsed,
        'mobile-open': mobileOpen
      }"
    >
      <button
        v-if="mobileOpen"
        class="sidebar-backdrop"
        aria-label="关闭导航"
        @click="mobileOpen = false"
      />
      <aside id="main-sidebar" class="sidebar">
        <router-link class="brand" :to="home"
          ><img src="/brand.svg" alt="" /><div
            ><strong>知行 <span>ZHIXING</span></strong
            ><small>企业学习平台</small></div
          ></router-link
        >
        <div class="workspace"
          ><span class="workspace-icon"><AppIcon name="building" :size="18" /></span
          ><div
            ><b :title="platformSettings?.companyName">{{
              platformSettings?.companyName || '企业培训'
            }}</b
            ><small>{{ isManagement ? '培训管理工作区' : '员工学习工作区' }}</small></div
          ></div
        >
        <nav aria-label="主导航"
          ><div class="nav-group">{{ isManagement ? '培训管理' : '我的学习' }}</div
          ><router-link
            v-for="item in links"
            :key="item.path"
            :to="item.path"
            :aria-label="item.name"
            :title="collapsed ? item.name : undefined"
            class="nav-link"
            :class="{
              active:
                route.path === item.path ||
                (item.path === '/courses' && route.path.startsWith('/learn/')) ||
                (item.path === '/admin/courses' && route.path.startsWith('/admin/courses/'))
            }"
            ><AppIcon :name="item.icon" /><span>{{ item.name }}</span></router-link
          ></nav
        >
        <div class="sidebar-bottom"
          ><router-link
            v-if="currentUser.roleCode === 'ADMIN'"
            class="help-card"
            :to="isManagement ? '/dashboard' : '/admin/dashboard'"
            ><AppIcon :name="isManagement ? 'book' : 'settings'" /><span>{{
              isManagement ? '进入员工学习' : '进入培训管理'
            }}</span></router-link
          ><router-link class="sidebar-user" :to="profilePath"
            ><span class="avatar teal">{{ currentUser.name.slice(0, 1) }}</span
            ><div
              ><b>{{ currentUser.name }}</b
              ><small>{{ currentUser.employeeNo }}</small></div
            ><AppIcon name="right" :size="17" /></router-link
        ></div>
      </aside>
      <div class="app-main"
        ><header class="topbar"
          ><div class="topbar-left"
            ><button
              class="icon-button desktop-collapse"
              :aria-label="collapsed ? '展开导航' : '收起导航'"
              @click="collapsed = !collapsed"
              ><AppIcon :name="collapsed ? 'expand' : 'fold'" /></button
            ><button
              class="icon-button mobile-menu"
              aria-label="打开导航"
              aria-controls="main-sidebar"
              :aria-expanded="mobileOpen"
              @click="mobileOpen = true"
              ><AppIcon name="expand" /></button
            ><span class="breadcrumb"
              >{{ isManagement ? '管理空间' : '学习空间' }} <span>/</span>
              <b>{{ route.meta.title }}</b></span
            ></div
          ><div class="topbar-right"
            ><router-link class="global-search" :to="isManagement ? '/admin/courses' : '/courses'"
              ><AppIcon name="search" :size="17" /><span>搜索课程</span></router-link
            ><router-link
              class="service-notification-link"
              to="/announcements"
              :aria-label="`通知公告${platformSettings?.unreadReminders && unreadAnnouncements ? `（${unreadAnnouncements} 条未读）` : ''}`"
              ><AppIcon name="bell" /><span
                v-if="platformSettings?.unreadReminders && unreadAnnouncements"
                class="service-unread-dot"
                >{{ unreadAnnouncements > 99 ? '99+' : unreadAnnouncements }}</span
              ></router-link
            ><button class="text-link" :disabled="leaving" @click="signOut">{{
              leaving ? '退出中…' : '退出登录'
            }}</button
            ><span class="avatar top-avatar teal">{{ currentUser.name.slice(0, 1) }}</span></div
          ></header
        >
        <main class="main-content"
          ><div class="live-content" :class="{ 'admin-content': isManagement }"
            ><router-view :key="route.fullPath + currentUser.id" /></div
        ></main>
        <footer class="app-footer"
          ><span>© 2026 知行企业学习平台</span
          ><span v-if="platformSettings" class="footer-contact"
            >培训联系：{{ platformSettings.contactName
            }}<a
              v-if="platformSettings.contactEmail"
              :href="`mailto:${platformSettings.contactEmail}`"
              >{{ platformSettings.contactEmail }}</a
            ></span
          ><span v-else>每一次学习，都是向前的一步</span></footer
        >
      </div>
    </div>
  </el-config-provider>
</template>
