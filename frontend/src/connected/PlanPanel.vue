<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { RouterLink } from 'vue-router'
  import { ApiError, api, type Course, type Exam, type PlanDetail, type TrainingPlan } from './api'

  const props = defineProps<{ admin: boolean; employeeLinks?: boolean }>()
  const plans = ref<TrainingPlan[]>([])
  const courses = ref<Course[]>([])
  const exams = ref<Exam[]>([])
  const employees = ref<
    Array<{ id: number; employeeNo: string; name: string; departmentName?: string }>
  >([])
  const selectedEmployees = ref<number[]>([])
  const selected = ref<PlanDetail | null>(null)
  const loading = ref(false)
  const error = ref('')
  const notice = ref('')
  const form = reactive({
    title: '新员工综合培训计划',
    description: '完成课程学习并通过对应考试。',
    category: '入职培训',
    startDate: '',
    endDate: '',
    courseId: 0,
    examId: 0,
    courseRequired: true,
    examRequired: true
  })

  function message(text: string) {
    notice.value = text
    window.setTimeout(() => {
      if (notice.value === text) notice.value = ''
    }, 3000)
  }
  function apiMessage(e: unknown) {
    error.value = e instanceof ApiError ? e.message : '培训计划接口暂时不可用，请确认后端已启动'
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      plans.value = await api.plans(props.admin)
      if (props.admin) {
        employees.value = await api.eligibleEmployees()
        courses.value = (await api.courses(true)).list
        exams.value = await api.exams(true)
        form.courseId ||= courses.value.find((item) => item.status === 'PUBLISHED')?.id ?? 0
        form.examId ||= exams.value.find((item) => item.status === 'PUBLISHED')?.id ?? 0
      }
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function create() {
    if (!form.courseId && !form.examId) {
      error.value = '至少选择一门已发布课程或一场已发布考试'
      return
    }
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.createPlan({
        title: form.title,
        description: form.description,
        category: form.category,
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
        courses: form.courseId ? [{ courseId: form.courseId, required: form.courseRequired }] : [],
        exams: form.examId ? [{ examId: form.examId, required: form.examRequired }] : []
      })
      await load()
      message('培训计划草稿已创建')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function publish(plan: TrainingPlan) {
    if (!selectedEmployees.value.length) {
      error.value = '请在目标员工名单中选择至少一名员工'
      return
    }
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.publishPlan(plan.id, selectedEmployees.value)
      await load()
      message('培训计划已发布并分配给员工')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function open(plan: TrainingPlan) {
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.planDetail(plan.id, props.admin)
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function refreshProgress(plan: TrainingPlan) {
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.refreshPlan(plan.id)
      plans.value = await api.plans(false)
      message('计划进度已刷新')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  onMounted(load)
</script>

<template>
  <section class="page-section plan-panel"
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">TRAINING / MY PLAN</span
        ><h1>{{ admin ? '培训计划' : '我的培训计划' }}</h1
        ><p>{{
          admin
            ? '把已发布课程和考试组合为一个可追踪的培训任务。'
            : '完成全部必修课程和考试后自动结业，选修项目不影响结业。'
        }}</p></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新数据</button></div
    >
    <div v-if="notice" class="toast">{{ notice }}</div
    ><div v-if="error" class="api-alert"
      ><b>培训计划接口</b><span>{{ error }}</span
      ><button class="text-button" @click="error = ''">关闭</button></div
    >
    <div v-if="loading && !plans.length" class="empty-state">正在读取培训计划…</div>
    <div v-if="admin" class="panel roster-selector"
      ><h2>发布目标名单</h2
      ><p class="muted">发布草稿前选择目标员工，发布后名单固化；重复选择不会重复分配。</p
      ><label v-for="employee in employees" :key="employee.id" class="option-line"
        ><input v-model="selectedEmployees" type="checkbox" :value="employee.id" />{{
          employee.name
        }}
        · {{ employee.employeeNo }} · {{ employee.departmentName || '未分配部门' }}</label
      ><p>已选择 {{ selectedEmployees.length }} 人</p></div
    >
    <template v-if="!loading || plans.length">
      <div class="plan-list"
        ><article v-for="plan in plans" :key="plan.id" class="plan-card"
          ><div
            ><span class="course-meta"
              >{{ plan.category }} · {{ plan.startDate }} 至 {{ plan.endDate }}</span
            ><h3>{{ plan.title }}</h3
            ><p>{{ plan.description || '暂无计划说明' }}</p
            ><div class="progress-track"><i :style="{ width: `${plan.progressPercent}%` }"></i></div
            ><small
              >{{ plan.completedItems }} / {{ plan.totalItems }} 必修项完成 ·
              {{ plan.progressPercent }}%</small
            ></div
          ><div class="plan-card-actions"
            ><span class="status-pill">{{
              plan.status === 'PUBLISHED'
                ? plan.progressPercent === 100
                  ? '已完成'
                  : '进行中'
                : '草稿'
            }}</span
            ><button
              v-if="admin && plan.status === 'DRAFT'"
              class="text-button"
              @click="publish(plan)"
              >发布</button
            ><button class="text-button" @click="open(plan)">查看</button
            ><button v-if="!admin" class="outline-button compact" @click="refreshProgress(plan)"
              >刷新进度</button
            ></div
          ></article
        ><p v-if="!plans.length" class="empty-state">暂无培训计划</p></div
      >
      <form v-if="admin" class="panel plan-form" @submit.prevent="create"
        ><div class="panel-heading"><h2>创建计划草稿</h2></div
        ><div class="form-grid"
          ><label>计划名称<input v-model="form.title" required /></label
          ><label>分类<input v-model="form.category" /></label
          ><label>开始日期<input v-model="form.startDate" type="date" /></label
          ><label>结束日期<input v-model="form.endDate" type="date" /></label
          ><label
            >绑定课程<select v-model.number="form.courseId"
              ><option :value="0">不绑定课程</option
              ><option
                v-for="course in courses.filter((item) => item.status === 'PUBLISHED')"
                :key="course.id"
                :value="course.id"
                >{{ course.title }}</option
              ></select
            ></label
          ><label
            >绑定考试<select v-model.number="form.examId"
              ><option :value="0">不绑定考试</option
              ><option
                v-for="exam in exams.filter((item) => item.status === 'PUBLISHED')"
                :key="exam.id"
                :value="exam.id"
                >{{ exam.title }}</option
              ></select
            ></label
          ><label class="option-line"
            ><input v-model="form.courseRequired" type="checkbox" />所选课程为必修</label
          ><label class="option-line"
            ><input v-model="form.examRequired" type="checkbox" />所选考试为必修</label
          ><label class="wide"
            >说明<textarea v-model="form.description" rows="2"></textarea></label></div
        ><button class="primary-button compact" :disabled="loading">创建计划</button></form
      >
    </template>
    <div v-if="selected" class="detail-panel plan-detail"
      ><div class="panel-heading"
        ><h2>{{ selected.plan.title }}</h2
        ><button class="text-button" @click="selected = null">关闭</button></div
      ><p class="muted"
        >{{ selected.plan.description }} · {{ selected.plan.progressPercent }}% 完成</p
      ><div class="plan-items"
        ><article v-for="item in selected.items" :key="`${item.itemType}-${item.itemId}`"
          ><span class="status-pill">{{ item.itemType === 'COURSE' ? '课程' : '考试' }}</span
          ><strong>{{ item.title }}</strong
          ><router-link
            v-if="employeeLinks"
            class="text-button"
            :to="item.itemType === 'COURSE' ? `/learn/${item.itemId}` : '/exams'"
            >{{ item.itemType === 'COURSE' ? '前往学习' : '前往考试' }}</router-link
          ><small
            >{{ item.required ? '必修' : '选修' }} ·
            {{ item.status === 'COMPLETED' ? '已完成' : `${item.progressPercent}%` }}</small
          ></article
        ></div
      ></div
    >
  </section>
</template>
