<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { useRouter, useRoute } from 'vue-router'
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  import { useTraining } from './store'
  const s = useTraining(),
    router = useRouter(),
    route = useRoute()
  const mobileOpen = ref(false),
    collapsed = ref(false),
    search = ref(''),
    searchOpen = ref(false),
    noticesOpen = ref(false),
    helpOpen = ref(false)
  const links = computed(() =>
    s.isAdmin
      ? [
          { path: '/dashboard', name: '工作台', icon: 'dashboard', group: '概览' },
          { path: '/courses', name: '课程中心', icon: 'book', group: '培训管理' },
          { path: '/plans', name: '培训计划', icon: 'calendar' },
          { path: '/exams', name: '考试管理', icon: 'exam' },
          { path: '/question-bank', name: '题库管理', icon: 'collection' },
          { path: '/employees', name: '组织与员工', icon: 'user', group: '组织成长' },
          { path: '/archives', name: '培训档案', icon: 'medal' },
          { path: '/analytics', name: '数据分析', icon: 'chart' },
          { path: '/feedback', name: '交流与反馈', icon: 'chat', group: '平台服务' },
          { path: '/settings', name: '平台设置', icon: 'settings' },
          { path: '/profile', name: '个人中心', icon: 'user' }
        ]
      : [
          { path: '/dashboard', name: '学习首页', icon: 'dashboard', group: '我的学习' },
          { path: '/courses', name: '课程中心', icon: 'book' },
          { path: '/plans', name: '我的培训', icon: 'calendar' },
          { path: '/records', name: '学习记录', icon: 'chart' },
          { path: '/exams', name: '考试与练习', icon: 'exam' },
          { path: '/archives', name: '我的档案', icon: 'medal', group: '个人中心' },
          { path: '/feedback', name: '交流与反馈', icon: 'chat' },
          { path: '/profile', name: '账号与资料', icon: 'user' }
        ]
  )
  const searchResults = computed(() => {
    const q = search.value.trim()
    if (!q) return []
    return [
      ...s.published.map((c) => ({ title: c.title, category: '课程', path: `/learn/${c.id}` })),
      ...s.accessiblePlans.map((p) => ({ title: p.title, category: '培训计划', path: '/plans' })),
      ...s.accessibleExams.map((e) => ({ title: e.title, category: '考试', path: '/exams' }))
    ].filter((x) => x.title.toLowerCase().includes(q.toLowerCase()))
  })
  async function switchRole() {
    const next = s.isAdmin ? 'employee' : 'admin'
    if (route.path === '/dashboard') {
      s.role = next
      return
    }
    const failure = await router.push('/dashboard')
    if (!failure) s.role = next
  }
  watch(
    () => route.path,
    () => {
      mobileOpen.value = false
      searchOpen.value = false
    }
  )
