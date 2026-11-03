<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ApiError, api, csrf, currentUser, login, logout, refresh, type Course, type CourseDetail, type Resource, type User } from './api'
import AssessmentPanel from './AssessmentPanel.vue'
import PlanPanel from './PlanPanel.vue'
import ArchivePanel from './ArchivePanel.vue'
import CertificatePanel from './CertificatePanel.vue'
import OrganizationPanel from './OrganizationPanel.vue'

const user = ref<User | null>(currentUser.value)
const view = ref<'overview' | 'courses' | 'assessment' | 'plans' | 'archive' | 'certificates' | 'organization' | 'profile'>('overview')
const loading = ref(false)
const booting = ref(true)
const error = ref('')
const notice = ref('')
const courses = ref<Course[]>([])
const total = ref(0)
const keyword = ref('')
const loginForm = reactive({ employeeNo: 'E20260001', password: 'Zhixing@123' })
const profile = reactive({ name: '', email: '', phone: '', jobTitle: '', version: 0 })
const password = reactive({ current: '', next: '' })
const editing = ref<Course | null>(null)
const editorOpen = ref(false)
const detail = ref<CourseDetail | null>(null)
const detailLoading = ref(false)
const activeResource = ref<Resource | null>(null)
const activeResourceUrl = ref('')
const resourceLoading = ref(false)
const uploadDraft = reactive<{ chapterId: number; file: File | null; title: string; sortNo: number }>({ chapterId: 0, file: null, title: '', sortNo: 1 })
const courseForm = reactive({ title: '', summary: '', category: '', difficulty: 'BEGINNER', status: 'DRAFT', durationMinutes: 30, version: 0 })

const isAdmin = computed(() => user.value?.roleCode === 'ADMIN')
const loggedIn = computed(() => !!user.value)

