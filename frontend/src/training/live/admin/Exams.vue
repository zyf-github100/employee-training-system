<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { api, type BankQuestion, type Exam, type ExamDetail } from '../../../connected/api'
  import { errorMessage } from '../session'
  const exams = ref<Exam[]>([]),
    questions = ref<BankQuestion[]>([]),
    detail = ref<ExamDetail | null>(null)
  const loading = ref(false),
    saving = ref(false),
    error = ref(''),
    editor = ref(false),
    keyword = ref(''),
    status = ref(''),
    questionSearch = ref('')
  const form = reactive({
    title: '',
    category: '',
    durationMinutes: 30,
    passScore: 60,
    deadline: ''
  })
  const selected = ref<Array<{ questionId: number; points: number }>>([])
  const labels: Record<string, string> = { single: '单选', multiple: '多选', boolean: '判断' }
  const visible = computed(() =>
    exams.value.filter(
      (e) =>
        (!status.value || e.status === status.value) &&
        e.title.toLowerCase().includes(keyword.value.trim().toLowerCase())
    )
  )
  const available = computed(() =>
    questions.value.filter(
      (q) =>
        q.status === 'ENABLED' &&
        q.stem.toLowerCase().includes(questionSearch.value.trim().toLowerCase())
    )
  )
  const total = computed(() => selected.value.reduce((n, q) => n + (Number(q.points) || 0), 0))
  async function load() {
    loading.value = true
    error.value = ''
    try {
      const [list, bank] = await Promise.all([api.exams(true), api.questions()])
      exams.value = list
      questions.value = bank
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  function create() {
    Object.assign(form, {
      title: '',
      category: '',
      durationMinutes: 30,
      passScore: 60,
      deadline: ''
    })
    selected.value = []
    questionSearch.value = ''
    editor.value = true
  }
  function toggle(id: number) {
    if (selected.value.some((q) => q.questionId === id))
      selected.value = selected.value.filter((q) => q.questionId !== id)
    else if (selected.value.length < 50) selected.value.push({ questionId: id, points: 1 })
    else ElMessage.warning('每张试卷最多选择 50 道题目')
  }
  function distribute() {
    const n = selected.value.length
    if (n)
      selected.value.forEach((q, i) => (q.points = Math.floor(100 / n) + (i < 100 % n ? 1 : 0)))
  }
  async function save() {
    if (saving.value) return
    if (
      !selected.value.length ||
      total.value !== 100 ||
      selected.value.some((q) => !Number.isInteger(q.points) || q.points < 1)
    ) {
      ElMessage.error('请选择题目并配置分值，总分必须为 100 分')
      return
    }
    saving.value = true
    try {
      detail.value = await api.createExam({
        ...form,
        deadline: form.deadline || undefined,
        questions: selected.value
      })
      editor.value = false
      await load()
      ElMessage.success('试卷草稿已创建')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  async function open(exam: Exam) {
    saving.value = true
    try {
      detail.value = await api.examDetail(exam.id, true)
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  async function publish(exam: Exam) {
    try {
      await ElMessageBox.confirm(
        `发布“${exam.title}”后，将为当前数据范围内的启用员工分配考试。`,
        '确认发布考试',
        { confirmButtonText: '发布并分配', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    saving.value = true
    try {
      detail.value = await api.publishExam(exam.id)
      await load()
      ElMessage.success('考试已发布并分配')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">ASSESSMENTS</span><h1>考试管理</h1
        ><p>从题库手动选题、配置分值，发布团队测评。</p></div
      ><button class="primary-button" :disabled="loading || saving" @click="create"
        >创建考试</button
      ></div
    ><div class="toolbar admin-toolbar"
      ><input v-model="keyword" aria-label="搜索考试" placeholder="搜索考试名称" /><select
        v-model="status"
        aria-label="考试状态"
        ><option value="">全部状态</option
        ><option value="DRAFT">草稿</option
        ><option value="PUBLISHED">已发布</option></select
      ><button class="outline-button" :disabled="loading || saving" @click="load">刷新</button
      ><span>共 {{ visible.length }} 场考试</span></div
    ><div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取考试…</div
    ><div v-else-if="!visible.length" class="panel empty-state">暂无符合条件的考试。</div
    ><div v-else class="admin-exam-list"
      ><article v-for="exam in visible" :key="exam.id" class="panel"
        ><div class="panel-heading"
          ><span class="course-meta">{{ exam.category }} · {{ exam.durationMinutes }} 分钟</span
          ><span class="status-pill" :class="{ draft: exam.status === 'DRAFT' }">{{
            exam.status === 'DRAFT' ? '草稿' : '已发布'
          }}</span></div
        ><h2>{{ exam.title }}</h2
        ><p class="muted"
          >{{ exam.questionCount }} 道题 · 满分 {{ exam.totalPoints }} 分 · 及格
          {{ exam.passScore }} 分</p
        ><div class="admin-exam-footer"
          ><span>截止 {{ exam.deadline }} · {{ exam.attemptCount }} 份范围内答卷</span
          ><div
            ><button class="text-button" :disabled="saving" @click="open(exam)">查看试卷</button
            ><button
              v-if="exam.status === 'DRAFT'"
              class="outline-button"
              :disabled="saving"
              @click="publish(exam)"
              >发布考试</button
            ></div
          ></div
        ></article
      ></div
    >
    <el-dialog
      v-model="editor"
      title="创建考试"
      width="1000px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><form class="admin-dialog-form" @submit.prevent="save"
        ><fieldset :disabled="saving"
          ><div class="admin-form-grid"
            ><label>考试名称<input v-model.trim="form.title" required maxlength="160" /></label
            ><label>分类<input v-model.trim="form.category" maxlength="80" /></label
            ><label
              >考试时长（分钟）<input
                v-model.number="form.durationMinutes"
                type="number"
                min="1"
                max="180"
                required /></label
            ><label
              >及格分数<input
                v-model.number="form.passScore"
                type="number"
                min="1"
                max="100"
                required /></label
            ><label
              >截止日期<input v-model="form.deadline" type="date" /><small
                >留空则默认为 30 天后</small
              ></label
            ></div
          ><div class="admin-compose-heading"
            ><h3>选择试题 · {{ selected.length }} / 50</h3
            ><button
              type="button"
              class="text-button"
              :disabled="!selected.length"
              @click="distribute"
              >均分为 100 分</button
            ><strong :class="{ danger: total !== 100 }">总分 {{ total }} / 100</strong></div
          ><input
            v-model="questionSearch"
            aria-label="搜索可选试题"
            placeholder="搜索题干，已选题目跨搜索保留"
          /><div class="admin-compose-list"
            ><label v-for="q in available" :key="q.id" class="admin-pick-question"
              ><input
                type="checkbox"
                :checked="selected.some((s) => s.questionId === q.id)"
                @change="toggle(q.id)"
              /><span
                ><small>{{ labels[q.type] }} · {{ q.category }}</small
                >{{ q.stem }}</span
              ></label
            ><p v-if="!available.length" class="empty-state"
              >没有可选题目，请先到题库创建并启用题目。</p
            ></div
          ><div v-if="selected.length" class="admin-selected-questions"
            ><div v-for="(item, i) in selected" :key="item.questionId"
              ><span>{{ i + 1 }}. {{ questions.find((q) => q.id === item.questionId)?.stem }}</span
              ><label
                >分值<input
                  v-model.number="item.points"
                  type="number"
                  min="1"
                  max="100"
                  required /></label
              ><button type="button" class="text-button" @click="toggle(item.questionId)"
                >移除</button
              ></div
            ></div
          ></fieldset
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="saving || !selected.length || total !== 100">{{
            saving ? '保存中…' : '保存试卷草稿'
          }}</button></div
        ></form
      ></el-dialog
    >
    <el-dialog
      :model-value="!!detail"
      :title="detail?.exam.title || '试卷详情'"
      width="760px"
      class="responsive-dialog"
      @update:model-value="!$event && (detail = null)"
      ><template v-if="detail"
        ><p class="muted"
          >{{ detail.exam.questionCount }} 道题 · {{ detail.exam.totalPoints }} 分 ·
          {{ detail.exam.durationMinutes }} 分钟 ·
          {{ detail.exam.status === 'DRAFT' ? '草稿' : '已发布' }}</p
        ><article v-for="(q, i) in detail.questions" :key="q.id" class="admin-paper-question"
          ><h3
            >{{ i + 1 }}. {{ q.stem }}
            <small>（{{ labels[q.type] }} · {{ q.points }} 分）</small></h3
          ><p v-for="(option, n) in q.options" :key="n"
            >{{ String.fromCharCode(65 + n) }}. {{ option }}</p
          ><p v-if="q.analysis" class="admin-analysis">解析：{{ q.analysis }}</p></article
        ><p class="muted">此处展示生成试卷时保存的题目快照。</p></template
      ></el-dialog
    >
  </section>
</template>
