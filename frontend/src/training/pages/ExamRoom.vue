<script setup lang="ts">
  import { computed, onBeforeUnmount, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import type { Attempt } from '../types'
  import {
    answerIndices,
    answerText,
    isCorrect,
    scorePaper,
    questionTypeNames
  } from '../services/assessment'
  const s = useTraining(),
    route = useRoute(),
    router = useRouter(),
    current = ref(0),
    showAnalysis = ref(false),
    now = ref(Date.now())
  const id = Number(route.params.id),
    practice = route.query.practice === '1'
  const sourceExam = computed(() =>
    s.data.exams.find((e) => e.id === id && s.canAccessExam(e) && e.status !== '草稿')
  )
  const practiceAttempt = ref<Attempt>({
    examId: id,
    answers: {},
    deadline: Date.now() + 60 * 60 * 1000,
    submitted: false,
    score: 0
  })
  if (!practice && !s.isAdmin) s.beginExam(id)
  const attempt = computed(() => (practice ? practiceAttempt.value : s.attempts[id]))
  const exam = computed(
    () =>
      sourceExam.value && {
        ...sourceExam.value,
        questions: attempt.value?.questionSnapshot || sourceExam.value.questions,
        passScore: attempt.value?.passScore ?? sourceExam.value.passScore,
        title: attempt.value?.examTitle || sourceExam.value.title
      }
  )
  const question = computed(() => exam.value?.questions[current.value])
  const remaining = computed(() =>
    Math.max(0, Math.ceil(((attempt.value?.deadline || now.value) - now.value) / 1000))
  )
  const clock = computed(
    () =>
      `${String(Math.floor(remaining.value / 60)).padStart(2, '0')}:${String(remaining.value % 60).padStart(2, '0')}`
  )
  const answered = computed(() => Object.keys(attempt.value?.answers || {}).length)
  const timer = setInterval(() => {
    now.value = Date.now()
    if (attempt.value && !attempt.value.submitted && remaining.value <= 0 && !practice) {
      s.submitExam(id)
      ElMessage.info('考试时间已到，已自动交卷')
    }
  }, 1000)
  onBeforeUnmount(() => clearInterval(timer))
  function answer(index: number) {
    if (attempt.value && !attempt.value.submitted && question.value) {
      if (!practice && Date.now() > attempt.value.deadline) {
        s.submitExam(id)
        return
      }
      const q = question.value
      if (q.type === 'multiple') {
        const selected = answerIndices(attempt.value.answers[q.id])
        const updated = selected.includes(index)
          ? selected.filter((i) => i !== index)
          : [...selected, index]
        if (updated.length) attempt.value.answers[q.id] = updated
        else delete attempt.value.answers[q.id]
      } else attempt.value.answers[q.id] = index
    }
  }
  async function submit() {
    if (!attempt.value || !exam.value) return
    try {
      await ElMessageBox.confirm(
        `已作答 ${answered.value} / ${exam.value.questions.length} 题。${answered.value < exam.value.questions.length ? '未答题目将按 0 分计入。' : ''}确认提交？`,
        practice ? '结束练习' : '确认交卷',
        { confirmButtonText: '确认提交', cancelButtonText: '继续答题' }
      )
      if (practice) {
        attempt.value.score = scorePaper(exam.value.questions, attempt.value.answers)
        attempt.value.submitted = true
      } else s.submitExam(id)
    } catch {}
  }
</script>
<template>
  <div v-if="exam && attempt"
    ><button class="back-link" @click="router.push('/exams')"
      ><AppIcon name="left" :size="15" />返回考试中心</button
    ><PageTitle
      :title="exam.title"
      :description="
        practice
          ? '模拟练习不计入正式成绩，放心练习，巩固所学。'
          : '认真思考，从容作答。答案会自动保存在当前浏览器。'
      "
      ><span class="category-label">{{ practice ? '模拟练习' : '在线测评' }}</span></PageTitle
    >
    <template v-if="!attempt.submitted"
      ><div class="exam-room-layout"
        ><section class="panel answer-panel"
          ><div class="answer-heading"
            ><span class="category-label">单选题</span
            ><span class="muted">第 {{ current + 1 }} 题 / 共 {{ exam.questions.length }} 题</span
            ><small class="saved-status"
              ><AppIcon name="check" :size="13" />本地自动保存</small
            ></div
          ><h2>{{ question?.text }}</h2
          ><div class="answer-options"
            ><button
              v-for="(o, i) in question?.options"
              :key="i"
              :class="{
                selected: question && answerIndices(attempt.answers[question.id]).includes(i)
              }"
              :aria-pressed="question && answerIndices(attempt.answers[question.id]).includes(i)"
              @click="answer(i)"
              ><span>{{ String.fromCharCode(65 + i) }}</span
              ><b>{{ o }}</b
              ><AppIcon
                v-if="question && answerIndices(attempt.answers[question.id]).includes(i)"
                name="success"
                :size="20" /></button></div
          ><div class="answer-footer"
            ><el-button :disabled="current === 0" @click="current--">上一题</el-button
            ><span class="muted small">{{ answered }} / {{ exam.questions.length }} 题已作答</span
            ><el-button v-if="current < exam.questions.length - 1" type="primary" @click="current++"
              >下一题<AppIcon name="right" :size="14" /></el-button
            ><el-button v-else type="primary" @click="submit">提交答卷</el-button></div
          ></section
        ><aside class="panel answer-sheet"
          ><div class="timer-label"
            ><AppIcon name="clock" :size="18" />{{ practice ? '模拟练习' : '剩余时间' }}</div
          ><strong class="countdown" :class="{ urgent: remaining < 60 }">{{
            practice ? '自由作答' : clock
          }}</strong
          ><div class="answer-sheet-title"
            ><h3>答题卡</h3><span>{{ answered }}/{{ exam.questions.length }}</span></div
          ><div class="answer-numbers"
            ><button
              v-for="(q, i) in exam.questions"
              :key="q.id"
              :class="{ answered: attempt.answers[q.id] !== undefined, current: current === i }"
              @click="current = i"
              >{{ i + 1 }}</button
            ></div
          ><div class="answer-legend"
            ><span><i />未作答</span><span><i class="filled" />已作答</span></div
          ><el-button type="primary" class="full-width" @click="submit">{{
            practice ? '结束练习' : '提前交卷'
          }}</el-button
          ><p class="demo-note"
            >答案会自动保存，倒计时结束后系统将自动交卷。</p
          ></aside
        ></div
      ></template
    >
    <section v-else class="panel exam-result"
      ><span class="result-emblem"
        ><AppIcon :name="attempt.score >= exam.passScore ? 'medal' : 'book'" :size="54" /></span
      ><span class="eyebrow">{{ practice ? 'PRACTICE COMPLETED' : 'ASSESSMENT COMPLETED' }}</span
      ><h2>{{
        attempt.score >= exam.passScore ? '学有所成，继续向前！' : '每一次尝试，都是成长'
      }}</h2
      ><p>{{
        practice ? '练习已完成，你可以查看每道题的解析。' : '答卷已提交，成绩已记录到你的学习档案。'
      }}</p
      ><div class="big-score">{{ attempt.score }}<span>/ 100 分</span></div
      ><StatusBadge :status="attempt.score >= exam.passScore ? '已通过' : '未通过'" /><div
        class="result-actions"
        ><el-button @click="router.push('/exams')">返回考试中心</el-button
        ><el-button type="primary" @click="showAnalysis = !showAnalysis">{{
          showAnalysis ? '收起答案解析' : '查看答案解析'
        }}</el-button></div
      ><div v-if="showAnalysis" class="analysis-list"
        ><article v-for="(q, i) in exam.questions" :key="q.id"
          ><div
            ><StatusBadge :status="isCorrect(q, attempt.answers[q.id]) ? '已通过' : '未通过'" /><b
              >{{ i + 1 }}. {{ q.text }}</b
            ></div
          ><p>你的答案：{{ answerText(q, attempt.answers[q.id]) }}</p
          ><p class="correct-answer">正确答案：{{ answerText(q, q.answer) }}</p
          ><p class="muted">{{ q.analysis }}</p></article
        ></div
      ></section
    > </div
  ><el-empty v-else :description="s.isAdmin ? '请切换到员工学习端参加测评' : '考试不存在或尚未开放'"
    ><el-button @click="router.push('/exams')">返回考试中心</el-button></el-empty
  >
</template>