function message(text: string) { notice.value = text; window.setTimeout(() => { if (notice.value === text) notice.value = '' }, 3200) }
function apiMessage(e: unknown) {
  error.value = e instanceof ApiError ? `${e.message}${e.traceId ? `（追踪号 ${e.traceId.slice(0, 8)}）` : ''}` : '网络暂时不可用，请确认后端服务已启动'
}
async function loadCourses() {
  if (!user.value) return
  loading.value = true; error.value = ''
  try { const result = await api.courses(isAdmin.value, 1, 20, keyword.value); courses.value = result.list; total.value = result.total } catch (e) { apiMessage(e) } finally { loading.value = false }
}
async function openCourse(id: number) {
  closeResource(); detailLoading.value = true; error.value = ''
  try { detail.value = await api.courseDetail(id, isAdmin.value) } catch (e) { apiMessage(e) } finally { detailLoading.value = false }
}
async function enrollCourse() {
  if (!detail.value) return
  detailLoading.value = true; error.value = ''
  try { detail.value = await api.enroll(detail.value.course.id); message('已报名课程，可以开始学习') } catch (e) { apiMessage(e) } finally { detailLoading.value = false }
}
async function finishChapter(chapterId: number) {
  if (!detail.value) return
  detailLoading.value = true; error.value = ''
  try { await api.completeChapter(detail.value.course.id, chapterId); detail.value = await api.courseDetail(detail.value.course.id, isAdmin.value); message('章节进度已保存') } catch (e) { apiMessage(e) } finally { detailLoading.value = false }
}
async function openResource(resource: Resource) {
  closeResource()
  activeResource.value = resource; resourceLoading.value = true; error.value = ''
  try { activeResourceUrl.value = URL.createObjectURL(await api.resourceBlob(resource.id)) } catch (e) { activeResource.value = null; apiMessage(e) } finally { resourceLoading.value = false }
}
function closeResource() {
  if (activeResourceUrl.value) URL.revokeObjectURL(activeResourceUrl.value)
  activeResourceUrl.value = ''; activeResource.value = null
}
function selectUploadFile(event: Event, chapterId: number) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  uploadDraft.chapterId = chapterId; uploadDraft.file = file; uploadDraft.title = file?.name.replace(/\.[^.]+$/, '') ?? ''; uploadDraft.sortNo = 1
}
async function uploadForChapter(chapterId: number) {
  if (!uploadDraft.file || !detail.value) { error.value = '请先选择一个资源文件'; return }
  detailLoading.value = true; error.value = ''
  try { await api.uploadResource(chapterId, uploadDraft.file, uploadDraft.title, uploadDraft.sortNo); detail.value = await api.courseDetail(detail.value.course.id, true); uploadDraft.file = null; uploadDraft.title = ''; message('资源上传成功') } catch (e) { apiMessage(e) } finally { detailLoading.value = false }
}
async function removeResource(resource: Resource) {
  if (!detail.value || !window.confirm(`确定删除资源“${resource.title}”吗？`)) return
  detailLoading.value = true; error.value = ''
  try { await api.deleteResource(resource.id); if (activeResource.value?.id === resource.id) closeResource(); detail.value = await api.courseDetail(detail.value.course.id, true); message('资源已删除') } catch (e) { apiMessage(e) } finally { detailLoading.value = false }
}
async function enter(userData: User) {
  user.value = userData; currentUser.value = userData
  profile.name = userData.name; profile.email = userData.email ?? ''; profile.phone = userData.phone ?? ''; profile.jobTitle = userData.jobTitle ?? ''; profile.version = userData.version
  await loadCourses()
}
async function submitLogin() {
  loading.value = true; error.value = ''
  try { await enter(await login(loginForm.employeeNo, loginForm.password)); message('已连接真实后端，欢迎回来') } catch (e) { apiMessage(e) } finally { loading.value = false }
}
async function autoLogin() {
  try { await refresh(); if (currentUser.value) await enter(currentUser.value) } catch { /* anonymous is the expected first state */ }
  booting.value = false
}
async function submitProfile() {
  loading.value = true; error.value = ''
  try { const updated = await api.updateProfile(profile); await enter(updated); message('个人资料已保存') } catch (e) { apiMessage(e) } finally { loading.value = false }
}
async function submitPassword() {
  loading.value = true; error.value = ''
  try { await api.changePassword(password.current, password.next); password.current = ''; password.next = ''; message('密码已修改，现有会话已注销，请重新登录') ; await logout(); user.value = null } catch (e) { apiMessage(e) } finally { loading.value = false }
}
function newCourse() { editing.value = null; editorOpen.value = true; Object.assign(courseForm, { title: '', summary: '', category: '', difficulty: 'BEGINNER', status: 'DRAFT', durationMinutes: 30, version: 0 }) }
function editCourse(item: Course) { editing.value = item; editorOpen.value = true; Object.assign(courseForm, { title: item.title, summary: item.summary ?? '', category: item.category ?? '', difficulty: item.difficulty, status: item.status, durationMinutes: item.durationMinutes, version: item.version }) }
async function saveCourse() {
  loading.value = true; error.value = ''
  try { if (editing.value) await api.updateCourse(editing.value.id, courseForm); else await api.createCourse(courseForm); await loadCourses(); editing.value = null; editorOpen.value = false; message('课程信息已保存') } catch (e) { apiMessage(e) } finally { loading.value = false }
}
async function signOut() { await logout(); closeResource(); user.value = null; courses.value = []; detail.value = null; view.value = 'overview'; message('已退出联调工作区') }

onMounted(async () => { try { await csrf() } catch { /* backend may be offline */ } await autoLogin() })
watch(currentUser, value => { user.value = value; if (!value) { closeResource(); view.value = 'overview' } })
onUnmounted(closeResource)
</script>

