<script setup lang="ts">
  import { ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  import { ElMessage } from 'element-plus'
  import { logout } from '../../connected/api'
  import { currentUser, errorMessage } from './session'
  const route = useRoute(),
    router = useRouter()
  const mobileOpen = ref(false),
    collapsed = ref(false),
    leaving = ref(false)
  const links = [
    { path: '/dashboard', name: '学习首页', icon: 'dashboard' },
    { path: '/courses', name: '课程中心', icon: 'book' },
    { path: '/plans', name: '我的培训', icon: 'calendar' },
    { path: '/exams', name: '我的考试', icon: 'exam' },
    { path: '/archives', name: '培训档案', icon: 'chart' },
    { path: '/certificates', name: '我的证书', icon: 'medal' },
    { path: '/profile', name: '账号与资料', icon: 'user' }
  ]
  watch(
    () => route.path,
    () => {
      mobileOpen.value = false
    }
  )
  watch(currentUser, (user) => {
    if (!user && route.path !== '/account-setup') void router.replace('/login')
  })
  async function signOut() {
    leaving.value = true
    try {
      if (route.path !== '/dashboard' && (await router.push('/dashboard'))) return
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
    <router-view v-if="route.path === '/login' || route.path === '/account-setup'" />
    <div v-else-if="!currentUser" class="live-boot" role="status">正在恢复登录状态…</div>
    <div
      v-else
      class="app-shell employee-mode"
      :class="{ 'sidebar-collapsed': collapsed, 'mobile-open': mobileOpen }"
    >
      <button
        v-if="mobileOpen"
        class="sidebar-backdrop"
        aria-label="关闭导航"
        @click="mobileOpen = false"
      />
      <aside id="main-sidebar" class="sidebar">
        <router-link class="brand" to="/dashboard"
          ><img src="/brand.svg" alt="" /><div
            ><strong>知行 <span>ZHIXING</span></strong
            ><small>企业学习平台</small></div
          ></router-link
        >
        <div class="workspace"
          ><span class="workspace-icon"><AppIcon name="building" :size="18" /></span
          ><div
            ><b>{{ currentUser.departmentName || '企业培训' }}</b
            ><small>员工学习工作区</small></div
          ></div
        >
        <nav aria-label="主导航"
          ><div class="nav-group">我的学习</div
          ><router-link
            v-for="item in links"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{
              active:
                route.path === item.path ||
                (item.path === '/courses' && route.path.startsWith('/learn/'))
            }"
            ><AppIcon :name="item.icon" /><span>{{ item.name }}</span></router-link
          ></nav
        >
        <div class="sidebar-bottom"
          ><a v-if="currentUser.roleCode === 'ADMIN'" class="help-card" href="/connected.html"
            ><AppIcon name="settings" /><span>进入培训管理</span></a
          ><router-link class="sidebar-user" to="/profile"
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
              >学习空间 <span>/</span> <b>{{ route.meta.title }}</b></span
            ></div
          ><div class="topbar-right"
            ><router-link class="global-search" to="/courses"
              ><AppIcon name="search" :size="17" /><span>搜索课程</span></router-link
            ><button class="text-link" :disabled="leaving" @click="signOut">{{
              leaving ? '退出中…' : '退出登录'
            }}</button
            ><span class="avatar top-avatar teal">{{ currentUser.name.slice(0, 1) }}</span></div
          ></header
        >
        <main class="main-content"
          ><div class="live-content"><router-view :key="route.fullPath + currentUser.id" /></div
        ></main>
        <footer class="app-footer"
          ><span>© 2026 知行企业学习平台</span><span>每一次学习，都是向前的一步</span></footer
        >
      </div>
    </div>
  </el-config-provider>
</template>
