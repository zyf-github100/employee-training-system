<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { api, type Course } from '../../../connected/api'
  import { errorMessage } from '../session'
  const courses = ref<Course[]>([]),
    total = ref(0),
    page = ref(1),
    keyword = ref('')
  const loading = ref(false),
    saving = ref(false),
    error = ref(''),
    editor = ref(false)
  const editingId = ref<number | null>(null)
  const fresh = () => ({
    title: '',
    summary: '',
    category: '',
    difficulty: 'BEGINNER',
    durationMinutes: 30,
    coverUrl: '',
    status: 'DRAFT',
    version: 0
  })
  const form = reactive(fresh())
  let request = 0
  async function load() {
    const ticket = ++request
    loading.value = true
    error.value = ''
    try {
      const data = await api.courses(true, page.value, 12, keyword.value.trim())
      if (ticket !== request) return
      courses.value = data.list
      total.value = data.total
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
    }
  }
  function search() {
    page.value = 1
    void load()
  }
  function changePage(delta: number) {
    page.value += delta
    void load()
  }
  function edit(course?: Course) {
    editingId.value = course?.id ?? null
    Object.assign(
      form,
      fresh(),
      course
        ? {
            ...course,
            summary: course.summary ?? '',
            category: course.category ?? '',
            coverUrl: course.coverUrl ?? ''
          }
        : {}
    )
    error.value = ''
    editor.value = true
  }
  async function save() {
    if (saving.value) return
    saving.value = true
    try {
      if (editingId.value) await api.updateCourse(editingId.value, form)
      else await api.createCourse(form)
      editor.value = false
      await load()
      ElMessage.success('课程已保存')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  async function changeStatus(course: Course) {
    const publish = course.status !== 'PUBLISHED'
    try {
      await ElMessageBox.confirm(
        publish
          ? `发布“${course.title}”后，员工可浏览并报名学习。`
          : `将“${course.title}”转为草稿后，员工将无法继续访问该课程及资源。`,
        publish ? '发布课程' : '转为草稿',
        { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    saving.value = true
    try {
      if (publish) await api.publishCourse(course.id, course.version, '管理员发布')
      else await api.updateCourse(course.id, { ...course, status: 'DRAFT' })
      await load()
      ElMessage.success(publish ? '课程已发布' : '已转为草稿')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section>
    <div class="section-heading"
      ><div
        ><span class="eyebrow">COURSE LIBRARY</span><h1>课程管理</h1
        ><p>建设团队的知识库，让每一门课程都有清晰的学习路径。</p></div
      ><button class="primary-button" @click="edit()"
        ><AppIcon name="plus" :size="16" />新建课程</button
      ></div
    >
    <form class="toolbar admin-toolbar" @submit.prevent="search"
      ><input v-model="keyword" aria-label="搜索课程" placeholder="搜索课程名称或简介" /><button
        class="outline-button"
        :disabled="loading"
        >搜索</button
      ><button type="button" class="text-button" :disabled="loading" @click="load">刷新</button
      ><span>共 {{ total }} 门课程</span></form
    >
    <div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    >
    <div v-if="loading" class="empty-state" role="status">正在读取课程…</div>
    <div v-else-if="!courses.length" class="panel empty-state">{{
      keyword ? '没有找到符合条件的课程，请调整关键词。' : '还没有课程，点击“新建课程”开始建设。'
    }}</div>
    <div v-else class="admin-course-grid">
      <article v-for="(course, index) in courses" :key="course.id" class="admin-course-card"
        ><div class="admin-course-cover" :class="`tone-${index % 4}`"
          ><span>{{ course.category || '通用培训' }}</span
          ><AppIcon name="book" :size="54" /><span
            class="status-pill"
            :class="{ draft: course.status !== 'PUBLISHED' }"
            >{{ course.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span
          ></div
        ><div class="admin-course-body"
          ><router-link :to="`/admin/courses/${course.id}`"
            ><h2>{{ course.title }}</h2></router-link
          ><p>{{ course.summary || '暂未填写课程简介' }}</p
          ><div class="admin-course-meta"
            ><span>{{ course.chapterCount }} 个章节</span
            ><span>{{ course.durationMinutes }} 分钟</span></div
          ><div class="admin-course-actions"
            ><router-link class="text-button" :to="`/admin/courses/${course.id}`"
              >章节与资源 →</router-link
            ><button class="text-button" :disabled="saving" @click="edit(course)">编辑</button
            ><button class="text-button" :disabled="saving" @click="changeStatus(course)">{{
              course.status === 'PUBLISHED' ? '转为草稿' : '发布'
            }}</button></div
          ></div
        ></article
      >
    </div>
    <div v-if="total > 12" class="live-pagination"
      ><button class="outline-button" :disabled="page <= 1 || loading" @click="changePage(-1)"
        >上一页</button
      ><span>{{ page }} / {{ Math.ceil(total / 12) }}</span
      ><button
        class="outline-button"
        :disabled="page * 12 >= total || loading"
        @click="changePage(1)"
        >下一页</button
      ></div
    >
    <el-dialog
      v-model="editor"
      :title="editingId ? '编辑课程' : '新建课程'"
      width="620px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><form class="admin-dialog-form" @submit.prevent="save"
        ><label
          >课程名称<input
            v-model.trim="form.title"
            required
            maxlength="160"
            placeholder="例如：新员工安全与合规" /></label
        ><div class="admin-form-grid"
          ><label
            >课程分类<input
              v-model.trim="form.category"
              maxlength="80"
              placeholder="例如：入职培训" /></label
          ><label
            >难度<select v-model="form.difficulty"
              ><option value="BEGINNER">基础</option
              ><option value="INTERMEDIATE">进阶</option
              ><option value="ADVANCED">高级</option></select
            ></label
          ><label
            >预计时长（分钟）<input
              v-model.number="form.durationMinutes"
              type="number"
              min="0"
              max="100000"
              required /></label></div
        ><label
          >课程简介<textarea
            v-model.trim="form.summary"
            maxlength="500"
            rows="4"
            placeholder="说明学习目标与适用人群"
          /></label
        ><p class="muted">新课程保存为草稿，完善章节与资源后可发布。</p
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="saving">{{
            saving ? '保存中…' : '保存课程'
          }}</button></div
        ></form
      ></el-dialog
    >
  </section>
</template>