<template>
  <div class="connected-root">
    <div v-if="booting" class="boot-screen"><div class="logo-mark">知</div><p>正在连接后端服务…</p></div>
    <section v-else-if="!loggedIn" class="login-layout">
      <div class="login-intro"><div class="brand-line"><span class="logo-mark">知</span><span>ZHIXING / API WORKSPACE</span></div><h1>真实后端联调工作区</h1><p>这里的数据来自 Spring Boot API，不使用前端演示数据。登录后可验证权限、课程学习进度、资源上传/播放、题库与考试、培训计划、培训档案、结业证书及个人资料更新。</p><div class="boundary-note"><b>当前边界</b><span>已接入：登录、课程目录、课程详情、报名、章节进度、资源上传/播放、题库与考试、培训计划、培训档案与数据分析、结业证书、个人资料</span><span>后续切片：证书打印模板与更细粒度统计图表</span></div></div>
      <form class="login-card" @submit.prevent="submitLogin"><span class="eyebrow">SECURE SIGN IN</span><h2>登录联调环境</h2><p class="muted">使用工号登录，服务端负责校验角色与会话。</p><label>工号<input v-model="loginForm.employeeNo" autocomplete="username" placeholder="例如 E20260001" /></label><label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label><p v-if="error" class="error-text">{{ error }}</p><button class="primary-button" :disabled="loading">{{ loading ? '连接中…' : '登录并开始联调' }}</button><div class="login-hint">本地默认账号：E20260001 / Zhixing@123<br />管理员：ADMIN001 / Zhixing@123</div><a class="back-link" href="/">← 返回员工学习平台</a></form>
    </section>
    <template v-else>
