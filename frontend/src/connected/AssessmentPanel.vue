<script setup lang="ts">
  import { inject, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
  import { onBeforeRouteLeave, routerKey } from 'vue-router'
  import {
    ApiError,
    api,
    currentUser,
    type Attempt,
    type AttemptDetail,
    type BankQuestion,
    type Exam,
    type ExamDetail
  } from './api'

  const props = defineProps<{ admin: boolean }>()
  const questions = ref<BankQuestion[]>([])
  const exams = ref<Exam[]>([])
  const selected = ref<ExamDetail | null>(null)
  const attempt = ref<AttemptDetail | null>(null)
  const history = ref<Attempt[]>([])
  const savedRevision = ref(0),
    revision = ref(0),
    saveFailed = ref(false)
  const loading = ref(false)
  const error = ref('')
  const notice = ref('')
  const saving = ref(false)
  const submitting = ref(false)
  const questionForm = reactive({
    type: 'single',
    stem: '',
    options: '正确\n错误',
    answer: '0',
    category: '安全合规',
    difficulty: '基础'
  })
  const examForm = reactive({
    title: '新员工阶段测评',
    category: '安全合规',
    durationMinutes: 30,
    passScore: 60,
    deadline: ''
  })
  const answers = reactive<Record<number, number[]>>({})
  let saveTimer: number | undefined
  let countdownTimer: number | undefined
  let inFlight: Promise<void> | null = null
  let deadline = 0
  let disposed = false

  function apiMessage(e: unknown) {
    error.value = e instanceof ApiError ? e.message : '考试接口暂时不可用，请确认后端已启动'
  }
  function message(text: string) {
    notice.value = text
    window.setTimeout(() => {
      if (notice.value === text) notice.value = ''
    }, 3000)
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      if (props.admin) {
        questions.value = await api.questions()
        exams.value = await api.exams(true)
      } else {
        await flushSave()
        exams.value = await api.exams()
        history.value = await api.myAttempts()
        const active = (await api.activeAttempts())[0]
        if (active) {
          const restored = await api.attemptDetail(active.id)
          setAttempt(restored)
          selected.value = await api.examDetail(restored.attempt.paperId)
          message('已恢复未完成答卷')
        }
      }
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function createQuestion() {
    const options = questionForm.options
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const answer = questionForm.answer
      .split(',')
      .map((item) => Number(item.trim()))
      .filter(Number.isInteger)
    loading.value = true
    error.value = ''
    try {
      await api.createQuestion({
        type: questionForm.type,
        stem: questionForm.stem,
        options,
        answer,
        analysis: '',
        category: questionForm.category,
        difficulty: questionForm.difficulty,
        status: 'ENABLED',
        version: 0
      })
      questionForm.stem = ''
      await load()
      message('题目已加入题库')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function createExam() {
    if (questions.value.length < 1) {
      error.value = '请先在题库中创建至少 1 道启用题目'
      return
    }
    const selectedQuestions = questions.value
      .filter((question) => question.status === 'ENABLED')
      .slice(0, 3)
    const points = selectedQuestions.map((_, index) =>
      index === selectedQuestions.length - 1
        ? 100 - Math.floor(100 / selectedQuestions.length) * (selectedQuestions.length - 1)
        : Math.floor(100 / selectedQuestions.length)
    )
    loading.value = true
    error.value = ''
    try {
      const detail = await api.createExam({
        ...examForm,
        deadline: examForm.deadline || undefined,
        questions: selectedQuestions.map((q, index) => ({
          questionId: q.id,
          points: points[index]
        }))
      })
      selected.value = detail
      await load()
      message('试卷草稿已创建，可在下方发布')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function publish(exam: Exam) {
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.publishExam(exam.id)
      await load()
      message('试卷已发布，已分配给当前数据范围内的启用员工')
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function openExam(exam: Exam) {
    loading.value = true
    error.value = ''
    try {
      selected.value = await api.examDetail(exam.id, props.admin)
      attempt.value = null
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  function setAttempt(value: AttemptDetail) {
    attempt.value = value
    deadline = Date.now() + value.remainingSeconds * 1000
    revision.value = 0
    savedRevision.value = 0
    saveFailed.value = false
    Object.keys(answers).forEach((key) => {
      delete answers[Number(key)]
    })
    value.questions.forEach((question) => {
      answers[question.id] = value.answers?.[question.id] ?? []
    })
    startCountdown()
  }
  function answerPayload() {
    return (
      attempt.value?.questions.map((question) => ({
        paperQuestionId: question.id,
        answer: answers[question.id] ?? []
      })) ?? []
    )
  }
  async function saveAnswers(): Promise<void> {
    if (inFlight) return inFlight
    if (
      !attempt.value ||
      attempt.value.attempt.status === 'SUBMITTED' ||
      revision.value === savedRevision.value
    )
      return
    const snapshotRevision = revision.value,
      id = attempt.value.attempt.id,
      payload = answerPayload()
    saving.value = true
    saveFailed.value = false
    inFlight = (async () => {
      try {
        const saved = await api.saveAttemptAnswers(id, payload)
        if (disposed || attempt.value?.attempt.id !== id) return
        error.value = ''
        if (saved.attempt.status === 'SUBMITTED') setAttempt(saved)
        else {
          attempt.value = {
            ...attempt.value,
            attempt: saved.attempt,
            remainingSeconds: saved.remainingSeconds
          }
          deadline = Date.now() + saved.remainingSeconds * 1000
          savedRevision.value = snapshotRevision
        }
      } catch (e) {
        saveFailed.value = true
        apiMessage(e)
        throw e
      } finally {
        saving.value = false
        inFlight = null
      }
    })()
    return inFlight
  }
  function scheduleSave() {
    if (props.admin || !attempt.value || attempt.value.attempt.status === 'SUBMITTED') return
    if (saveTimer) window.clearTimeout(saveTimer)
    saveTimer = window.setTimeout(() => {
      void saveAnswers()
        .then(() => {
          if (revision.value !== savedRevision.value) scheduleSave()
        })
        .catch(() => {})
    }, 600)
  }
  async function flushSave() {
    if (saveTimer) {
      window.clearTimeout(saveTimer)
      saveTimer = undefined
    }
    if (inFlight) await inFlight
    while (
      attempt.value?.attempt.status === 'IN_PROGRESS' &&
      revision.value !== savedRevision.value
    )
      await saveAnswers()
  }
  async function review(id: number) {
    loading.value = true
    error.value = ''
    try {
      await flushSave()
      setAttempt(await api.attemptDetail(id))
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function openEmployeeExam(exam: Exam) {
    const previous = history.value.find((item) => item.paperId === exam.id)
    if (previous) await review(previous.id)
    else await begin(exam)
  }
  async function closeAttempt() {
    try {
      await flushSave()
      attempt.value = null
      history.value = await api.myAttempts()
    } catch (e) {
      apiMessage(e)
    }
  }
  async function begin(exam: Exam) {
    loading.value = true
    error.value = ''
    try {
      setAttempt(await api.beginExam(exam.id))
      selected.value = {
        exam: exams.value.find((item) => item.id === exam.id) ?? exam,
        questions: attempt.value!.questions
      }
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
  }
  function choose(questionId: number, index: number, multiple: boolean) {
    const current = answers[questionId] ?? []
    answers[questionId] = multiple
      ? current.includes(index)
        ? current.filter((item) => item !== index)
        : [...current, index].sort()
      : [index]
    revision.value += 1
    scheduleSave()
  }
  async function submit(auto = false) {
    if (!attempt.value || attempt.value.attempt.status === 'SUBMITTED' || submitting.value) return
    submitting.value = true
    error.value = ''
    try {
      await flushSave()
      const result = await api.submitAttempt(attempt.value.attempt.id, answerPayload())
      attempt.value = { ...attempt.value, attempt: result, remainingSeconds: 0 }
      setAttempt(await api.attemptDetail(result.id))
      history.value = await api.myAttempts()
      if (auto) {
        message('考试时间已到，系统已自动交卷')
      } else {
        message(`答卷已提交，本次得分 ${result.score ?? 0} 分`)
      }
    } catch (e) {
      apiMessage(e)
    } finally {
      submitting.value = false
    }
  }
  function startCountdown() {
    if (countdownTimer) window.clearInterval(countdownTimer)
    countdownTimer = window.setInterval(() => {
      if (!attempt.value || attempt.value.attempt.status === 'SUBMITTED') return
      attempt.value.remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      if (attempt.value.remainingSeconds === 0) void submit(true)
    }, 1000)
  }
  const router = inject(routerKey, undefined)
  if (router)
    onBeforeRouteLeave(async () => {
      if (!currentUser.value) return true
      try {
        await flushSave()
        return true
      } catch {
        return false
      }
    })
  function beforeUnload(event: BeforeUnloadEvent) {
    if (revision.value !== savedRevision.value && attempt.value?.attempt.status === 'IN_PROGRESS') {
      event.preventDefault()
      event.returnValue = ''
    }
  }
  onMounted(() => {
    void load()
    window.addEventListener('beforeunload', beforeUnload)
  })
  onBeforeUnmount(() => {
    disposed = true
    window.removeEventListener('beforeunload', beforeUnload)
    if (saveTimer) window.clearTimeout(saveTimer)
    if (countdownTimer) window.clearInterval(countdownTimer)
  })
</script>

<template>
  <section class="page-section assessment-panel">
    <div class="section-heading"
      ><div
        ><span class="eyebrow">ASSESSMENT / MY PROGRESS</span
        ><h1>{{ admin ? '题库与试卷' : '我的考试' }}</h1
        ><p>{{
          admin
            ? '维护题目、生成试卷，发布时分配给当前数据范围内的启用员工。'
            : '完成分配的考试，查看答卷与成绩。'
        }}</p></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新数据</button></div
    >
    <div v-if="notice" class="toast">{{ notice }}</div
    ><div v-if="error" class="api-alert"
      ><b>考试接口</b><span>{{ error }}</span
      ><button class="text-button" @click="attempt ? flushSave().catch(() => {}) : load()"
        >重试</button
      ></div
    >
    <div v-if="loading && !questions.length && !exams.length" class="empty-state"
      >正在读取题库与考试…</div
    >
    <template v-else-if="admin">
      <div class="assessment-columns">
        <div class="panel"
          ><div class="panel-heading"
            ><h2>题库（{{ questions.length }}）</h2></div
          ><div class="question-list"
            ><article v-for="question in questions" :key="question.id" class="question-card"
              ><span class="course-meta"
                >{{ question.type }} · {{ question.category }} · {{ question.difficulty }}</span
              ><h3>{{ question.stem }}</h3
              ><p>{{ question.options.join(' / ') }}</p></article
            ><p v-if="!questions.length" class="muted">暂无题目</p></div
          ><form class="compact-form" @submit.prevent="createQuestion"
            ><h3>新增题目</h3
            ><label
              >题型<select v-model="questionForm.type"
                ><option value="single">单选</option
                ><option value="multiple">多选</option
                ><option value="boolean">判断</option></select
              ></label
            ><label
              >题干<input v-model="questionForm.stem" required placeholder="请输入题干" /></label
            ><label
              >选项（每行一个）<textarea
                v-model="questionForm.options"
                rows="4"
                required
              ></textarea></label
            ><label
              >答案索引（从 0 开始，多选用逗号）<input
                v-model="questionForm.answer"
                required /></label
            ><button class="primary-button compact" :disabled="loading">保存题目</button></form
          ></div
        >
        <div class="panel"
          ><div class="panel-heading"
            ><h2>试卷（{{ exams.length }}）</h2></div
          ><div class="exam-list"
            ><article v-for="exam in exams" :key="exam.id" class="exam-row"
              ><div
                ><b>{{ exam.title }}</b
                ><small
                  >{{ exam.category }} · {{ exam.questionCount }} 题 · {{ exam.totalPoints }} 分 ·
                  截止 {{ exam.deadline }}</small
                ></div
              ><div
                ><span class="status-pill">{{
                  exam.status === 'PUBLISHED' ? '已发布' : '草稿'
                }}</span
                ><button v-if="exam.status === 'DRAFT'" class="text-button" @click="publish(exam)"
                  >发布</button
                ><button class="text-button" @click="openExam(exam)">查看</button></div
              ></article
            ><p v-if="!exams.length" class="muted">暂无试卷</p></div
          ><form class="compact-form" @submit.prevent="createExam"
            ><h3>从题库生成试卷</h3
            ><label>试卷标题<input v-model="examForm.title" required /></label
            ><label
              >考试时长<input
                v-model.number="examForm.durationMinutes"
                type="number"
                min="1" /></label
            ><label>截止日期<input v-model="examForm.deadline" type="date" /></label
            ><button class="outline-button compact" :disabled="loading"
              >创建 100 分草稿</button
            ></form
          ></div
        >
      </div>
    </template>
    <template v-else>
      <div v-if="!attempt" class="exam-list standalone-exams"
        ><article v-for="exam in exams" :key="exam.id" class="exam-card"
          ><div
            ><span class="course-meta">{{ exam.category }} · {{ exam.durationMinutes }} 分钟</span
            ><h3>{{ exam.title }}</h3
            ><p
              >共 {{ exam.questionCount }} 题，满分 {{ exam.totalPoints }}，及格线
              {{ exam.passScore }} · 截止 {{ exam.deadline }}</p
            ></div
          ><button
            class="primary-button compact"
            :disabled="loading"
            @click="openEmployeeExam(exam)"
            >{{ exam.attemptCount ? '查看答卷' : '开始考试' }}</button
          ></article
        ><p v-if="!exams.length" class="empty-state">暂无待参加考试</p></div
      >
      <div v-else class="panel exam-taking"
        ><button class="text-button" :disabled="submitting" @click="closeAttempt"
          >← 返回考试列表</button
        ><div class="panel-heading"
          ><div
            ><span class="eyebrow">{{ attempt.attempt.title }}</span
            ><h2>{{
              attempt.attempt.status === 'SUBMITTED'
                ? `已提交 · ${attempt.attempt.score ?? 0} 分`
                : '答题中'
            }}</h2></div
          ><div v-if="attempt.attempt.status !== 'SUBMITTED'"
            ><span class="status-pill"
              >剩余 {{ Math.ceil(attempt.remainingSeconds / 60) }} 分钟</span
            ><small class="save-hint">{{
              saving
                ? '正在保存…'
                : saveFailed
                  ? '保存失败，请重试'
                  : revision !== savedRevision
                    ? '有答案待保存'
                    : '答案已保存'
            }}</small></div
          ></div
        ><article
          v-for="(question, number) in attempt.questions"
          :key="question.id"
          class="taking-question"
          ><b>{{ number + 1 }}. {{ question.stem }}（{{ question.points }} 分）</b
          ><label v-for="(option, index) in question.options" :key="option" class="option-line"
            ><input
              :type="question.type === 'multiple' ? 'checkbox' : 'radio'"
              :name="`q-${question.id}`"
              :checked="(answers[question.id] ?? []).includes(index)"
              :disabled="attempt.attempt.status === 'SUBMITTED' || submitting || loading"
              @change="choose(question.id, index, question.type === 'multiple')"
            />{{ option }}</label
          ><p v-if="attempt.attempt.status === 'SUBMITTED' && question.analysis" class="analysis"
            >解析：{{ question.analysis }}</p
          ></article
        ><button
          v-if="attempt.attempt.status !== 'SUBMITTED'"
          class="primary-button"
          :disabled="loading || submitting"
          @click="submit()"
          >{{ submitting ? '正在提交…' : '提交答卷' }}</button
        ></div
      >
      <div v-if="!attempt && history.length" class="panel attempt-history"
        ><h2>答卷记录</h2
        ><article v-for="record in history" :key="record.id" class="exam-row"
          ><div
            ><b>{{ record.title }}</b
            ><small>{{
              record.status === 'SUBMITTED'
                ? `${record.score ?? 0} 分 · ${record.passed ? '已通过' : '未通过'}`
                : '答题中'
            }}</small></div
          ><button class="text-button" @click="review(record.id)">{{
            record.status === 'SUBMITTED' ? '查看结果' : '继续答题'
          }}</button></article
        ></div
      >
    </template>
    <div v-if="selected && admin" class="detail-panel assessment-detail"
      ><div class="panel-heading"
        ><h2>{{ selected.exam.title }}</h2
        ><button class="text-button" @click="selected = null">关闭</button></div
      ><p class="muted"
        >{{ selected.questions.length }} 道题 · 总分 {{ selected.exam.totalPoints }} ·
        {{ selected.exam.status === 'PUBLISHED' ? '已发布' : '草稿' }}</p
      ><ol
        ><li v-for="question in selected.questions" :key="question.id"
          >{{ question.stem }} <span class="course-meta">{{ question.points }} 分</span></li
        ></ol
      ></div
    >
  </section>
</template>
