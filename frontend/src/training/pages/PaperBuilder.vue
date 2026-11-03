<script setup lang="ts">
  import { computed, reactive, ref } from 'vue'
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { distributePoints, drawQuestions, questionTypeNames } from '../services/assessment'
  import type { BankQuestion, Exam, Question } from '../types'
  const s = useTraining(),
    router = useRouter(),
    route = useRoute(),
    id = route.params.id
      ? Number(route.params.id)
      : Math.max(Date.now(), ...s.data.exams.map((e) => e.id + 1))
  const existing = s.data.exams.find((e) => e.id === id),
    editable = !route.params.id || existing?.status === '草稿'
  const form = reactive({
    title: existing?.title || '',
    category: existing?.category || '通用能力',
    duration: existing?.duration || 30,
    passScore: existing?.passScore || 60,
    deadline: existing?.deadline || new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10),
    audienceDepartment: existing?.audienceDepartment || '全体部门'
  })
  const mode = ref<'manual' | 'random'>(existing?.assemblyMode || 'manual'),
    questions = ref<Question[]>(JSON.parse(JSON.stringify(existing?.questions || []))),
    keyword = ref(''),
    category = ref('全部分类'),
    difficulty = ref('全部难度'),
    type = ref('全部题型'),
    poolPage = ref(1),
    preview = ref(false)
  const counts = reactive({ single: 3, multiple: 1, boolean: 1 })
  const pool = computed(() =>
    s.data.questionBank.filter(
      (q) =>
        q.status === '启用' &&
        (category.value === '全部分类' || q.category === category.value) &&
        (difficulty.value === '全部难度' || q.difficulty === difficulty.value)
    )
  )
  const manualPool = computed(() =>
    pool.value.filter(
      (q) =>
        (type.value === '全部题型' || q.type === type.value) &&
        q.text.toLowerCase().includes(keyword.value.trim().toLowerCase())
    )
  )
  const total = computed(() => questions.value.reduce((sum, q) => sum + (q.points || 0), 0))
  const audience = computed(() =>
    s.data.employees.filter(
      (e) =>
        e.status === '在职' &&
        (form.audienceDepartment === '全体部门' || e.department === form.audienceDepartment)
    )
  )
  const signature = computed(() => JSON.stringify([form, mode.value, questions.value])),
    baseline = ref(signature.value)
  const dirty = computed(() => signature.value !== baseline.value)
  function toggle(q: BankQuestion) {
    if (questions.value.some((v) => v.id === q.id))
      questions.value = questions.value.filter((v) => v.id !== q.id)
    else {
      if (questions.value.length >= 50) return ElMessage.warning('一份试卷最多 50 道题')
      questions.value.push({ ...JSON.parse(JSON.stringify(q)), points: 10 })
    }
  }
  function move(i: number, offset: number) {
    const next = i + offset
    if (next < 0 || next >= questions.value.length) return
    ;[questions.value[i], questions.value[next]] = [questions.value[next]!, questions.value[i]!]
  }
  async function generate() {
    try {
      const result = drawQuestions(pool.value, counts)
      if (questions.value.length)
        await ElMessageBox.confirm('重新抽题将替换当前选题与分值。', '重新生成试卷', {
          confirmButtonText: '替换当前选题',
          cancelButtonText: '保留当前选题'
        })
      questions.value = result
      ElMessage.success(`已抽取 ${result.length} 道不重复试题，并分配 100 分`)
    } catch (e) {
      if (e instanceof Error) ElMessage.warning(e.message)
    }
  }
  async function save(publish: boolean) {
    try {
      if (publish)
        await ElMessageBox.confirm(
          `将向 ${audience.value.length} 名在职员工发布，共 ${questions.value.length} 题、${total.value} 分。发布后此试卷题目被冻结。`,
          '确认发布试卷',
          { confirmButtonText: '确认发布', cancelButtonText: '继续编排' }
        )
      const payload: Exam = {
        ...form,
        id,
        status: '草稿',
        questions: questions.value,
        assemblyMode: mode.value,
        participantIds: audience.value.map((e) => e.id),
        participants: audience.value.length
      }
      s.savePaper(payload, publish)
      baseline.value = signature.value
      ElMessage.success(publish ? '考试已发布，员工端可见' : '组卷草稿已保存')
      router.push('/exams')
    } catch (e) {
      if (e instanceof Error) ElMessage.warning(e.message)
    }
  }
  onBeforeRouteLeave(async () => {
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm('组卷内容尚未保存，离开将放弃修改。', '有未保存的试卷', {
        confirmButtonText: '放弃并离开',
        cancelButtonText: '继续组卷'
      })
      return true
    } catch {
      return false
    }
  })