<header class="connected-header"><a class="brand-line" href="/connected.html"><span class="logo-mark">知</span><span>ZHIXING / API WORKSPACE</span></a><nav><button :class="{active: view === 'overview'}" @click="view = 'overview'">概览</button><button :class="{active: view === 'courses'}" @click="view = 'courses'; loadCourses()">{{ isAdmin ? '课程管理' : '课程目录' }}</button><button :class="{active: view === 'assessment'}" @click="view = 'assessment'">{{ isAdmin ? '题库与试卷' : '考试' }}</button><button :class="{active: view === 'plans'}" @click="view = 'plans'">{{ isAdmin ? '培训计划' : '我的计划' }}</button><button :class="{active: view === 'archive'}" @click="view = 'archive'">{{ isAdmin ? '数据分析' : '培训档案' }}</button><button :class="{active: view === 'certificates'}" @click="view = 'certificates'">{{ isAdmin ? '结业证书' : '我的证书' }}</button><button v-if="isAdmin" :class="{active: view === 'organization'}" @click="view = 'organization'">组织与员工</button><button :class="{active: view === 'profile'}" @click="view = 'profile'">个人资料</button></nav><div class="header-user"><span class="status-dot"></span><span>{{ user?.name }} · {{ isAdmin ? '管理员' : '员工' }}</span><button class="text-button" @click="signOut">退出</button></div></header>
      <main class="connected-main">
        <div v-if="notice" class="toast">{{ notice }}</div>
        <div v-if="error" class="api-alert"><b>接口请求未完成</b><span>{{ error }}</span><button class="text-button" @click="error = ''; loadCourses()">重试</button></div>
        <section v-if="view === 'overview'" class="page-section"><div class="section-heading"><div><span class="eyebrow">CONNECTED MODE</span><h1>{{ isAdmin ? '管理联调概览' : '我的学习入口' }}</h1><p>当前身份由服务端 JWT 会话决定，页面不会切换或伪造角色。</p></div><span class="live-badge"><i></i> API 在线</span></div><div class="stat-grid"><article><span>当前身份</span><strong>{{ user?.roleCode }}</strong><small>{{ user?.employeeNo }} · {{ user?.departmentName || '未分配部门' }}</small></article><article><span>{{ isAdmin ? '课程总数' : '可学习课程' }}</span><strong>{{ total }}</strong><small>来自课程接口实时分页结果</small></article><article><span>访问令牌</span><strong>15 min</strong><small>短时令牌 + HttpOnly 刷新 Cookie</small></article></div><div class="next-card"><div><span class="eyebrow">NEXT SLICE</span><h2>培训业务持续完善中</h2><p>课程、考试、培训计划、培训档案和结业证书已经可以真实联调；证书打印模板和更细粒度统计图表会继续接入。</p></div><button class="outline-button" @click="view = 'certificates'">查看{{ isAdmin ? '结业证书' : '我的证书' }}</button></div></section>
        <section v-else-if="view === 'courses'" class="page-section"><div class="section-heading"><div><span class="eyebrow">COURSES API</span><h1>{{ isAdmin ? '课程管理' : '课程目录' }}</h1><p>{{ isAdmin ? '草稿和已发布课程均来自服务端，可验证乐观锁版本。' : '员工仅能读取已发布课程。' }}</p></div><button v-if="isAdmin" class="primary-button compact" @click="newCourse">新建课程</button></div><div class="toolbar"><input v-model="keyword" placeholder="搜索课程标题或简介" @keyup.enter="loadCourses" /><button class="outline-button" @click="loadCourses">查询</button><span>共 {{ total }} 条</span></div><div v-if="loading" class="empty-state">正在读取课程接口…</div><div v-else-if="!courses.length" class="empty-state">暂无符合条件的课程</div><div v-else class="course-grid"><article v-for="course in courses" :key="course.id" class="course-card"><div class="course-cover"><span>{{ course.category || '培训课程' }}</span><b>{{ course.status === 'PUBLISHED' ? '已发布' : '草稿' }}</b></div><div class="course-body"><span class="course-meta">{{ course.difficulty }} · {{ course.durationMinutes }} 分钟</span><h3>{{ course.title }}</h3><p>{{ course.summary || '暂无课程简介' }}</p><div class="course-footer"><span>{{ course.chapterCount }} 个章节 · v{{ course.version }}</span><span class="course-actions"><button class="text-button" @click="openCourse(course.id)">查看详情</button><button v-if="isAdmin" class="text-button" @click="editCourse(course)">编辑</button></span></div></div></article></div><div v-if="detail" class="detail-panel"><div class="panel-heading"><div><span class="eyebrow">LEARNING DETAIL</span><h2>{{ detail.course.title }}</h2></div><button class="text-button" @click="detail = null">关闭</button></div><p class="detail-summary">{{ detail.course.summary || '暂无课程简介' }}</p><div class="progress-summary"><div><span>{{ isAdmin ? '课程完成概览' : '我的学习进度' }}</span><strong>{{ detail.progressPercent }}%</strong></div><div class="progress-track"><i :style="{ width: `${detail.progressPercent}%` }"></i></div><small>已完成 {{ detail.completedChapters }} / {{ detail.chapters.length }} 个章节</small></div><button v-if="!isAdmin && !detail.enrolled" class="primary-button compact" :disabled="detailLoading" @click="enrollCourse">报名并开始学习</button><span v-else-if="!isAdmin" class="enrolled-badge">已报名 · 可继续学习</span><span v-else class="enrolled-badge">管理员查看模式</span><div class="chapter-list"><article v-for="chapter in detail.chapters" :key="chapter.id" class="chapter-row"><div class="chapter-content"><div class="chapter-heading"><span class="chapter-index">{{ String(chapter.sortNo).padStart(2, '0') }}</span><div><h3>{{ chapter.title }}</h3><small>{{ chapter.durationMinutes }} 分钟 · {{ chapter.resources.length }} 个学习资源</small></div></div><div v-if="chapter.resources.length" class="resource-list"><button v-for="resource in chapter.resources" :key="resource.id" class="resource-chip" @click="openResource(resource)"><span>{{ resource.resourceType }}</span>{{ resource.title }}</button></div><div v-if="isAdmin" class="resource-upload"><input type="file" :disabled="detailLoading" @change="selectUploadFile($event, chapter.id)" /><input v-if="uploadDraft.chapterId === chapter.id" v-model="uploadDraft.title" placeholder="资源名称（可选）" /><button class="outline-button compact" :disabled="detailLoading || uploadDraft.chapterId !== chapter.id || !uploadDraft.file" @click="uploadForChapter(chapter.id)">上传资源</button><button v-for="resource in chapter.resources" :key="`delete-${resource.id}`" class="resource-delete" :disabled="detailLoading" @click="removeResource(resource)">删除 {{ resource.title }}</button></div></div><button v-if="!isAdmin" class="outline-button compact" :disabled="detailLoading || !detail.enrolled" @click="finishChapter(chapter.id)">{{ detail.progressPercent === 100 && detail.completedChapters === detail.chapters.length ? '已完成' : '完成章节' }}</button></article></div><div v-if="resourceLoading" class="resource-viewer loading-viewer">正在读取资源…</div><div v-else-if="activeResource && activeResourceUrl" class="resource-viewer"><div class="viewer-heading"><div><span class="eyebrow">RESOURCE PREVIEW</span><strong>{{ activeResource.title }}</strong></div><button class="text-button" @click="closeResource">关闭预览</button></div><video v-if="activeResource.resourceType === 'VIDEO'" :src="activeResourceUrl" controls playsinline></video><iframe v-else-if="activeResource.resourceType === 'PDF'" :src="activeResourceUrl" title="PDF 预览"></iframe><img v-else-if="activeResource.resourceType === 'IMAGE'" :src="activeResourceUrl" :alt="activeResource.title" /><div v-else class="attachment-viewer"><p>该资源类型不支持直接预览，可下载原文件。</p><a class="outline-button" :href="activeResourceUrl" :download="activeResource.originalName || activeResource.title">下载资源</a></div></div></div><div v-if="editorOpen" class="editor-panel"><div class="panel-heading"><h2>{{ editing ? '编辑课程' : '新建课程' }}</h2><button class="text-button" @click="editorOpen = false">关闭</button></div><div class="form-grid"><label>课程标题<input v-model="courseForm.title" /></label><label>分类<input v-model="courseForm.category" /></label><label class="wide">简介<textarea v-model="courseForm.summary" rows="3"></textarea></label><label>时长（分钟）<input v-model.number="courseForm.durationMinutes" type="number" min="1" /></label><label>状态<select v-model="courseForm.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">发布</option></select></label></div><button class="primary-button" :disabled="loading" @click="saveCourse">保存课程</button></div></section>
        <AssessmentPanel v-else-if="view === 'assessment'" :admin="isAdmin" />
        <PlanPanel v-else-if="view === 'plans'" :admin="isAdmin" />
        <ArchivePanel v-else-if="view === 'archive'" :admin="isAdmin" />
        <OrganizationPanel v-else-if="view === 'organization' && isAdmin" />
        <CertificatePanel v-else-if="view === 'certificates'" :admin="isAdmin" />
        <section v-else class="page-section"><div class="section-heading"><div><span class="eyebrow">ACCOUNT / PROFILE</span><h1>个人资料</h1><p>更新会携带版本号，发生并发修改时服务端返回 409。</p></div></div><div class="profile-grid"><form class="panel" @submit.prevent="submitProfile"><h2>基本资料</h2><label>姓名<input v-model="profile.name" /></label><label>岗位<input v-model="profile.jobTitle" /></label><label>邮箱<input v-model="profile.email" type="email" /></label><label>手机号<input v-model="profile.phone" /></label><button class="primary-button" :disabled="loading">保存资料</button></form><form class="panel" @submit.prevent="submitPassword"><h2>修改密码</h2><p class="muted">修改后所有刷新会话会被服务端撤销。</p><label>当前密码<input v-model="password.current" type="password" /></label><label>新密码<input v-model="password.next" type="password" placeholder="至少 8 位，含字母和数字" /></label><button class="outline-button" :disabled="loading">修改密码</button></form></div></section>
      </main>
    </template>
  </div>
</template>
