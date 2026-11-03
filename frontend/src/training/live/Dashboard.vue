<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import {
    api,
    type EmployeeArchive,
    type TrainingPlan,
    type Exam,
    type Attempt
  } from '../../connected/api'
  import { currentUser, errorMessage } from './session'
  const archive = ref<EmployeeArchive>(),
    plans = ref<TrainingPlan[]>([]),
    exams = ref<Exam[]>([]),
    attempts = ref<Attempt[]>([])
  const loading = ref(true),
    error = ref('')
  const pendingExams = computed(() =>
    exams.value.filter(
      (e) => !attempts.value.some((a) => a.paperId === e.id && a.status === 'SUBMITTED')
    )
  )
  const cards = computed(() => [
    {
      label: '已完成课程',
      value: archive.value?.courses.completed ?? 0,
      unit: '门',
      icon: 'book',
      tone: 'blue'
    },
    {
      label: '通过考试',
      value: archive.value?.exams.passed ?? 0,
      unit: '场',
      icon: 'exam',
      tone: 'teal'
    },
    {
      label: '已完成培训',
      value: archive.value?.plans.completed ?? 0,
      unit: '项',
      icon: 'medal',
      tone: 'violet'
    },
    {
      label: '完成课程时长',
      value: archive.value?.courses.completedMinutes ?? 0,
      unit: '分钟',
      icon: 'clock',
      tone: 'orange'
    }
  ])
  async function load() {
    loading.value = true
    error.value = ''
    try {
      ;[archive.value, plans.value, exams.value, attempts.value] = await Promise.all([
        api.employeeArchive(),
        api.plans(),
        api.exams(),
        api.myAttempts()
      ])
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">LEARNING / EVERY DAY</span
        ><h1>{{ currentUser?.name }}，开始今天的学习吧</h1
        ><p
          >{{
            new Date().toLocaleDateString('zh-CN', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'long'
            })
          }}
          · 每一步成长，都值得记录</p
        ></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新</button></div
    >
    <div v-if="error" class="api-alert" role="alert"
      >{{ error }}<button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取学习进度…</div>
    <template v-else-if="archive"
      ><section class="employee-overview-strip"
        ><div class="overview-lead"
          ><span class="eyebrow">我的学习安排</span><h2>从待完成任务开始</h2
          ><p>完成课程与考试后，培训进度和证书会自动更新。</p></div
        ><div class="overview-items"
          ><div
            ><span>待完成考试</span><b>{{ pendingExams.length }}</b></div
          ><div
            ><span>进行中培训</span
            ><b>{{ plans.filter((p) => p.progressPercent < 100).length }}</b></div
          ></div
        ><router-link class="overview-action" to="/plans"
          >查看培训计划<AppIcon name="right" :size="15" /></router-link
      ></section>
      <div class="stats-grid"
        ><article v-for="card in cards" :key="card.label" class="stat-card"
          ><div class="stat-top"
            ><span>{{ card.label }}</span
            ><div class="stat-icon" :class="card.tone"
              ><AppIcon :name="card.icon" :size="20" /></div></div
          ><div class="stat-value"
            ><b>{{ card.value }}</b
            ><span>{{ card.unit }}</span></div
          ></article
        ></div
      >
      <div class="live-dashboard-grid"
        ><section class="panel"
          ><div class="panel-heading"
            ><h2>我的培训</h2
            ><router-link class="text-button" to="/plans">查看全部 →</router-link></div
          ><article v-for="plan in plans.slice(0, 4)" :key="plan.id" class="live-task"
            ><h3>{{ plan.title }}</h3
            ><small
              >截止 {{ plan.endDate }} · 必修完成 {{ plan.completedItems }}/{{
                plan.totalItems
              }}</small
            ><div class="progress-track"
              ><i :style="{ width: `${plan.progressPercent}%` }" /></div></article
          ><p v-if="!plans.length" class="muted">暂无分配的培训计划</p></section
        >
        <section class="panel"
          ><div class="panel-heading"
            ><h2>继续学习</h2
            ><router-link class="text-button" to="/courses">课程中心 →</router-link></div
          ><router-link
            v-for="record in archive.recentRecords
              .filter((r) => r.recordType === 'COURSE')
              .slice(0, 4)"
            :key="record.recordId"
            :to="`/learn/${record.recordId}`"
            class="live-task live-task-link"
            ><AppIcon name="book" /><div
              ><h3>{{ record.title }}</h3
              ><small>{{ record.progressPercent }}% 完成</small></div
            ><AppIcon name="right" /></router-link
          ><p v-if="!archive.courses.enrolled" class="muted"
            >还没有开始学习，去课程中心选一门课程吧。</p
          ></section
        ></div
      ></template
    >
  </section>
</template>
