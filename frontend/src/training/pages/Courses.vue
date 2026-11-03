<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import type { Course, CourseCategory } from '../types'
  import { createChapters, curriculumError } from '../services/curriculum'
  const s = useTraining(),
    route = useRoute(),
    router = useRouter()
  const keyword = ref(''),
    category = ref('全部课程'),
    status = ref('全部状态'),
    mode = ref('grid'),
    page = ref(1),
    editor = ref(false),
    editId = ref<number | null>(null)
  const categories = ['全部课程', '通用能力', '专业技能', '管理能力', '合规安全', '新人必修']
  const filtered = computed(() =>
    s.courseViews.filter(
      (c) =>
        (s.isAdmin || c.status === '已发布') &&
        (category.value === '全部课程' || c.category === category.value) &&
        (status.value === '全部状态' || c.status === status.value) &&
        [c.title, c.instructor, c.subtitle]
          .join('')
          .toLowerCase()
          .includes(keyword.value.trim().toLowerCase())
    )
  )
  const visible = computed(() => filtered.value.slice((page.value - 1) * 9, page.value * 9))
  watch([keyword, category, status], () => (page.value = 1))
  const form = reactive({
    title: '',
    subtitle: '',
    category: '通用能力' as CourseCategory,
    instructor: '',
    duration: 60,
    lessons: 6,
    required: false,
    description: '',
    status: '草稿' as Course['status'],
    theme: 'communication'
  })
  function openEditor(c?: Course) {
    editId.value = c?.id ?? null
    Object.assign(
      form,
      c
        ? { ...c }
        : {
            title: '',
            subtitle: '',
            category: '通用能力',
            instructor: '',
            duration: 60,
            lessons: 6,
            required: false,
            description: '',
            status: '草稿',
            theme: 'communication'
          }
    )
    editor.value = true
  }
  function save() {
    if (!form.title.trim() || !form.instructor.trim()) {
      ElMessage.warning('请填写课程名称和讲师')
      return
    }
    if (form.status === '已发布' && editId.value) {
      const c = s.data.courses.find((c) => c.id === editId.value)
      const error = curriculumError(c?.chapters || [], true)
      if (error) {
        ElMessage.warning(error)
        return
      }
    }
    if (
      !Number.isInteger(form.duration) ||
      !Number.isInteger(form.lessons) ||
      form.duration < 1 ||
      form.lessons < 1 ||
      form.lessons > 30
    ) {
      ElMessage.warning('请填写有效的时长与课时')
      return
    }
    if (!editId.value && (form.duration < form.lessons || form.duration > form.lessons * 240)) {
      ElMessage.warning('总时长需能分配为每章 1–240 分钟，请调整时长或章节数')
      return
    }
    if (editId.value) {
      const c = s.data.courses.find((c) => c.id === editId.value)
      if (c)
        Object.assign(c, { ...form, title: form.title.trim(), instructor: form.instructor.trim() })
    } else {
      const created: Course = {
        ...form,
        id: Date.now(),
        title: form.title.trim(),
        instructor: form.instructor.trim(),
        learners: 0,
        rating: 0,
        progress: 0
      }
      created.chapters = createChapters(created)
      s.data.courses.unshift(created)
      router.push('/courses/' + created.id + '/manage')
      keyword.value = ''
      category.value = '全部课程'
      status.value = '全部状态'
      page.value = 1
    }
    editor.value = false
    ElMessage.success(editId.value ? '课程已更新' : '课程已创建')
  }
  async function setStatus() {
    const c = s.data.courses.find((c) => c.id === editId.value)
    if (!c) return
    try {
      await ElMessageBox.confirm(
        c.status === '已发布' ? '下架后员工端将不再展示此课程。' : '将课程发布到员工学习中心？',
        '课程状态',
        { confirmButtonText: '确认', cancelButtonText: '取消' }
      )
      form.status = c.status === '已发布' ? '已下架' : '已发布'
      save()
    } catch {}
  }
  watch(
    () => route.query.edit,
    (value) => {
      const c = s.data.courses.find((c) => c.id === Number(value))
      if (value && c && s.isAdmin) openEditor(c)
    },
    { immediate: true }
  )
  watch(
    () => route.query.create,
    (v) => {
      if (v && s.isAdmin) {
        openEditor()
        router.replace('/courses')
      }
    },
    { immediate: true }
  )
