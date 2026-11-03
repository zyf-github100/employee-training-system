<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useTraining } from '../store'
  import { exportCsv } from '../api/repository'
  import type { Exam } from '../types'
  import QuestionPreview from '../components/QuestionPreview.vue'
  const s = useTraining(),
    router = useRouter(),
    keyword = ref(''),
    tab = ref('考试任务'),
    resultOpen = ref(false),
    paperOpen = ref(false),
    selectedExam = ref<Exam>()
  const statusOf = (e: Exam) =>
    e.status === '进行中' && Date.now() > new Date(e.deadline + 'T23:59:59').getTime()
      ? '已结束'
      : e.status
  const filtered = computed(() =>
    s.accessibleExams.filter(
      (e) =>
        (tab.value === '组卷草稿' ? e.status === '草稿' : e.status !== '草稿') &&
        e.title.includes(keyword.value.trim())
    )
  )
  const results = computed(() =>
    (s.isAdmin ? s.allAttempts : Object.values(s.attempts))
      .filter((a) => a.submitted)
      .map((a) => ({
        id: a.employeeId + '-' + a.examId,
        examId: a.examId,
        title: a.examTitle || s.data.exams.find((e) => e.id === a.examId)?.title || '历史考试',
        name: s.data.employees.find((e) => e.id === a.employeeId)?.name || '员工',
        score: a.score,
        passed:
          a.score >= (a.passScore ?? s.data.exams.find((e) => e.id === a.examId)?.passScore ?? 60),
        date: a.submittedAt?.slice(0, 10)
      }))
  )
  function action(e: Exam) {
    if (!s.beginExam(e.id)) {
      ElMessage.info('考试未开放或已截止')
      return
    }
    router.push('/exam/' + e.id)
  }
  function exportResults() {
    exportCsv(
      '考试成绩.csv',
      ['员工', '考试名称', '成绩', '结果', '提交日期'],
      results.value.map((r) => [r.name, r.title, r.score, r.passed ? '通过' : '未通过', r.date])
    )
  }