</script>
<template>
  <el-config-provider :locale="zhCn"
    ><router-view v-if="route.path === '/login'" /><div
      v-else
      class="app-shell"
      :class="{ 'sidebar-collapsed': collapsed, 'mobile-open': mobileOpen, 'employee-mode': !s.isAdmin }"
    >
      <button
        v-if="mobileOpen"
        class="sidebar-backdrop"
        aria-label="关闭导航"
        @click="mobileOpen = false"
      />
      <aside id="main-sidebar" class="sidebar"
        ><router-link class="brand" to="/dashboard"
          ><img src="/brand.svg" alt="" /><div
            ><strong>知行 <span>ZHIXING</span></strong
            ><small>企业学习平台</small></div
          ></router-link
        ><div class="workspace"
          ><span class="workspace-icon"><AppIcon name="building" :size="18" /></span
          ><div
            ><b>{{ s.data.settings.company }}</b
            ><small>{{ s.isAdmin ? '让每一份潜力，都被看见' : '员工培训工作区' }}</small></div
          ><AppIcon name="down" :size="13"
        /></div>
        <nav aria-label="主导航"
          ><template v-for="item in links" :key="item.path"
            ><div v-if="item.group" class="nav-group">{{ item.group }}</div
            ><router-link
              :to="item.path"
              class="nav-link"
              :class="{
                active:
                  route.path === item.path ||
                  (item.path === '/courses' &&
                    (route.path.startsWith('/learn') || route.path.startsWith('/courses/'))) ||
                  (item.path === '/exams' &&
                    (route.path.startsWith('/exam/') || route.path.startsWith('/exams/'))) ||
                  (item.path === '/plans' && route.path.startsWith('/plans/'))
              }"
              ><AppIcon :name="item.icon" /><span>{{ item.name }}</span
              ><span v-if="item.path === '/feedback' && s.isAdmin" class="nav-count">{{
                s.data.feedback.filter((f) => f.status === '待处理').length
              }}</span></router-link
            ></template
          ></nav
        >
        <div class="sidebar-bottom"
          ><button class="help-card" @click="helpOpen = true"
            ><span class="help-circle">?</span
            ><div><b>需要一点帮助？</b><small>查看平台使用指南</small></div
            ><AppIcon name="right" :size="14" /></button
          ><button class="sidebar-user" @click="router.push('/profile')"
            ><span class="avatar" :class="s.isAdmin ? 'teal' : s.profile.avatar">{{
              s.displayName.slice(0, 1)
            }}</span
            ><div
              ><b>{{ s.displayName }}</b
              ><small>{{
                s.isAdmin ? '培训管理员' : s.profile.department + ' · ' + s.profile.job
              }}</small></div
            ><AppIcon name="right" :size="17" /></button></div
      ></aside>
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
              >{{ s.isAdmin ? '工作空间' : '学习空间' }} <span>/</span> <b>{{ route.meta.title }}</b></span
            ></div
          ><div class="topbar-right"
            ><button class="global-search" @click="searchOpen = true"
              ><AppIcon name="search" :size="17" /><span>搜索课程、培训计划...</span
              ><kbd>搜索</kbd></button
            ><span v-if="s.isAdmin" class="demo-indicator"><i />演示工作区</span
            ><a v-if="s.isAdmin" class="connected-workspace-link" href="/connected.html">后端联调</a
            ><button class="role-switch" @click="switchRole"
              ><AppIcon name="switch" :size="16" />{{
                s.isAdmin ? '员工学习端' : '管理工作台'
              }}</button
            ><button
              class="icon-button notification-button"
              aria-label="查看通知"
              @click="noticesOpen = true"
              ><AppIcon name="bell" /><i v-if="s.unread && s.data.settings.reminder" /></button
            ><button
              class="avatar top-avatar"
              :class="s.isAdmin ? 'teal' : s.profile.avatar"
              aria-label="打开个人中心"
              @click="router.push('/profile')"
              >{{ s.displayName.slice(0, 1) }}</button
            ></div
          ></header
        >
        <div class="demo-mode-banner">本地演示：当前数据仅保存在此浏览器，不是正式培训记录。<a href="/">进入正式学习平台</a></div><main class="main-content"
          ><router-view
            :key="route.path + String(route.query.practice ?? '') + s.role + s.employeeId" /></main
        ><footer class="app-footer"
          ><span>© 2026 知行企业学习平台</span><span>每一次学习，都是向前的一步</span></footer
        ></div
      >
      <el-dialog v-model="searchOpen" title="搜索工作空间" width="560px" class="responsive-dialog"
        ><el-input
          v-model="search"
          placeholder="输入课程、培训计划或考试名称"
          clearable
          size="large"
          autofocus /><div class="search-results"
          ><button
            v-for="r in searchResults"
            :key="r.category + r.title"
            @click="router.push(r.path)"
            ><AppIcon :name="r.category === '课程' ? 'book' : 'calendar'" /><span
              ><small>{{ r.category }}</small
              >{{ r.title }}</span
            ><AppIcon name="right" :size="16" /></button
          ><el-empty
            v-if="!searchResults.length"
            :description="search ? '没有找到匹配内容' : '输入关键词，发现学习内容'"
            :image-size="80" /></div
      ></el-dialog>
      <el-drawer v-model="noticesOpen" title="通知与公告" size="420px" class="notice-drawer"
        ><div class="drawer-top"
          ><span class="muted">{{ s.unread }} 条未读通知</span
          ><button class="text-link" @click="s.data.notices.forEach((n) => (n.read = true))"
            >全部标记已读</button
          ></div
        ><article
          v-for="n in s.data.notices"
          :key="n.id"
          class="notice-card"
          :class="{ unread: !n.read }"
          @click="n.read = true"
          ><span class="category-label">{{ n.category }}</span
          ><h3>{{ n.title }}</h3
          ><p>{{ n.content }}</p
          ><small>{{ n.date }}</small></article
        ></el-drawer
      >
      <el-dialog
        v-model="helpOpen"
        title="欢迎使用知行学习平台"
        width="520px"
        class="responsive-dialog"
        ><div class="help-content"
          ><p>从课程出发，让学习成为日常。</p
          ><ol
            ><li>在右上角切换「管理工作台」与「员工学习端」。</li
            ><li>管理员可以新建课程、安排培训、维护员工和回复反馈。</li
            ><li>员工可以阅读课程、记录学习进度、完成在线测评并查看档案。</li></ol
          ><div class="demo-note"
            ><AppIcon
              name="info"
            />当前为前端演示版，数据保存在本浏览器。真实身份认证、视频资源及服务端考试校验将在后端阶段接入。</div
          ></div
        ></el-dialog
      >
    </div></el-config-provider
  >
</template>
