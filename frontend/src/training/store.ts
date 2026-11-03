import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { trainingRepository } from './api/repository'
import { createSeed } from './api/seed'
import { upgradeTrainingData, emptyLearning } from './api/migrate'
import { checkDemoPassword } from './services/demo-auth'
import type {
  Attempt,
  Role,
  TrainingData,
  DemoAccount,
  CourseReview,
  CourseChapter,
  Plan,
  Exam,
  BankQuestion
} from './types'
import { curriculumError } from './services/curriculum'
import { questionError, scorePaper } from './services/assessment'
export const useTraining = defineStore('training', () => {
  const data = ref<TrainingData>(trainingRepository.load())
  const role = ref<Role>('admin')
  const signedIn = ref(true)
  const employeeId = ref(1)
  try {
    role.value = localStorage.getItem('zhixing-role') === 'employee' ? 'employee' : 'admin'
    signedIn.value = localStorage.getItem('zhixing-signed-in') !== 'false'
    const savedId = Number(localStorage.getItem('zhixing-employee-id') || 1)
    if (data.value.employees.some((e) => e.id === savedId)) employeeId.value = savedId
  } catch {
    /* storage can be disabled */
  }
  let warned = false
  watch(
    data,
    (d) => {
      if (!trainingRepository.save(d) && !warned) {
        warned = true
        ElMessage.warning('浏览器存储不可用，本次操作仅在当前页面保留')
      }
    },
    { deep: true, flush: 'post' }
  )
  window.addEventListener('pagehide', () => trainingRepository.save(data.value))
  watch(role, (r) => {
    try {
      localStorage.setItem('zhixing-role', r)
    } catch {}
  })
  watch(employeeId, (id) => {
    try {
      localStorage.setItem('zhixing-employee-id', String(id))
    } catch {}
  })
  watch(signedIn, (v) => {
    try {
      localStorage.setItem('zhixing-signed-in', String(v))
    } catch {}
  })
  const isAdmin = computed(() => role.value === 'admin')
  const profile = computed(
    () => data.value.employees.find((e) => e.id === employeeId.value) || data.value.employees[0]!
  )
  const currentAccount = computed(() =>
    data.value.accounts.find((a) =>
      isAdmin.value ? a.role === 'admin' : a.employeeId === employeeId.value
    )
  )
  const displayName = computed(() =>
    isAdmin.value ? data.value.settings.admin : profile.value?.name || '员工'
  )
  const myLearning = computed(() => data.value.learning[employeeId.value] || {})
  const attempts = computed(() => data.value.employeeAttempts[employeeId.value] || {})
  const allAttempts = computed(() =>
    Object.values(data.value.employeeAttempts).flatMap((a) => Object.values(a))
  )
  const progressOf = (id: number) => myLearning.value[id]?.progress || 0
  const courseViews = computed(() =>
    data.value.courses.map((c) => (isAdmin.value ? c : { ...c, progress: progressOf(c.id) }))
  )
  const unread = computed(() => data.value.notices.filter((n) => !n.read).length)
  const published = computed(() => courseViews.value.filter((c) => c.status === '已发布'))
  const totalHours = computed(() => data.value.employees.reduce((sum, e) => sum + e.hours, 0))
  const planProgress = (plan: Plan, id = employeeId.value) => {
    const complete =
      plan.courseIds.length > 0 &&
      plan.courseIds.every((cid) => data.value.learning[id]?.[cid]?.progress === 100)
    const average = Math.round(
      plan.courseIds.reduce(
        (sum, cid) => sum + (data.value.learning[id]?.[cid]?.progress || 0),
        0
      ) / Math.max(1, plan.courseIds.length)
    )
    return complete ? 100 : Math.min(99, average)
  }
  const planViews = computed(() =>
    data.value.plans.map((p) =>
      p.participantIds
        ? {
            ...p,
            participants: p.participantIds.length,
            completed: p.participantIds.filter((id) => planProgress(p, id) === 100).length
          }
        : p
    )
  )
  const canAccessPlan = (p: Plan) =>
    isAdmin.value ||
    (p.status !== '草稿' &&
      (p.participantIds
        ? p.participantIds.includes(employeeId.value)
        : p.department === '全体部门' || p.department === profile.value.department))
  const accessiblePlans = computed(() => planViews.value.filter(canAccessPlan))
  const canAccessExam = (e: Exam) =>
    isAdmin.value ||
    (e.status !== '草稿' && (!e.participantIds || e.participantIds.includes(employeeId.value)))
  const accessibleExams = computed(() => data.value.exams.filter(canAccessExam))
  const completionRate = computed(() =>
    Math.round(
      (planViews.value.filter((p) => p.status !== '草稿').reduce((s, p) => s + p.completed, 0) /
        Math.max(
          1,
          planViews.value.filter((p) => p.status !== '草稿').reduce((s, p) => s + p.participants, 0)
        )) *
        100
    )
  )
  function login(r: Role, id = 1) {
    // Explicitly labelled quick demo entrance, intentionally not an authentication boundary.
    role.value = r
    employeeId.value = id
    signedIn.value = true
  }
  async function loginWithPassword(no: string, password: string) {
    const account = data.value.accounts.find((a) => a.no.toUpperCase() === no.trim().toUpperCase())
    if (!account || !account.active) throw new Error('工号未激活或不存在，请检查后重试')
    if (
      account.employeeId &&
      data.value.employees.find((e) => e.id === account.employeeId)?.status !== '在职'
    )
      throw new Error('当前账号已停用，请联系培训管理员')
    if (!(await checkDemoPassword(account, password))) throw new Error('工号或密码不正确')
    login(account.role, account.employeeId || 1)
  }
  function accountCandidate(no: string): DemoAccount | undefined {
    const normalized = no.trim().toUpperCase()
    const existing = data.value.accounts.find((a) => a.no.toUpperCase() === normalized)
    if (existing) return existing
    const employee = data.value.employees.find(
      (e) => e.no.toUpperCase() === normalized && e.status === '在职'
    )
    return employee
      ? {
          no: employee.no,
          employeeId: employee.id,
          role: 'employee',
          active: false,
          passwordHash: '',
          salt: ''
        }
      : undefined
  }
  function enrollCourse(id: number) {
    if (isAdmin.value) throw new Error('请切换到员工端报名')
    const course = data.value.courses.find((c) => c.id === id && c.status === '已发布')
    if (!course) throw new Error('课程已下架或不存在')
    data.value.learning[employeeId.value] ??= {}
    if (!myLearning.value[id]) {
      data.value.learning[employeeId.value]![id] = emptyLearning(id)
      course.learners++
      updateLearningSummary()
    }
    return myLearning.value[id]!
  }
  function updateLearningSummary(id = employeeId.value) {
    const records = Object.values(data.value.learning[id] || {}),
      employee = data.value.employees.find((e) => e.id === id)
    if (!employee || !records.length) return
    employee.progress = Math.round(records.reduce((sum, r) => sum + r.progress, 0) / records.length)
    employee.hours = Number((records.reduce((sum, r) => sum + r.seconds, 0) / 3600).toFixed(2))
  }
  function completeLesson(id: number, index: number) {
    const r = myLearning.value[id],
      c = data.value.courses.find((c) => c.id === id)
    if (isAdmin.value || !r || !c || c.status !== '已发布') throw new Error('请先报名可用课程')
    if (!Number.isInteger(index) || index < 0 || index >= c.lessons) throw new Error('章节不存在')
    if (!r.completedLessons.includes(index)) r.completedLessons.push(index)
    r.progress = Math.max(
      r.progress,
      Math.round((r.completedLessons.filter((i) => i < c.lessons).length / c.lessons) * 100)
    )
    r.lastStudiedAt = new Date().toISOString()
    r.lastChapter = Math.min(index + 1, c.lessons - 1)
    if (r.progress === 100 && !r.completedAt) r.completedAt = r.lastStudiedAt
    updateLearningSummary()
  }
  function recordStudyTime(id: number, seconds: number) {
    const r = myLearning.value[id]
    if (isAdmin.value || !r || seconds <= 0 || !Number.isFinite(seconds)) return
    r.seconds += Math.min(30, Math.floor(seconds))
    r.lastStudiedAt = new Date().toISOString()
    updateLearningSummary()
  }
  function saveReview(courseId: number, rating: number, content: string) {
    if (isAdmin.value || progressOf(courseId) !== 100) throw new Error('完成课程后即可评价')
    if (
      !Number.isInteger(rating) ||
      rating < 1 ||
      rating > 5 ||
      !content.trim() ||
      content.trim().length > 500
    )
      throw new Error('请选择 1–5 星并填写 1–500 字评价')
    const existing = data.value.reviews.find(
      (r) => r.courseId === courseId && r.employeeId === employeeId.value
    )
    const now = new Date().toISOString()
    if (existing) Object.assign(existing, { rating, content: content.trim(), updatedAt: now })
    else
      data.value.reviews.unshift({
        id: crypto.randomUUID(),
        courseId,
        employeeId: employeeId.value,
        rating,
        content: content.trim(),
        createdAt: now,
        updatedAt: now
      } satisfies CourseReview)
  }
  function reset() {
    data.value = upgradeTrainingData(createSeed(), false)
    employeeId.value = 1
    ElMessage.success('演示数据已恢复')
  }
  function beginExam(id: number) {
    if (isAdmin.value) return
    const exam = data.value.exams.find((e) => e.id === id)
    if (!exam || !canAccessExam(exam)) return
    if (attempts.value[id]?.submitted) return attempts.value[id]
    if (exam.status !== '进行中' || Date.now() > new Date(exam.deadline + 'T23:59:59').getTime())
      return
    data.value.employeeAttempts[employeeId.value] ??= {}
    if (!attempts.value[id])
      data.value.employeeAttempts[employeeId.value]![id] = {
        employeeId: employeeId.value,
        examId: id,
        answers: {},
        deadline: Math.min(
          Date.now() + exam.duration * 60000,
          new Date(exam.deadline + 'T23:59:59').getTime()
        ),
        submitted: false,
        score: 0,
        questionSnapshot: JSON.parse(JSON.stringify(exam.questions)),
        passScore: exam.passScore,
        examTitle: exam.title
      }
    return attempts.value[id]
  }
  function submitExam(id: number): Attempt | undefined {
    if (isAdmin.value) return
    const attempt = attempts.value[id]
    const exam = data.value.exams.find((e) => e.id === id)
    if (!attempt || !exam || attempt.submitted) return attempt
    attempt.score = scorePaper(attempt.questionSnapshot || exam.questions, attempt.answers)
    attempt.submitted = true
    attempt.submittedAt = new Date().toISOString()
    return attempt
  }
  function requireAdmin() {
    if (!isAdmin.value) throw Error('此操作仅限管理员演示角色')
  }
  const curriculumLocked = (id: number) =>
    Object.values(data.value.learning).some((records) => !!records[id])
  function saveCurriculum(id: number, chapters: CourseChapter[], publish = false) {
    requireAdmin()
    const course = data.value.courses.find((c) => c.id === id)
    if (!course) throw Error('课程不存在')
    const invalid = curriculumError(chapters, publish || course.status === '已发布')
    if (invalid) throw Error(invalid)
    if (
      curriculumLocked(id) &&
      JSON.stringify(chapters.map((c) => c.id)) !==
        JSON.stringify(course.chapters?.map((c) => c.id))
    )
      throw Error('课程已有报名，为保留学习记录，不能增删或重排章节')
    course.chapters = JSON.parse(JSON.stringify(chapters))
    course.lessons = chapters.length
    course.duration = chapters.reduce((sum, c) => sum + c.duration, 0)
    if (publish) course.status = '已发布'
  }
  function assignPlan(plan: Plan) {
    if (plan.status === '草稿') return
    for (const id of plan.participantIds || []) {
      if (data.value.employees.find((e) => e.id === id)?.status !== '在职') continue
      data.value.learning[id] ??= {}
      for (const cid of plan.courseIds) {
        const c = data.value.courses.find((c) => c.id === cid && c.status === '已发布')
        if (c && !data.value.learning[id]![cid]) {
          data.value.learning[id]![cid] = emptyLearning(cid)
          c.learners++
        }
      }
      updateLearningSummary(id)
    }
  }
  function saveRoster(id: number, ids: number[]) {
    requireAdmin()
    const p = data.value.plans.find((p) => p.id === id)
    if (!p) throw Error('培训计划不存在')
    const unique = [...new Set(ids)]
    if (!unique.length && p.status !== '草稿') throw Error('已发布计划至少保留一名参训员工')
    if (unique.some((id) => !data.value.employees.some((e) => e.id === id)))
      throw Error('名单中含有不存在的员工')
    if (
      unique.some(
        (id) =>
          data.value.employees.find((e) => e.id === id)?.status !== '在职' &&
          !p.participantIds?.includes(id)
      )
    )
      throw Error('不能新增已停用的参训员工')
    p.participantIds = unique
    p.participants = unique.length
    p.rosterUpdatedAt = new Date().toISOString()
    assignPlan(p)
  }
  function saveBankQuestion(question: BankQuestion) {
    requireAdmin()
    const invalid = questionError(question)
    if (invalid) throw Error(invalid)
    const old = data.value.questionBank.find((q) => q.id === question.id)
    const payload = JSON.parse(
      JSON.stringify({
        ...question,
        text: question.text.trim(),
        options: question.options.map((o) => o.trim())
      })
    )
    if (old) Object.assign(old, payload)
    else data.value.questionBank.unshift(payload)
  }
  function savePaper(exam: Exam, publish: boolean) {
    requireAdmin()
    const old = data.value.exams.find((e) => e.id === exam.id)
    if (old && old.status !== '草稿') throw Error('已发布试卷已冻结，请创建新的试卷')
    if (!exam.title.trim() || exam.title.length > 80) throw Error('请填写 1–80 字考试名称')
    if (
      !Number.isInteger(exam.duration) ||
      exam.duration < 1 ||
      exam.duration > 180 ||
      !Number.isInteger(exam.passScore) ||
      exam.passScore < 1 ||
      exam.passScore > 100
    )
      throw Error('请检查考试时长和及格分')
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(exam.deadline) ||
      !Number.isFinite(new Date(exam.deadline + 'T23:59:59').getTime())
    )
      throw Error('请选择有效的截止日期')
    if (publish) {
      if (new Date(exam.deadline + 'T23:59:59').getTime() < Date.now())
        throw Error('考试截止日期不能早于今天')
      if (!exam.questions.length || exam.questions.length > 50)
        throw Error('发布试卷需有 1–50 道题')
      if (new Set(exam.questions.map((q) => q.id)).size !== exam.questions.length)
        throw Error('试卷不能包含重复试题')
      for (const [i, q] of exam.questions.entries()) {
        const error = questionError(q)
        if (error) throw Error(`第 ${i + 1} 题：${error}`)
        if (!Number.isInteger(q.points) || q.points! < 1)
          throw Error(`第 ${i + 1} 题分值须为正整数`)
      }
      if (exam.questions.reduce((sum, q) => sum + (q.points || 0), 0) !== 100)
        throw Error('试卷总分必须为 100 分，可使用自动均分')
      if (
        !exam.participantIds?.length ||
        exam.participantIds.some(
          (id) => data.value.employees.find((e) => e.id === id)?.status !== '在职'
        )
      )
        throw Error('请至少选择一名在职参考员工')
    }
    const payload = JSON.parse(
      JSON.stringify({
        ...exam,
        title: exam.title.trim(),
        status: publish ? '进行中' : '草稿',
        participants: exam.participantIds?.length || 0
      })
    ) as Exam
    if (old) Object.assign(old, payload)
    else data.value.exams.unshift(payload)
  }
  return {
    data,
    role,
    signedIn,
    isAdmin,
    displayName,
    unread,
    published,
    totalHours,
    completionRate,
    login,
    reset,
    beginExam,
    submitExam,
    employeeId,
    profile,
    currentAccount,
    myLearning,
    attempts,
    allAttempts,
    courseViews,
    progressOf,
    loginWithPassword,
    accountCandidate,
    enrollCourse,
    completeLesson,
    recordStudyTime,
    saveReview,
    planProgress,
    planViews,
    accessiblePlans,
    canAccessPlan,
    accessibleExams,
    canAccessExam,
    curriculumLocked,
    saveCurriculum,
    assignPlan,
    saveRoster,
    saveBankQuestion,
    savePaper
  }
})