</script>
<template>
  <div>
    <PageTitle
      :title="s.isAdmin ? '考试管理' : '考试与练习'"
      :description="
        s.isAdmin
          ? '从题库到试卷，从作答到结果，让每一次测评都有据可循。'
          : '查看待完成考试和历史成绩，按截止时间完成测评。'
      "
    >
      <el-button v-if="s.isAdmin" @click="resultOpen = true">成绩管理</el-button>
      <el-button v-if="s.isAdmin" @click="router.push('/question-bank')">题库管理</el-button>
      <el-button v-if="s.isAdmin" type="primary" @click="router.push('/exams/compose')"
        ><AppIcon name="plus" :size="15" />创建试卷</el-button
      >
    </PageTitle>
    <div v-if="s.isAdmin" class="exam-intro"
      ><span class="exam-intro-icon"><AppIcon name="exam" :size="36" /></span
      ><div
        ><h2>{{ s.isAdmin ? '让知识的掌握，清晰可见' : '考试任务与成绩' }}</h2
        ><p>{{
          s.isAdmin
            ? '独立题库 · 灵活组卷 · 发布快照 · 多题型测评'
            : '正式考试计入培训档案，模拟练习用于熟悉题型。'
        }}</p></div
      ><div
        ><strong>{{ s.accessibleExams.filter((e) => statusOf(e) === '进行中').length }}</strong
        ><span>场测评正在进行</span></div
      ></div
    >
    <section v-else class="employee-exam-summary"
      ><div><span class="eyebrow">考试中心</span><h2>先完成有截止时间的考试</h2><p>正式考试会记录成绩，模拟练习不会影响培训档案。</p></div
      ><div><b>{{ s.accessibleExams.filter((e) => statusOf(e) === '进行中' && !s.attempts[e.id]?.submitted).length }}</b><span>待完成</span></div
      ><div><b>{{ results.length }}</b><span>已有成绩</span></div
    ></section>
    <section class="panel list-toolbar"
      ><div class="content-tabs"
        ><button
          v-for="v in s.isAdmin ? ['考试任务', '组卷草稿'] : ['考试任务', '模拟练习', '我的成绩']"
          :key="v"
          :class="{ active: tab === v }"
          @click="tab = v"
          >{{ v
          }}<span v-if="v === '组卷草稿'" class="tab-count">{{
            s.data.exams.filter((e) => e.status === '草稿').length
          }}</span></button
        ></div
      >
      <el-input
        v-if="tab !== '我的成绩'"
        v-model="keyword"
        clearable
        placeholder="搜索考试名称"
        style="width: 240px"
        ><template #prefix><AppIcon name="search" :size="16" /></template></el-input
    ></section>
    <div v-if="tab !== '我的成绩'" class="exam-grid">
      <article v-for="(e, i) in filtered" :key="e.id" class="panel exam-card">
        <div class="exam-card-top"
          ><span class="stat-icon" :class="['teal', 'blue', 'violet'][i % 3]"
            ><AppIcon name="exam" :size="25" /></span
          ><StatusBadge
            :status="!s.isAdmin && s.attempts[e.id]?.submitted ? '已完成' : statusOf(e)"
        /></div>
        <span class="eyebrow"
          >{{ e.category }} ·
          {{ tab === '模拟练习' ? '模拟练习' : e.status === '草稿' ? '草稿' : '正式考试' }}</span
        ><h3>{{ e.title }}</h3>
        <p class="muted">{{
          e.status === '草稿'
            ? '继续编排试题与分值，发布后员工即可作答。'
            : '从理解到应用，检验你的学习成果。'
        }}</p>
        <div class="exam-attributes"
          ><span><AppIcon name="clock" :size="15" />{{ e.duration }} 分钟</span
          ><span><AppIcon name="ticket" :size="15" />{{ e.questions.length }} 道题</span
          ><span><AppIcon name="target" :size="15" />{{ e.passScore }} 分及格</span></div
        >
        <div class="exam-deadline"
          ><span>截止日期</span><b>{{ e.deadline }}</b></div
        >
        <div v-if="s.isAdmin" class="exam-card-footer"
          ><span class="muted">{{ e.participantIds?.length ?? e.participants }} 名参考学员</span
          ><button
            v-if="e.status === '草稿'"
            class="text-link"
            @click="router.push('/exams/compose/' + e.id)"
            >继续组卷<AppIcon name="right" :size="13" /></button
          ><button
            v-else
            class="text-link"
            @click="
              () => {
                selectedExam = e
                paperOpen = true
              }
            "
            >查看试卷<AppIcon name="right" :size="13" /></button
        ></div>
        <el-button
          v-else
          type="primary"
          :plain="tab === '模拟练习' || !!s.attempts[e.id]?.submitted"
          class="full-width"
          :disabled="tab !== '模拟练习' && !s.attempts[e.id]?.submitted && statusOf(e) !== '进行中'"
          @click="tab === '模拟练习' ? router.push('/exam/' + e.id + '?practice=1') : action(e)"
          >{{
            tab === '模拟练习'
              ? '开始模拟练习'
              : s.attempts[e.id]?.submitted
                ? '查看成绩与解析'
                : s.attempts[e.id]
                  ? '继续考试'
                  : statusOf(e) === '未开始'
                    ? '等待开放'
                    : statusOf(e) === '已结束'
                      ? '已截止'
                      : '开始考试'
          }}<AppIcon name="right" :size="14"
        /></el-button>
      </article>
    </div>
    <el-empty
      v-if="tab !== '我的成绩' && !filtered.length"
      :description="tab === '组卷草稿' ? '还没有草稿，创建一份属于你的试卷吧' : '没有匹配的考试'"
    />
    <section v-if="tab === '我的成绩'" class="panel"
      ><el-empty
        v-if="!results.length"
        description="还没有测评成绩，完成一场考试后会展示在这里" /><div v-else class="result-list"
        ><div v-for="r in results" :key="r.id"
          ><span class="result-score">{{ r.score }}<small>分</small></span
          ><div
            ><h3>{{ r.title }}</h3
            ><p class="muted"
              >{{ r.date }} ·
              {{
                r.passed ? '继续保持，你已经掌握了这些知识' : '再练习一次，让知识掌握得更扎实'
              }}</p
            ><button class="text-link" @click="router.push('/exam/' + r.examId)"
              >查看答卷与解析</button
            ></div
          ><StatusBadge :status="r.passed ? '已通过' : '未通过'" /></div></div
    ></section>
    <el-drawer
      v-model="paperOpen"
      :title="selectedExam?.title || '试卷预览'"
      size="680px"
      class="responsive-drawer"
      ><p class="demo-note">已发布试卷保留独立题目快照。修改、停用题库试题不会改变本试卷。</p
      ><QuestionPreview
        v-for="(q, i) in selectedExam?.questions"
        :key="q.id"
        :question="q"
        :index="i"
        show-answer
    /></el-drawer>
    <el-dialog v-model="resultOpen" title="考试成绩管理" width="760px" class="responsive-dialog"
      ><el-empty
        v-if="!results.length"
        description="暂时没有提交的答卷，请切换员工端完成一次考试"
      /><div v-else class="table-scroll"
        ><table class="data-table"
          ><thead
            ><tr><th>员工</th><th>考试名称</th><th>成绩</th><th>结果</th></tr></thead
          ><tbody
            ><tr v-for="r in results" :key="r.id"
              ><td>{{ r.name }}</td
              ><td>{{ r.title }}</td
              ><td>{{ r.score }} 分</td
              ><td
                ><StatusBadge
                  :status="r.passed ? '已通过' : '未通过'" /></td></tr></tbody></table></div
      ><template #footer
        ><el-button @click="resultOpen = false">关闭</el-button
        ><el-button :disabled="!results.length" type="primary" @click="exportResults"
          >导出成绩 CSV</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
