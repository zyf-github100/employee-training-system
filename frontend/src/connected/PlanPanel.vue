<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { ElMessageBox } from 'element-plus'
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
  const employeeSearch = ref(''),
    employeePage = ref(1)
  const filteredEmployees = computed(() => {
    const q = employeeSearch.value.trim().toLowerCase()
    return employees.value.filter((e) =>
      `${e.name} ${e.employeeNo} ${e.departmentName ?? ''}`.toLowerCase().includes(q)
    )
  })
  const pageEmployees = computed(() =>
    filteredEmployees.value.slice((employeePage.value - 1) * 20, employeePage.value * 20)
  )
  watch(employeeSearch, () => (employeePage.value = 1))
  function selectPage() {
    selectedEmployees.value = [
      ...new Set([...selectedEmployees.value, ...pageEmployees.value.map((e) => e.id)])
    ]
  }
  function unselectPage() {
    const ids = new Set(pageEmployees.value.map((e) => e.id))
    selectedEmployees.value = selectedEmployees.value.filter((id) => !ids.has(id))
  }
  const selected = ref<PlanDetail | null>(null)
  const editingId = ref<number | null>(null),
    editingVersion = ref(0)
  const expiring = computed(() =>
    plans.value.filter(
      (plan) =>
        plan.status === 'PUBLISHED' &&
        plan.daysRemaining <= 7 &&
        (props.admin || plan.progressPercent < 100)
    )
  )
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
        const first = await api.courses(false, 1, 100)
        const published = [...first.list]
        for (let page = 2; (page - 1) * 100 < first.total; page++)
          published.push(...(await api.courses(false, page, 100)).list)
        courses.value = published
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
  async function savePlan() {
    if (!form.courseId && !form.examId) {
      error.value = '至少选择一门已发布课程或一场已发布考试'
      return
    }
    loading.value = true
    error.value = ''
    const payload = {
      title: form.title,
      description: form.description,
      category: form.category,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      courses: form.courseId
        ? [{ courseId: form.courseId, required: form.courseRequired }]
        : [],
      exams: form.examId ? [{ examId: form.examId, required: form.examRequired }] : []
    }
    try {
      if (editingId.value) {
        selected.value = await api.updatePlan(editingId.value, {
          ...payload,
          version: editingVersion.value
        })
        editingId.value = null
        editingVersion.value = 0
        message('计划草稿已保存')
      } else {
        selected.value = await api.createPlan(payload)
        message('培训计划草稿已创建')
      }
      await load()
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function startEdit(plan: TrainingPlan) {
    loading.value = true
    error.value = ''
    try {
      const detail = await api.planDetail(plan.id, true)
      const courseItems = detail.items.filter((item) => item.itemType === 'COURSE')
      const examItems = detail.items.filter((item) => item.itemType === 'EXAM')
      if (courseItems.length > 1 || examItems.length > 1) {
        error.value =
          '该计划包含多门课程或多场考试，当前编辑表单只支持一门课程 + 一场考试；请新建计划或通过接口调整。'
        return
      }
      Object.assign(form, {
        title: detail.plan.title,
        description: detail.plan.description ?? '',
        category: detail.plan.category,
        startDate: detail.plan.startDate,
        endDate: detail.plan.endDate,
        courseId: courseItems[0]?.itemId ?? 0,
        examId: examItems[0]?.itemId ?? 0,
        courseRequired: courseItems[0]?.required ?? true,
        examRequired: examItems[0]?.required ?? true
      })
      editingId.value = detail.plan.id
      editingVersion.value = detail.plan.version
      selected.value = detail
      message('已载入草稿，修改后保存')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  function resetForm() {
    Object.assign(form, {
      title: '新员工综合培训计划',
      description: '完成课程学习并通过对应考试。',
      category: '入职培训',
      startDate: '',
      endDate: '',
      courseId: courses.value.find((item) => item.status === 'PUBLISHED')?.id ?? 0,
      examId: exams.value.find((item) => item.status === 'PUBLISHED')?.id ?? 0,
      courseRequired: true,
      examRequired: true
    })
    editingId.value = null
    editingVersion.value = 0
  }
  function cancelEdit() {
    resetForm()
    message('已取消编辑')
  }
  function statusLabel(plan: TrainingPlan) {
    if (plan.status === 'TERMINATED') return '已终止'
    if (plan.status === 'DRAFT') return '草稿'
    if (!props.admin) return plan.progressPercent === 100 ? '已完成' : '进行中'
    return '已发布'
  }
  function expiryLabel(plan: TrainingPlan) {
    if (plan.status !== 'PUBLISHED' || plan.daysRemaining > 7) return ''
    if (plan.daysRemaining < 0) return `已逾期 ${Math.abs(plan.daysRemaining)} 天`
    if (plan.daysRemaining === 0) return '今天到期'
    return `剩余 ${plan.daysRemaining} 天`
  }
  async function terminate(plan: TrainingPlan) {
    let reason = ''
    try {
      const result = await ElMessageBox.prompt(
        '员工会看到终止原因；终止后该计划不能再推进或刷新进度。',
        `终止「${plan.title}」`,
        {
          confirmButtonText: '确认终止',
          cancelButtonText: '取消',
          type: 'warning',
          inputType: 'textarea',
          inputPlaceholder: '例如：培训需求取消',
          inputValidator: (value: string) => (value && value.trim() ? true : '请填写终止原因')
        }
      )
      reason = String(result.value ?? '').trim()
    } catch {
      return
    }
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.terminatePlan(plan.id, reason, plan.version)
      await load()
      message('培训计划已终止')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function extend(plan: TrainingPlan) {
    let endDate = ''
    try {
      const result = await ElMessageBox.prompt(
        '新的结束日期（YYYY-MM-DD）。考试截止日期需在考试管理中单独调整。',
        `延期「${plan.title}」`,
        {
          confirmButtonText: '确认延期',
          cancelButtonText: '取消',
          inputValue: plan.endDate,
          inputPattern: /^\d{4}-\d{2}-\d{2}$/,
          inputErrorMessage: '请输入 YYYY-MM-DD 格式的日期'
        }
      )
      endDate = String(result.value ?? '').trim()
    } catch {
      return
    }
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.extendPlan(plan.id, endDate, plan.version)
      await load()
      message('培训计划已延期')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function publish(plan: TrainingPlan) {
    if (loading.value) return
    if (!selectedEmployees.value.length) {
      error.value = '请在目标员工名单中选择至少一名员工'
      return
    }
    try {
      await ElMessageBox.confirm(
        `将“${plan.title}”发布给已选择的 ${selectedEmployees.value.length} 名员工，发布后名单固化。`,
        '发布培训计划',
        { confirmButtonText: '发布并分配', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
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
            ? '草稿可编辑；发布后可延期或终止，名单和已签发证书不会被删除。'
            : '完成全部必修课程和考试后自动结业，选修项目不影响结业。'
        }}</p></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新数据</button></div
    >
    <div v-if="notice" class="toast">{{ notice }}</div
    ><div v-if="error" class="api-alert"
      ><b>培训计划接口</b><span>{{ error }}</span
      ><button class="text-button" @click="error = ''">关闭</button></div
    ><div v-if="expiring.length" class="plan-reminder" role="status"
      ><b>到期提醒</b
      ><span
        >{{ admin ? '有' : '你有' }} {{ expiring.length }}
        个计划已到期或将在 7 天内到期，请尽快处理。</span
      ></div
    >
    <div v-if="loading && !plans.length" class="empty-state">正在读取培训计划…</div>
    <div v-if="admin" class="panel roster-selector"
      ><h2>发布目标名单</h2
      ><p class="muted">发布草稿前选择目标员工，发布后名单固化；重复选择不会重复分配。</p
      ><form class="toolbar admin-toolbar" @submit.prevent
        ><input
          v-model="employeeSearch"
          aria-label="检索参训员工"
          placeholder="搜索姓名、工号或部门"
        /><button type="button" class="text-button" :disabled="loading" @click="selectPage"
          >选择本页</button
        ><button type="button" class="text-button" :disabled="loading" @click="unselectPage"
          >取消本页</button
        ><button
          type="button"
          class="text-button"
          :disabled="loading"
          @click="selectedEmployees = []"
          >清空选择</button
        ></form
      ><label v-for="employee in pageEmployees" :key="employee.id" class="option-line"
        ><input
          v-model="selectedEmployees"
          type="checkbox"
          :value="employee.id"
          :disabled="loading"
        />{{ employee.name }} · {{ employee.employeeNo }} ·
        {{ employee.departmentName || '未分配部门' }}</label
      ><p v-if="!filteredEmployees.length" class="muted">没有符合条件的可分配员工。</p
      ><div class="live-pagination"
        ><button
          class="outline-button"
          :disabled="employeePage <= 1 || loading"
          @click="employeePage--"
          >上一页</button
        ><span
          >{{ employeePage }} / {{ Math.max(1, Math.ceil(filteredEmployees.length / 20)) }}</span
        ><button
          class="outline-button"
          :disabled="employeePage * 20 >= filteredEmployees.length || loading"
          @click="employeePage++"
          >下一页</button
        ></div
      ><p
        >已选择 {{ selectedEmployees.length }} 人 · 筛选结果
        {{ filteredEmployees.length }} 人，跨页保留选择</p
      ></div
    >
    <template v-if="!loading || plans.length">
      <div class="plan-list"
        ><article v-for="plan in plans" :key="plan.id" class="plan-card"
          ><div
            ><span class="course-meta"
              >{{ plan.category }} · {{ plan.startDate }} 至 {{ plan.endDate
              }}<template v-if="expiryLabel(plan)"
                > · <span class="plan-expiry" :class="{ overdue: plan.daysRemaining < 0 }">{{
                  expiryLabel(plan)
                }}</span></template
              ></span
            ><h3>{{ plan.title }}</h3
            ><p>{{ plan.description || '暂无计划说明' }}</p
            ><div v-if="!admin" class="progress-track"
              ><i :style="{ width: `${plan.progressPercent}%` }"></i></div
            ><small v-if="!admin"
              >{{ plan.completedItems }} / {{ plan.totalItems }} 必修项完成 ·
              {{ plan.progressPercent }}%</small
            ><small v-else
              >{{ plan.assigneeCount }} 名参训员工 · {{ plan.totalItems }} 项必修要求</small
            ></div
          ><div class="plan-card-actions"
            ><span class="status-pill">{{ statusLabel(plan) }}</span
            ><button
              v-if="admin && plan.status === 'DRAFT'"
              class="text-button"
              :disabled="loading"
              @click="startEdit(plan)"
              >编辑</button
            ><button
              v-if="admin && plan.status === 'DRAFT'"
              class="text-button"
              :disabled="loading"
              @click="publish(plan)"
              >发布</button
            ><button
              v-if="admin && plan.status === 'PUBLISHED'"
              class="text-button"
              :disabled="loading"
              @click="extend(plan)"
              >延期</button
            ><button
              v-if="admin && plan.status === 'PUBLISHED'"
              class="text-button"
              :disabled="loading"
              @click="terminate(plan)"
              >终止</button
            ><button class="text-button" :disabled="loading" @click="open(plan)">查看</button
            ><button
              v-if="!admin && plan.status === 'PUBLISHED'"
              class="outline-button compact"
              @click="refreshProgress(plan)"
              >刷新进度</button
            ></div
          ></article
        ><p v-if="!plans.length" class="empty-state">暂无培训计划</p></div
      >
      <form v-if="admin" class="panel plan-form" @submit.prevent="savePlan"
        ><div class="panel-heading"
          ><h2>{{ editingId ? '编辑计划草稿' : '创建计划草稿' }}</h2></div
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
        ><button class="primary-button compact" :disabled="loading">{{
          editingId ? '保存修改' : '创建计划'
        }}</button
        ><button
          v-if="editingId"
          type="button"
          class="text-button"
          :disabled="loading"
          @click="cancelEdit"
          >取消编辑</button
        ></form
      >
    </template>
    <div v-if="selected" class="detail-panel plan-detail"
      ><div class="panel-heading"
        ><h2>{{ selected.plan.title }}</h2
        ><span class="status-pill">{{ statusLabel(selected.plan) }}</span
        ><button class="text-button" @click="selected = null">关闭</button></div
      ><p class="muted"
        >{{ selected.plan.description
        }}<template v-if="!admin"> · {{ selected.plan.progressPercent }}% 完成</template></p
      ><p
        v-if="selected.plan.status === 'TERMINATED'"
        class="plan-terminated"
        >已于 {{ selected.plan.terminatedAt?.slice(0, 10) }} 终止<template
          v-if="selected.plan.terminationReason"
          >：{{ selected.plan.terminationReason }}</template
        ></p
      ><div class="plan-items"
        ><article v-for="item in selected.items" :key="`${item.itemType}-${item.itemId}`"
          ><span class="status-pill">{{ item.itemType === 'COURSE' ? '课程' : '考试' }}</span
          ><strong>{{ item.title }}</strong
          ><router-link
            v-if="employeeLinks && selected.plan.status === 'PUBLISHED'"
            class="text-button"
            :to="item.itemType === 'COURSE' ? `/learn/${item.itemId}` : '/exams'"
            >{{ item.itemType === 'COURSE' ? '前往学习' : '前往考试' }}</router-link
          ><small
            >{{ item.required ? '必修' : '选修'
            }}<template v-if="!admin">
              · {{ item.status === 'COMPLETED' ? '已完成' : `${item.progressPercent}%` }}</template
            ></small
          ></article
        ></div
      ></div
    >
  </section>
</template>

<style scoped>
  .plan-reminder {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 12px 16px;
    margin: 14px 0;
    font-size: 13px;
    color: #7a4a00;
    background: #fff8e6;
    border: 1px solid #e9c46a;
    border-radius: 10px;
  }
  .plan-expiry {
    font-weight: 600;
    color: #a15c00;
  }
  .plan-expiry.overdue {
    color: #b42318;
  }
  .plan-terminated {
    padding: 10px 14px;
    color: #b42318;
    background: #fff2f0;
    border: 1px solid #f3c2bd;
    border-radius: 8px;
  }
</style>
