<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { ElMessageBox } from 'element-plus'
  import { RouterLink } from 'vue-router'
  import AppIcon from '../training/components/AppIcon.vue'
  import {
    ApiError,
    api,
    type Course,
    type Exam,
    type PlanDetail,
    type TrainingPlan
  } from './api'

  const props = defineProps<{ admin: boolean; employeeLinks?: boolean }>()
  const plans = ref<TrainingPlan[]>([])
  const courses = ref<Course[]>([])
  const exams = ref<Exam[]>([])
  const employees = ref<
    Array<{ id: number; employeeNo: string; name: string; departmentName?: string }>
  >([])
  const selectedEmployees = ref<number[]>([])
  const employeeSearch = ref('')
  const employeePage = ref(1)
  const filteredEmployees = computed(() => {
    const keyword = employeeSearch.value.trim().toLowerCase()
    return employees.value.filter((employee) =>
      `${employee.name} ${employee.employeeNo} ${employee.departmentName ?? ''}`
        .toLowerCase()
        .includes(keyword)
    )
  })
  const pageEmployees = computed(() =>
    filteredEmployees.value.slice((employeePage.value - 1) * 20, employeePage.value * 20)
  )
  watch(employeeSearch, () => (employeePage.value = 1))

  const selected = ref<PlanDetail | null>(null)
  const detailOpen = computed({
    get: () => !!selected.value,
    set: (value: boolean) => {
      if (!value) selected.value = null
    }
  })
  const formOpen = ref(false)
  const publishTarget = ref<TrainingPlan | null>(null)
  const publishOpen = computed({
    get: () => !!publishTarget.value,
    set: (value: boolean) => {
      if (!value) publishTarget.value = null
    }
  })
  const editingId = ref<number | null>(null)
  const editingVersion = ref(0)
  const formError = ref('')
  const publishError = ref('')
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

  const expiring = computed(() =>
    plans.value.filter(
      (plan) =>
        plan.status === 'PUBLISHED' &&
        plan.daysRemaining <= 7 &&
        (props.admin || plan.progressPercent < 100)
    )
  )
  const stats = computed(() => {
    const published = plans.value.filter((plan) => plan.status === 'PUBLISHED')
    return {
      total: plans.value.length,
      drafts: plans.value.filter((plan) => plan.status === 'DRAFT').length,
      active: published.filter((plan) => plan.progressPercent < 100).length,
      done: published.filter((plan) => plan.progressPercent === 100).length,
      expiring: expiring.value.length
    }
  })
  const statTiles = computed(() =>
    props.admin
      ? [
          { label: '计划总数', value: stats.value.total, tone: 'neutral' },
          { label: '草稿', value: stats.value.drafts, tone: 'draft' },
          { label: '进行中', value: stats.value.active, tone: 'active' },
          { label: '7 天内到期', value: stats.value.expiring, tone: 'warn' }
        ]
      : [
          { label: '我的计划', value: stats.value.total, tone: 'neutral' },
          { label: '进行中', value: stats.value.active, tone: 'active' },
          { label: '已完成', value: stats.value.done, tone: 'done' },
          { label: '7 天内到期', value: stats.value.expiring, tone: 'warn' }
        ]
  )
  const formTitle = computed(() => (editingId.value ? '编辑计划草稿' : '新建培训计划'))

  function message(text: string) {
    notice.value = text
    window.setTimeout(() => {
      if (notice.value === text) notice.value = ''
    }, 3000)
  }
  function errorText(e: unknown) {
    return e instanceof ApiError ? e.message : '培训计划接口暂时不可用，请稍后重试'
  }
  function apiMessage(e: unknown) {
    error.value = errorText(e)
  }
  function selectPage() {
    selectedEmployees.value = [
      ...new Set([...selectedEmployees.value, ...pageEmployees.value.map((employee) => employee.id)])
    ]
  }
  function unselectPage() {
    const pageIds = new Set(pageEmployees.value.map((employee) => employee.id))
    selectedEmployees.value = selectedEmployees.value.filter((id) => !pageIds.has(id))
  }
  function resetForm() {
    Object.assign(form, {
      title: '新员工综合培训计划',
      description: '完成课程学习并通过对应考试。',
      category: '入职培训',
      startDate: '',
      endDate: '',
      courseId: courses.value.find((course) => course.status === 'PUBLISHED')?.id ?? 0,
      examId: exams.value.find((exam) => exam.status === 'PUBLISHED')?.id ?? 0,
      courseRequired: true,
      examRequired: true
    })
    editingId.value = null
    editingVersion.value = 0
    formError.value = ''
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      plans.value = await api.plans(props.admin)
      if (props.admin) {
        employees.value = await api.eligibleEmployees()
        if (!courses.value.length) {
          const first = await api.courses(false, 1, 100)
          const published = [...first.list]
          for (let page = 2; (page - 1) * 100 < first.total; page++)
            published.push(...(await api.courses(false, page, 100)).list)
          courses.value = published
        }
        if (!exams.value.length) exams.value = await api.exams(true)
        form.courseId ||= courses.value.find((course) => course.status === 'PUBLISHED')?.id ?? 0
        form.examId ||= exams.value.find((exam) => exam.status === 'PUBLISHED')?.id ?? 0
      }
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  function openCreate() {
    resetForm()
    formOpen.value = true
  }
  function openEdit(plan: TrainingPlan) {
    loading.value = true
    error.value = ''
    formError.value = ''
    ;(async () => {
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
        formOpen.value = true
      } catch (e) {
        apiMessage(e)
      } finally {
        loading.value = false
      }
    })()
  }
  async function savePlan() {
    if (!form.title.trim()) {
      formError.value = '请填写计划名称'
      return
    }
    if (!form.courseId && !form.examId) {
      formError.value = '至少选择一门已发布课程或一场已发布考试'
      return
    }
    loading.value = true
    formError.value = ''
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
        await api.updatePlan(editingId.value, {
          ...payload,
          version: editingVersion.value
        })
        message('计划草稿已保存')
      } else {
        await api.createPlan(payload)
        message('培训计划草稿已创建')
      }
      formOpen.value = false
      resetForm()
      await load()
    } catch (e) {
      formError.value = errorText(e)
    } finally {
      loading.value = false
    }
  }
  function openPublish(plan: TrainingPlan) {
    publishTarget.value = plan
    publishError.value = ''
    selectedEmployees.value = []
    employeeSearch.value = ''
    employeePage.value = 1
  }
  async function confirmPublish() {
    if (!publishTarget.value) return
    if (!selectedEmployees.value.length) {
      publishError.value = '请至少选择一名员工'
      return
    }
    loading.value = true
    publishError.value = ''
    try {
      await api.publishPlan(publishTarget.value.id, selectedEmployees.value)
      publishOpen.value = false
      message('培训计划已发布并分配给员工')
      await load()
    } catch (e) {
      publishError.value = errorText(e)
    } finally {
      loading.value = false
    }
  }
  async function openDetail(plan: TrainingPlan) {
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
      const detail = await api.refreshPlan(plan.id)
      if (selected.value?.plan.id === plan.id) selected.value = detail
      plans.value = await api.plans(false)
      message('计划进度已刷新')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
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
      const detail = await api.terminatePlan(plan.id, reason, plan.version)
      if (selected.value?.plan.id === plan.id) selected.value = detail
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
      const detail = await api.extendPlan(plan.id, endDate, plan.version)
      if (selected.value?.plan.id === plan.id) selected.value = detail
      await load()
      message('培训计划已延期')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  function statusClass(plan: TrainingPlan) {
    if (plan.status === 'TERMINATED') return 'terminated'
    if (plan.status === 'DRAFT') return 'draft'
    return !props.admin && plan.progressPercent === 100 ? 'done' : 'active'
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
  onMounted(load)
</script>

<template>
  <section class="page-section plan-panel">
    <header class="plan-hero">
      <div class="plan-hero-copy">
        <span class="eyebrow">TRAINING / {{ admin ? 'MANAGEMENT' : 'MY PLAN' }}</span>
        <h1>{{ admin ? '培训计划' : '我的培训' }}</h1>
        <p>
          {{
            admin
              ? '组合课程与考试，跟踪每个人的完成情况。草稿可编辑，发布后可延期或终止。'
              : '完成全部必修项后自动结业，选修项不影响结业进度。'
          }}
        </p>
      </div>
      <div class="plan-hero-actions">
        <button class="outline-button" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="16" />刷新
        </button>
        <button v-if="admin" class="primary-button" @click="openCreate">
          <AppIcon name="plus" :size="16" />新建计划
        </button>
      </div>
    </header>

    <div v-if="error" class="api-alert" role="alert">
      <b>培训计划接口</b><span>{{ error }}</span>
      <button class="text-button" @click="error = ''">关闭</button>
    </div>
    <div v-if="notice" class="toast" role="status">{{ notice }}</div>

    <div class="plan-stats">
      <article
        v-for="tile in statTiles"
        :key="tile.label"
        class="plan-stat"
        :class="[`tone-${tile.tone}`, { 'is-zero': tile.value === 0 }]"
      >
        <span>{{ tile.label }}</span>
        <strong>{{ tile.value }}</strong>
      </article>
    </div>

    <div v-if="expiring.length" class="plan-reminder" role="status">
      <span class="plan-reminder-icon"><AppIcon name="clock" :size="18" /></span>
      <div>
        <b>到期提醒</b>
        <span
          >{{ admin ? '有' : '你有' }} {{ expiring.length }}
          个计划已到期或将在 7 天内到期，请尽快处理。</span
        >
      </div>
    </div>

    <div v-if="loading && !plans.length" class="plan-loading">
      <span class="plan-spinner" />正在读取培训计划…
    </div>
    <div v-else-if="!plans.length" class="plan-empty-wrap">
      <div class="plan-empty">
        <span class="plan-empty-icon"><AppIcon name="calendar" :size="30" /></span>
        <h2>{{ admin ? '还没有培训计划' : '暂无可参加的培训' }}</h2>
        <p>
          {{
            admin
              ? '把课程和考试组合成计划，选择员工发布后即可跟踪完成情况。'
              : '等管理员发布培训计划后，这里会显示你的学习任务和完成进度。'
          }}
        </p>
        <button v-if="admin" class="primary-button" @click="openCreate">
          <AppIcon name="plus" :size="16" />创建第一个计划
        </button>
      </div>
      <div v-if="admin" class="plan-guide">
        <article>
          <span>01</span>
          <div><b>创建计划</b><small>选择已发布的课程或考试，设置必修项</small></div>
        </article>
        <article>
          <span>02</span>
          <div><b>选择名单</b><small>搜索并勾选参训员工，发布后名单固化</small></div>
        </article>
        <article>
          <span>03</span>
          <div><b>发布跟踪</b><small>查看完成进度，临近截止会收到提醒</small></div>
        </article>
      </div>
    </div>
    <div v-else class="plan-grid">
      <article
        v-for="plan in plans"
        :key="plan.id"
        class="plan-card"
        :class="`tone-${statusClass(plan)}`"
      >
        <div class="plan-card-head">
          <span class="plan-status" :class="statusClass(plan)">{{ statusLabel(plan) }}</span>
          <span
            v-if="expiryLabel(plan)"
            class="plan-expiry"
            :class="{ overdue: plan.daysRemaining < 0 }"
            >{{ expiryLabel(plan) }}</span
          >
        </div>
        <h3>{{ plan.title }}</h3>
        <p>{{ plan.description || '暂无计划说明' }}</p>
        <div class="plan-meta">
          <span><AppIcon name="calendar" :size="14" />{{ plan.startDate }} — {{ plan.endDate }}</span>
          <span class="plan-category">{{ plan.category }}</span>
        </div>
        <div v-if="!admin" class="plan-progress">
          <div class="progress-track"><i :style="{ width: `${plan.progressPercent}%` }" /></div>
          <small>{{ plan.completedItems }} / {{ plan.totalItems }} 必修项 · {{ plan.progressPercent }}%</small>
        </div>
        <div v-else class="plan-admin-meta">
          <span><AppIcon name="user" :size="14" />{{ plan.assigneeCount }} 名员工</span>
          <span>{{ plan.totalItems }} 项必修</span>
        </div>
        <div class="plan-card-actions">
          <button class="text-button" @click="openDetail(plan)">查看详情</button>
          <button
            v-if="!admin && plan.status === 'PUBLISHED'"
            class="text-button"
            @click="refreshProgress(plan)"
            >刷新进度</button
          >
          <button
            v-if="admin && plan.status === 'DRAFT'"
            class="text-button"
            @click="openEdit(plan)"
            >编辑</button
          >
          <button
            v-if="admin && plan.status === 'DRAFT'"
            class="primary-button compact"
            @click="openPublish(plan)"
            >发布</button
          >
          <button
            v-if="admin && plan.status === 'PUBLISHED'"
            class="text-button"
            @click="extend(plan)"
            >延期</button
          >
          <button
            v-if="admin && plan.status === 'PUBLISHED'"
            class="text-button danger"
            @click="terminate(plan)"
            >终止</button
          >
        </div>
      </article>
    </div>

    <el-dialog
      v-model="formOpen"
      class="plan-dialog"
      :title="formTitle"
      width="min(720px, 94vw)"
      append-to-body
      destroy-on-close
      @closed="resetForm"
    >
      <p class="dialog-hint">
        {{
          editingId
            ? '草稿可以整体修改；发布后只能延期或终止。'
            : '至少绑定一门课程或一场考试，并设置至少一项必修内容。'
        }}
      </p>
      <div v-if="formError" class="form-error">{{ formError }}</div>
      <form class="plan-form-grid" @submit.prevent="savePlan">
        <label class="field"
          ><span>计划名称</span
          ><input
            v-model="form.title"
            required
            maxlength="160"
            placeholder="例如：2026 年新员工入职培训"
        /></label>
        <label class="field"
          ><span>分类</span
          ><input v-model="form.category" maxlength="80" placeholder="例如：入职培训"
        /></label>
        <label class="field"
          ><span>开始日期</span><input v-model="form.startDate" type="date"
        /></label>
        <label class="field"
          ><span>结束日期</span><input v-model="form.endDate" type="date"
        /></label>
        <label class="field"
          ><span>绑定课程</span
          ><select v-model.number="form.courseId"
            ><option :value="0">不绑定课程</option
            ><option
              v-for="course in courses.filter((item) => item.status === 'PUBLISHED')"
              :key="course.id"
              :value="course.id"
              >{{ course.title }}</option
            ></select
          ></label
        >
        <label class="field"
          ><span>绑定考试</span
          ><select v-model.number="form.examId"
            ><option :value="0">不绑定考试</option
            ><option
              v-for="exam in exams.filter((item) => item.status === 'PUBLISHED')"
              :key="exam.id"
              :value="exam.id"
              >{{ exam.title }}</option
            ></select
          ></label
        >
        <label class="field-check"
          ><input v-model="form.courseRequired" type="checkbox" /><span>所选课程为必修</span></label
        >
        <label class="field-check"
          ><input v-model="form.examRequired" type="checkbox" /><span>所选考试为必修</span></label
        >
        <label class="field field-wide"
          ><span>说明</span
          ><textarea
            v-model="form.description"
            rows="3"
            maxlength="500"
            placeholder="说明培训目标、完成要求或注意事项"
          ></textarea>
        </label>
      </form>
      <template #footer>
        <button class="outline-button" :disabled="loading" @click="formOpen = false">取消</button>
        <button class="primary-button" :disabled="loading" @click="savePlan">
          {{ editingId ? '保存修改' : '创建计划' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="publishOpen"
      class="plan-dialog"
      title="发布培训计划"
      width="min(680px, 94vw)"
      append-to-body
      @closed="publishTarget = null"
    >
      <div v-if="publishTarget" class="publish-summary">
        <b>{{ publishTarget.title }}</b>
        <span>{{ publishTarget.startDate }} — {{ publishTarget.endDate }}</span>
      </div>
      <p class="dialog-hint">选择参训员工；发布后名单固化，重复选择不会重复分配。</p>
      <div v-if="publishError" class="form-error">{{ publishError }}</div>
      <div class="roster-toolbar">
        <input v-model="employeeSearch" aria-label="检索参训员工" placeholder="搜索姓名、工号或部门" />
        <button class="text-button" @click="selectPage">选择本页</button>
        <button class="text-button" @click="unselectPage">取消本页</button>
        <button class="text-button" @click="selectedEmployees = []">清空选择</button>
      </div>
      <div class="roster-list">
        <label v-for="employee in pageEmployees" :key="employee.id" class="roster-item">
          <input v-model="selectedEmployees" type="checkbox" :value="employee.id" />
          <span class="roster-avatar">{{ employee.name.slice(0, 1) }}</span>
          <span class="roster-name"
            >{{ employee.name
            }}<small>{{ employee.employeeNo }} · {{ employee.departmentName || '未分配部门' }}</small></span
          >
        </label>
        <p v-if="!filteredEmployees.length" class="muted">没有符合条件的可分配员工。</p>
      </div>
      <div class="roster-footer">
        <span>已选择 <b>{{ selectedEmployees.length }}</b> 人 · 共 {{ filteredEmployees.length }} 人</span>
        <div class="pagination">
          <button class="outline-button compact" :disabled="employeePage <= 1" @click="employeePage--"
            >上一页</button
          >
          <span>{{ employeePage }} / {{ Math.max(1, Math.ceil(filteredEmployees.length / 20)) }}</span>
          <button
            class="outline-button compact"
            :disabled="employeePage * 20 >= filteredEmployees.length"
            @click="employeePage++"
            >下一页</button
          >
        </div>
      </div>
      <template #footer>
        <button class="outline-button" :disabled="loading" @click="publishOpen = false">取消</button>
        <button
          class="primary-button"
          :disabled="loading || !selectedEmployees.length"
          @click="confirmPublish"
        >
          发布给 {{ selectedEmployees.length }} 人
        </button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="detailOpen"
      class="plan-drawer"
      title="计划详情"
      size="min(520px, 94vw)"
      append-to-body
    >
      <template v-if="selected">
        <div class="detail-head">
          <span class="plan-status" :class="statusClass(selected.plan)">{{
            statusLabel(selected.plan)
          }}</span>
          <span
            v-if="expiryLabel(selected.plan)"
            class="plan-expiry"
            :class="{ overdue: selected.plan.daysRemaining < 0 }"
            >{{ expiryLabel(selected.plan) }}</span
          >
        </div>
        <h2>{{ selected.plan.title }}</h2>
        <p class="detail-desc">{{ selected.plan.description || '暂无计划说明' }}</p>
        <dl class="detail-meta">
          <div><dt>分类</dt><dd>{{ selected.plan.category }}</dd></div>
          <div><dt>培训周期</dt><dd>{{ selected.plan.startDate }} — {{ selected.plan.endDate }}</dd></div>
          <div v-if="admin"><dt>参训人数</dt><dd>{{ selected.plan.assigneeCount }} 人</dd></div>
          <div><dt>必修项</dt><dd>{{ selected.plan.totalItems }} 项</dd></div>
        </dl>
        <div v-if="selected.plan.status === 'TERMINATED'" class="detail-terminated">
          已于 {{ selected.plan.terminatedAt?.slice(0, 10) }} 终止<template
            v-if="selected.plan.terminationReason"
            >：{{ selected.plan.terminationReason }}</template
          >
        </div>
        <h3 class="detail-subtitle">培训内容</h3>
        <div class="detail-items">
          <article
            v-for="item in selected.items"
            :key="`${item.itemType}-${item.itemId}`"
            class="detail-item"
          >
            <span class="detail-item-kind">{{ item.itemType === 'COURSE' ? '课程' : '考试' }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <small
                >{{ item.required ? '必修' : '选修'
                }}<template v-if="!admin">
                  · {{ item.status === 'COMPLETED' ? '已完成' : `${item.progressPercent}%` }}</template
                ></small
              >
            </div>
            <router-link
              v-if="employeeLinks && selected.plan.status === 'PUBLISHED'"
              class="text-button"
              :to="item.itemType === 'COURSE' ? `/learn/${item.itemId}` : '/exams'"
              >前往</router-link
            >
          </article>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style>
  .plan-dialog {
    max-width: 94vw;
    border-radius: 16px;
  }
  .plan-dialog .el-dialog__header {
    padding: 22px 26px 0;
    margin-right: 0;
  }
  .plan-dialog .el-dialog__title {
    font-size: 18px;
    font-weight: 700;
    color: #17211f;
  }
  .plan-dialog .el-dialog__body {
    padding: 16px 26px 8px;
  }
  .plan-dialog .el-dialog__footer {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
    padding: 0 26px 22px;
  }
  .plan-drawer .el-drawer__header {
    padding: 22px 24px 6px;
    margin-bottom: 0;
    font-weight: 700;
    color: #17211f;
  }
  .plan-drawer .el-drawer__body {
    padding: 0 24px 24px;
  }
  .plan-dialog .el-dialog__footer .outline-button,
  .plan-dialog .el-dialog__footer .primary-button,
  .plan-drawer .outline-button,
  .plan-drawer .primary-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 38px;
    padding: 0 18px;
    font: inherit;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    border-radius: 10px;
  }
  .plan-dialog .el-dialog__footer .outline-button,
  .plan-drawer .outline-button {
    color: #0c6267;
    background: #fff;
    border: 1px solid #cfe0dc;
  }
  .plan-dialog .el-dialog__footer .primary-button,
  .plan-drawer .primary-button {
    color: #fff;
    background: #0c6267;
    border: 0;
    box-shadow: 0 6px 16px rgba(12, 98, 103, 0.18);
  }
  .plan-dialog .el-dialog__footer .primary-button:disabled,
  .plan-dialog .el-dialog__footer .outline-button:disabled,
  .plan-drawer .primary-button:disabled,
  .plan-drawer .outline-button:disabled {
    cursor: wait;
    opacity: 0.6;
  }
  .plan-dialog .text-button,
  .plan-drawer .text-button {
    padding: 4px 6px;
    font-size: 12px;
    color: #0c6267;
    cursor: pointer;
    background: none;
    border: 0;
  }
  .plan-dialog .text-button:hover,
  .plan-drawer .text-button:hover {
    text-decoration: underline;
  }
  .plan-dialog .field-check input,
  .plan-dialog .roster-item input {
    width: 16px;
    height: 16px;
    accent-color: #0c6267;
  }
</style>

<style scoped>
  .plan-panel {
    --ink: #17211f;
    --muted: #6b7a75;
    --line: #e4eae7;
    --brand: #1f7a66;
    --soft: #eef7f3;
  }
  .plan-hero {
    display: flex;
    gap: 24px;
    align-items: flex-end;
    justify-content: space-between;
    margin-bottom: 22px;
  }
  .plan-hero h1 {
    margin: 6px 0 8px;
    font-size: 32px;
    letter-spacing: -0.02em;
    color: var(--ink);
  }
  .plan-hero p {
    max-width: 620px;
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: var(--muted);
  }
  .plan-hero-actions {
    display: flex;
    flex-shrink: 0;
    gap: 10px;
  }
  .plan-hero-actions button {
    display: inline-flex;
    gap: 8px;
    align-items: center;
  }
  .plan-stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
    margin: 20px 0;
  }
  .plan-stat {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 16px 18px;
    background: #fff;
    border: 1px solid var(--line);
    border-radius: 14px;
  }
  .plan-stat span {
    font-size: 12px;
    color: var(--muted);
  }
  .plan-stat strong {
    font-size: 26px;
    line-height: 1.1;
    color: var(--ink);
  }
  .plan-stat.tone-active strong {
    color: #1f7a66;
  }
  .plan-stat.tone-warn strong {
    color: #b26a00;
  }
  .plan-stat.tone-done strong {
    color: #2f6f5e;
  }
  .plan-stat.is-zero strong {
    color: #9aa7a2;
  }
  .plan-reminder {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    padding: 14px 18px;
    margin: 0 0 18px;
    color: #7a4a00;
    background: #fff8e6;
    border: 1px solid #ecd39a;
    border-radius: 12px;
  }
  .plan-reminder b {
    display: block;
    margin-bottom: 2px;
    font-size: 13px;
  }
  .plan-reminder span {
    font-size: 13px;
  }
  .plan-reminder-icon {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    width: 32px;
    height: 32px;
    color: #a15c00;
    background: #ffedc2;
    border-radius: 50%;
  }
  .plan-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
  .plan-card {
    display: flex;
    flex-direction: column;
    flex-wrap: nowrap;
    gap: 10px;
    align-items: stretch;
    justify-content: flex-start;
    padding: 20px;
    background: #fff;
    border: 1px solid var(--line);
    border-top: 3px solid #cfd9d5;
    border-radius: 16px;
    box-shadow: 0 1px 2px rgba(18, 50, 40, 0.03);
    transition:
      box-shadow 0.2s,
      transform 0.2s;
  }
  .plan-card:hover {
    box-shadow: 0 10px 28px rgba(18, 50, 40, 0.08);
    transform: translateY(-2px);
  }
  .plan-card.tone-active {
    border-top-color: #1f7a66;
  }
  .plan-card.tone-draft {
    border-top-color: #c9a227;
  }
  .plan-card.tone-done {
    border-top-color: #2f6f5e;
  }
  .plan-card.tone-terminated {
    border-top-color: #b42318;
  }
  .plan-card-head {
    display: flex;
    flex: 0 0 auto;
    width: 100%;
    gap: 8px;
    align-items: center;
    justify-content: space-between;
  }
  .plan-card > p,
  .plan-card-actions,
  .plan-meta,
  .plan-admin-meta,
  .plan-progress {
    width: 100%;
  }
  .plan-status {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    font-size: 11px;
    font-weight: 600;
    border-radius: 999px;
  }
  .plan-status.active {
    color: #116454;
    background: #e6f5ef;
  }
  .plan-status.draft {
    color: #8a6a00;
    background: #fbf3d7;
  }
  .plan-status.done {
    color: #245c4c;
    background: #e9f4ef;
  }
  .plan-status.terminated {
    color: #a12820;
    background: #fdecea;
  }
  .plan-expiry {
    font-size: 12px;
    font-weight: 600;
    color: #a15c00;
    white-space: nowrap;
  }
  .plan-expiry.overdue {
    color: #b42318;
  }
  .plan-card h3 {
    margin: 0;
    font-size: 17px;
    color: var(--ink);
  }
  .plan-card > p {
    display: -webkit-box;
    min-height: 2.9em;
    margin: 0;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.6;
    color: var(--muted);
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
  .plan-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: #7d8b87;
  }
  .plan-meta span {
    display: inline-flex;
    gap: 5px;
    align-items: center;
  }
  .plan-category {
    padding: 2px 8px;
    color: #5f6f6a;
    background: #f3f6f5;
    border-radius: 6px;
    align-self: flex-start;
  }
  .plan-progress {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .plan-progress small {
    font-size: 11px;
    color: #7d8b87;
  }
  .plan-admin-meta {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #7d8b87;
  }
  .plan-admin-meta span {
    display: inline-flex;
    gap: 5px;
    align-items: center;
  }
  .plan-card-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
    justify-content: flex-start;
    padding-top: 8px;
    margin-top: auto;
    border-top: 1px solid #f0f3f2;
  }
  .plan-card-actions .text-button {
    padding: 6px 8px;
    font-size: 12px;
    color: #0c6267;
    cursor: pointer;
    background: transparent;
    border: 0;
  }
  .plan-card-actions .text-button:hover {
    text-decoration: underline;
  }
  .plan-card-actions .danger {
    color: #b42318;
  }
  .plan-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 60px 24px 52px;
    text-align: center;
    background: #fff;
    border: 1px dashed #d7e1dd;
    border-radius: 18px;
  }
  .plan-empty-wrap {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .plan-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }
  .plan-guide article {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 16px 18px;
    background: #fff;
    border: 1px solid var(--line);
    border-radius: 14px;
  }
  .plan-guide article > span {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    width: 34px;
    height: 34px;
    font-size: 11px;
    font-weight: 700;
    color: #1f7a66;
    background: var(--soft);
    border-radius: 50%;
  }
  .plan-guide div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .plan-guide b {
    font-size: 13px;
    color: var(--ink);
  }
  .plan-guide small {
    font-size: 11px;
    line-height: 1.5;
    color: var(--muted);
  }
  .plan-empty-icon {
    display: grid;
    place-items: center;
    width: 64px;
    height: 64px;
    margin-bottom: 16px;
    color: var(--brand);
    background: var(--soft);
    border-radius: 20px;
  }
  .plan-empty h2 {
    margin: 0 0 8px;
    font-size: 20px;
    color: var(--ink);
  }
  .plan-empty p {
    max-width: 420px;
    margin: 0 0 20px;
    font-size: 14px;
    line-height: 1.6;
    color: var(--muted);
  }
  .plan-loading {
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: center;
    padding: 60px;
    color: var(--muted);
  }
  .plan-spinner {
    width: 18px;
    height: 18px;
    border: 2px solid #d7e1dd;
    border-top-color: var(--brand);
    border-radius: 50%;
    animation: plan-spin 0.8s linear infinite;
  }
  @keyframes plan-spin {
    to {
      transform: rotate(360deg);
    }
  }
  .dialog-hint {
    margin: 0 0 14px;
    font-size: 12px;
    line-height: 1.6;
    color: #7d8b87;
  }
  .form-error {
    padding: 9px 12px;
    margin: 0 0 12px;
    font-size: 12px;
    color: #b42318;
    background: #fdecea;
    border-radius: 8px;
  }
  .plan-form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px 16px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #3d4c48;
  }
  .field input,
  .field select,
  .field textarea {
    width: 100%;
    padding: 10px 12px;
    font: inherit;
    font-weight: 400;
    color: #1f2a27;
    background: #fbfcfc;
    border: 1px solid #dce4e1;
    border-radius: 9px;
  }
  .field input:focus,
  .field select:focus,
  .field textarea:focus {
    background: #fff;
    border-color: #2f8a74;
    outline: none;
    box-shadow: 0 0 0 3px rgba(47, 138, 116, 0.12);
  }
  .field-wide {
    grid-column: 1 / -1;
  }
  .field-check {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    color: #4b5a56;
  }
  .field-check input {
    width: 16px;
    height: 16px;
  }
  .publish-summary {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 14px 16px;
    margin-bottom: 14px;
    background: #f4f8f6;
    border-radius: 10px;
  }
  .publish-summary b {
    color: #1f2a27;
  }
  .publish-summary span {
    font-size: 12px;
    color: #7d8b87;
  }
  .roster-toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-bottom: 12px;
  }
  .roster-toolbar input {
    flex: 1 1 220px;
    min-width: 0;
    padding: 9px 12px;
    border: 1px solid #dce4e1;
    border-radius: 8px;
  }
  .roster-list {
    max-height: 340px;
    overflow: auto;
    border: 1px solid #edf1ef;
    border-radius: 10px;
  }
  .roster-item {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 10px 12px;
    cursor: pointer;
    border-bottom: 1px solid #f2f5f4;
  }
  .roster-item:last-child {
    border-bottom: 0;
  }
  .roster-item:hover {
    background: #f8fbfa;
  }
  .roster-avatar {
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    font-size: 12px;
    font-weight: 700;
    color: #116454;
    background: #e6f5ef;
    border-radius: 50%;
  }
  .roster-name {
    display: flex;
    flex-direction: column;
    gap: 1px;
    font-size: 13px;
    color: #23302c;
  }
  .roster-name small {
    font-size: 11px;
    color: #7d8b87;
  }
  .roster-footer {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
    font-size: 12px;
    color: #7d8b87;
  }
  .pagination {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  .detail-head {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 10px;
  }
  .detail-head h2 {
    margin: 0;
  }
  .plan-drawer h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: #17211f;
  }
  .detail-desc {
    margin: 0 0 18px;
    font-size: 13px;
    line-height: 1.7;
    color: #6b7a75;
  }
  .detail-meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin: 0 0 16px;
  }
  .detail-meta div {
    padding: 10px 12px;
    background: #f7faf9;
    border-radius: 9px;
  }
  .detail-meta dt {
    margin: 0 0 2px;
    font-size: 11px;
    color: #8a9793;
  }
  .detail-meta dd {
    margin: 0;
    font-size: 12px;
    color: #25312d;
  }
  .detail-terminated {
    padding: 12px 14px;
    margin: 0 0 16px;
    font-size: 12px;
    line-height: 1.6;
    color: #a12820;
    background: #fdecea;
    border-radius: 10px;
  }
  .detail-subtitle {
    margin: 6px 0 10px;
    font-size: 13px;
    color: #3d4c48;
  }
  .detail-items {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .detail-item {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 10px 12px;
    border: 1px solid #edf1ef;
    border-radius: 10px;
  }
  .detail-item-kind {
    padding: 3px 8px;
    font-size: 11px;
    color: #116454;
    background: #eef7f3;
    border-radius: 6px;
  }
  .detail-item div {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }
  .detail-item strong {
    font-size: 13px;
    color: #25312d;
  }
  .detail-item small {
    font-size: 11px;
    color: #7d8b87;
  }
  .plan-panel .plan-card {
    flex-wrap: nowrap;
    justify-content: flex-start;
  }
  .plan-panel .plan-card-head {
    flex: 0 0 auto;
    width: 100%;
  }
  .plan-panel .plan-card-actions {
    white-space: normal;
  }
  @media (max-width: 900px) {
    .plan-grid {
      grid-template-columns: 1fr;
    }
    .plan-stats {
      grid-template-columns: repeat(2, 1fr);
    }
    .plan-guide {
      grid-template-columns: 1fr;
    }
  }
  @media (max-width: 640px) {
    .plan-hero {
      flex-direction: column;
      align-items: flex-start;
    }
    .plan-hero-actions {
      width: 100%;
    }
    .plan-hero-actions button {
      flex: 1;
      justify-content: center;
    }
    .plan-form-grid {
      grid-template-columns: 1fr;
    }
    .plan-meta {
      flex-direction: column;
      gap: 6px;
      align-items: flex-start;
    }
  }
</style>