</script>
<template>
  <div v-if="editable" class="management-page"
    ><button class="back-link" @click="router.push('/exams')"
      ><AppIcon name="left" :size="15" />返回考试管理</button
    ><PageTitle title="组卷工作台" description="从知识目标出发，组织一场有价值的测评。"
      ><el-button :disabled="!questions.length" @click="preview = true">预览试卷</el-button
      ><el-button @click="save(false)">保存草稿</el-button
      ><el-button type="primary" @click="save(true)"
        ><AppIcon name="send" :size="15" />发布考试</el-button
      ></PageTitle
    >
    <div class="paper-layout"
      ><div class="paper-main"
        ><section class="panel paper-settings"
          ><div class="panel-heading"
            ><div><span class="eyebrow">01 / ASSESSMENT DETAILS</span><h3>先定义这次测评</h3></div
            ><span class="tiny-label">总分 100 分</span></div
          ><el-form label-position="top"
            ><el-form-item label="考试名称"
              ><el-input
                v-model="form.title"
                maxlength="80"
                placeholder="例如：跨部门协作能力测评" /></el-form-item
            ><div class="form-grid"
              ><el-form-item label="考试分类"
                ><el-select v-model="form.category"
                  ><el-option
                    v-for="v in ['通用能力', '专业技能', '管理能力', '合规安全', '新人必修']"
                    :key="v"
                    :value="v" /></el-select></el-form-item
              ><el-form-item label="参考对象"
                ><el-select v-model="form.audienceDepartment"
                  ><el-option
                    v-for="v in ['全体部门', ...departments]"
                    :key="v"
                    :value="v" /></el-select></el-form-item
              ><el-form-item label="考试时长（分钟）"
                ><el-input-number v-model="form.duration" :min="1" :max="180" /></el-form-item
              ><el-form-item label="及格分数"
                ><el-input-number v-model="form.passScore" :min="1" :max="100" /></el-form-item
              ><el-form-item label="截止日期"
                ><el-date-picker
                  v-model="form.deadline"
                  type="date"
                  value-format="YYYY-MM-DD" /></el-form-item
              ><div class="paper-audience"
                ><AppIcon name="user" :size="19" /><span
                  >发布时固定当前 <b>{{ audience.length }}</b> 名在职参考员工</span
                ></div
              ></div
            ></el-form
          ></section
        >
        <section class="panel paper-selection"
          ><div class="panel-heading"
            ><div
              ><span class="eyebrow">02 / CURATE YOUR QUESTIONS</span
              ><h3>选择适合的知识检验方式</h3></div
            ></div
          ><div class="assembly-tabs"
            ><button :class="{ active: mode === 'manual' }" @click="mode = 'manual'"
              ><AppIcon name="list" :size="20" /><span
                ><b>手动选题</b><small>逐题挑选，精准编排</small></span
              ></button
            ><button :class="{ active: mode === 'random' }" @click="mode = 'random'"
              ><AppIcon name="refresh" :size="20" /><span
                ><b>规则抽题</b><small>按题型数量，随机抽取</small></span
              ></button
            ></div
          ><div class="pool-filters"
            ><el-select v-model="category" aria-label="选题知识分类" @change="poolPage = 1"
              ><el-option
                v-for="v in [
                  '全部分类',
                  '通用能力',
                  '专业技能',
                  '管理能力',
                  '合规安全',
                  '新人必修'
                ]"
                :key="v"
                :value="v" /></el-select
            ><el-select v-model="difficulty" aria-label="选题难度" @change="poolPage = 1"
              ><el-option
                v-for="v in ['全部难度', '基础', '进阶', '挑战']"
                :key="v"
                :value="v" /></el-select
          ></div>
          <template v-if="mode === 'manual'"
            ><div class="pool-search"
              ><el-input
                v-model="keyword"
                placeholder="搜索可用试题"
                clearable
                @input="poolPage = 1" /><el-select
                v-model="type"
                aria-label="手动选题题型"
                @change="poolPage = 1"
                ><el-option label="全部题型" value="全部题型" /><el-option
                  v-for="(label, key) in questionTypeNames"
                  :key="key"
                  :label="label"
                  :value="key" /></el-select></div
            ><div class="question-pool"
              ><label
                v-for="q in manualPool.slice((poolPage - 1) * 6, poolPage * 6)"
                :key="q.id"
                :class="{ selected: questions.some((v) => v.id === q.id) }"
                ><input
                  type="checkbox"
                  :checked="questions.some((v) => v.id === q.id)"
                  :aria-label="'选择试题 ' + q.id"
                  @change="toggle(q)" /><div
                  ><div
                    ><span class="question-kind" :class="q.type">{{
                      questionTypeNames[q.type]
                    }}</span
                    ><small>{{ q.category }} · {{ q.difficulty }}</small></div
                  ><p>{{ q.text }}</p></div
                ><AppIcon
                  v-if="questions.some((v) => v.id === q.id)"
                  name="check"
                  :size="16" /></label></div
            ><el-empty
              v-if="!manualPool.length"
              description="没有符合条件的启用试题"
              :image-size="80" /><div class="roster-pagination"
              ><span class="muted small">{{ manualPool.length }} 道可用试题</span
              ><el-pagination
                v-model:current-page="poolPage"
                :total="manualPool.length"
                :page-size="6"
                layout="prev,pager,next"
                small
                background /></div
          ></template>
          <div v-else class="random-rules"
            ><div v-for="(label, key) in questionTypeNames" :key="key"
              ><span class="question-kind" :class="key">{{ label }}</span
              ><span class="muted small"
                >可用 {{ pool.filter((q) => q.type === key).length }} 题</span
              ><el-input-number
                v-model="counts[key]"
                :min="0"
                :max="50"
                :aria-label="label + '抽题数量'" /></div
            ><p class="demo-note"
              >按当前分类与难度筛选，不重复抽取；题量不足时不会更改现有选题。随机仅用于前端演示，正式抽题由服务端完成。</p
            ><el-button type="primary" plain class="full-width" @click="generate"
              ><AppIcon name="refresh" :size="15" />{{
                questions.length ? '重新抽题' : '生成试卷'
              }}</el-button
            ></div
          >
        </section></div
      >
      <aside class="panel paper-outline"
        ><div class="paper-outline-top"
          ><span class="eyebrow">YOUR ASSESSMENT</span><h3>试卷编排</h3
          ><div
            ><span
              ><b>{{ questions.length }}</b
              >道试题</span
            ><span :class="{ 'score-warning': total !== 100 }"
              ><b>{{ total }}</b
              >/ 100 分</span
            ></div
          ><el-button
            :disabled="!questions.length"
            size="small"
            @click="distributePoints(questions)"
            >自动均分至 100 分</el-button
          ></div
        ><div class="paper-question-list"
          ><article v-for="(q, i) in questions" :key="q.id"
            ><div class="paper-question-title"
              ><span>{{ String(i + 1).padStart(2, '0') }}</span
              ><p>{{ q.text }}</p></div
            ><div class="paper-question-tools"
              ><span class="question-kind" :class="q.type || 'single'">{{
                questionTypeNames[q.type || 'single']
              }}</span
              ><el-input-number
                v-model="q.points"
                size="small"
                :min="1"
                :max="100"
                :aria-label="'第 ' + (i + 1) + ' 题分值'" /></div
            ><div class="paper-order"
              ><button :disabled="i === 0" :aria-label="'上移试题 ' + (i + 1)" @click="move(i, -1)"
                >上移</button
              ><button
                :disabled="i === questions.length - 1"
                :aria-label="'下移试题 ' + (i + 1)"
                @click="move(i, 1)"
                >下移</button
              ><button
                :aria-label="'移除试题 ' + (i + 1)"
                @click="questions = questions.filter((v) => v.id !== q.id)"
                >移除</button
              ></div
            ></article
          ><el-empty
            v-if="!questions.length"
            description="从左侧选题，开始编排"
            :image-size="70" /></div
        ><div class="paper-checklist"
          ><span><AppIcon :name="questions.length ? 'check' : 'info'" :size="14" />1–50 道试题</span
          ><span
            ><AppIcon :name="total === 100 ? 'check' : 'info'" :size="14" />总分
            {{ total === 100 ? '已达到' : '需设置为' }} 100 分</span
          ><span><AppIcon name="lock" :size="14" />发布后冻结题目快照</span></div
        ></aside
      ></div
    >
    <el-drawer v-model="preview" title="试卷预览（管理员可见答案）" size="720px"
      ><div class="paper-preview-heading"
        ><h2>{{ form.title || '未命名试卷' }}</h2
        ><p>{{ questions.length }} 道题 · {{ total }} 分 · {{ form.duration }} 分钟</p></div
      ><QuestionPreview
        v-for="(q, i) in questions"
        :key="q.id"
        :question="q"
        :index="i"
        show-answer
    /></el-drawer> </div
  ><el-empty v-else description="试卷不存在或已发布冻结，请新建试卷或从草稿继续编辑"
    ><el-button @click="router.push('/exams')">返回考试管理</el-button></el-empty
  >
</template>