</script>
<template>
  <div
    ><PageTitle
      title="课程中心"
      :description="
        s.isAdmin
          ? '连接优质内容与学习者，构建企业自己的知识资源库。'
          : '查看已发布课程，按培训计划完成学习。'
      "
      ><el-button v-if="s.isAdmin" type="primary" @click="openEditor()"
        ><AppIcon name="plus" :size="16" />新建课程</el-button
      ></PageTitle
    >
    <div v-if="s.isAdmin" class="course-library-banner"
      ><div
        ><span class="eyebrow">EXPLORE · LEARN · GROW</span><h2>知识有光，成长有方向</h2
        ><p>精选企业课程，陪伴每一个向前的你。</p></div
      ><div class="library-count"
        ><strong>{{ s.published.length }}</strong
        ><span>门精选课程</span></div
      ><div class="library-count"><strong>5</strong><span>大能力领域</span></div
      ><div class="library-symbol"><AppIcon name="collection" :size="72" /></div
    ></div>
    <section v-else class="employee-course-summary"
      ><div><span class="eyebrow">课程中心</span><h2>按岗位和培训计划完成学习</h2><p>已报名课程会保留学习进度，完成后可在培训档案中查看记录。</p></div
      ><div class="course-summary-metric"><b>{{ s.published.length }}</b><span>可学习课程</span></div
      ><div class="course-summary-metric"><b>{{ s.published.filter((c) => c.progress === 100).length }}</b><span>已完成</span></div
      ><div class="course-summary-metric"><b>{{ s.published.filter((c) => c.progress > 0 && c.progress < 100).length }}</b><span>学习中</span></div
    ></section>
    <section class="panel course-toolbar"
      ><div class="category-tabs" role="tablist" aria-label="课程分类"
        ><button
          v-for="c in categories"
          :key="c"
          role="tab"
          :aria-selected="category === c"
          :class="{ active: category === c }"
          @click="category = c"
          >{{ c
          }}<span v-if="c === '全部课程'">{{
            s.isAdmin ? s.data.courses.length : s.published.length
          }}</span></button
        ></div
      ><div class="filter-row"
        ><el-input
          v-model="keyword"
          placeholder="搜索课程名称、关键词或讲师"
          clearable
          class="search-input"
          ><template #prefix><AppIcon name="search" :size="17" /></template></el-input
        ><el-select v-if="s.isAdmin" v-model="status" aria-label="课程发布状态" style="width: 140px"
          ><el-option
            v-for="v in ['全部状态', '已发布', '草稿', '已下架']"
            :key="v"
            :value="v"
            :label="v" /></el-select
        ><span class="filter-result"
          >共 <b>{{ filtered.length }}</b> 门课程</span
        ><div class="view-toggle"
          ><button :class="{ active: mode === 'grid' }" aria-label="卡片视图" @click="mode = 'grid'"
            ><AppIcon name="grid" :size="17" /></button
          ><button :class="{ active: mode === 'list' }" aria-label="列表视图" @click="mode = 'list'"
            ><AppIcon name="list" :size="19" /></button></div></div
    ></section>
    <div v-if="mode === 'grid'" class="course-grid"
      ><CourseCard
        v-for="c in visible"
        :key="c.id"
        :course="c"
        :employee="!s.isAdmin"
        @open="router.push('/learn/' + c.id)"
        @edit="(c) => router.push('/courses/' + c.id + '/manage')" /></div
    ><section v-else class="panel table-scroll"
      ><table class="data-table"
        ><thead
          ><tr
            ><th>课程名称</th><th>分类</th><th>讲师</th><th>课时</th
            ><th>{{ s.isAdmin ? '状态' : '学习进度' }}</th
            ><th>操作</th></tr
          ></thead
        ><tbody
          ><tr v-for="c in visible" :key="c.id"
            ><td
              ><b>{{ c.title }}</b
              ><small class="block muted">{{ c.subtitle }}</small></td
            ><td>{{ c.category }}</td
            ><td>{{ c.instructor }}</td
            ><td>{{ c.lessons }} 课时</td
            ><td
              ><StatusBadge v-if="s.isAdmin" :status="c.status" /><span v-else
                >{{ c.progress }}%</span
              ></td
            ><td
              ><button
                class="text-link"
                @click="s.isAdmin ? openEditor(c) : router.push('/learn/' + c.id)"
                >{{ s.isAdmin ? '编辑课程' : '开始学习' }}</button
              ></td
            ></tr
          ></tbody
        ></table
      ></section
    >
    <el-empty v-if="!filtered.length" description="没有找到匹配课程，试试其他关键词" /><div
      class="pagination-bar"
      ><span>{{ s.isAdmin ? '课程内容与学习资源统一管理' : '学习进度会自动记录' }}</span
      ><el-pagination
        v-model:current-page="page"
        :page-size="9"
        :total="filtered.length"
        layout="prev, pager, next"
        background
        hide-on-single-page
    /></div>
    <el-dialog
      v-model="editor"
      :title="editId ? '编辑课程' : '创建一门好课程'"
      width="640px"
      class="responsive-dialog"
      ><el-form label-position="top" @submit.prevent="save"
        ><el-form-item label="课程名称 *"
          ><el-input
            v-model="form.title"
            maxlength="60"
            show-word-limit
            placeholder="给课程起一个清晰的名字" /></el-form-item
        ><el-form-item label="一句话介绍"
          ><el-input
            v-model="form.subtitle"
            maxlength="80"
            placeholder="这门课能帮助学员获得什么？" /></el-form-item
        ><div class="form-grid"
          ><el-form-item label="课程分类"
            ><el-select v-model="form.category"
              ><el-option
                v-for="c in categories.slice(1)"
                :key="c"
                :value="c"
                :label="c" /></el-select></el-form-item
          ><el-form-item label="讲师姓名 *"
            ><el-input v-model="form.instructor" placeholder="请输入讲师姓名" /></el-form-item
          ><el-form-item label="学习时长（分钟）"
            ><el-input-number
              v-model="form.duration"
              :disabled="!!editId"
              :min="5"
              :max="1200" /></el-form-item
          ><el-form-item label="课程章节数"
            ><el-input-number
              v-model="form.lessons"
              :disabled="!!editId"
              :min="1"
              :max="30" /></el-form-item></div
        ><el-form-item label="封面风格"
          ><div class="cover-swatches"
            ><button
              v-for="t in ['communication', 'ai', 'leadership', 'security', 'onboarding', 'data']"
              :key="t"
              type="button"
              :class="{ selected: form.theme === t }"
              :aria-label="'封面 ' + t"
              @click="form.theme = t"
              ><CourseCover :theme="t" compact /></button></div></el-form-item
        ><el-form-item label="课程简介"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="1000" /></el-form-item
        ><div class="form-switch"
          ><span>设为必修课程</span><el-switch v-model="form.required" /></div></el-form
      ><template #footer
        ><div class="dialog-footer-split"
          ><button v-if="editId" class="text-link" @click="setStatus">{{
            form.status === '已发布' ? '下架课程' : '发布课程'
          }}</button
          ><span v-else class="muted small">新课程默认保存为草稿</span
          ><div
            ><el-button @click="editor = false">取消</el-button
            ><el-button type="primary" @click="save">{{
              editId ? '保存修改' : '创建课程'
            }}</el-button></div
          ></div
        ></template
      ></el-dialog
    >
  </div>
</template>
