<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import {
    api,
    currentUser,
    type AnalyticsSummary,
    type TrainingPlan
  } from '../../../connected/api'
  import { errorMessage } from '../session'
  const summary = ref<AnalyticsSummary | null>(null),
    plans = ref<TrainingPlan[]>([])
  const loading = ref(true),
    error = ref(''),
    updated = ref('')
  const shortcuts = [
    { path: 'courses', title: '建设课程', description: '维护课程、章节与学习资源', icon: 'book' },
    {
      path: 'plans',
      title: '安排培训',
      description: '组合课程考试，分配参训名单',
      icon: 'calendar'
    },
    {
      path: 'questions',
      title: '维护题库',
      description: '管理试题、答案和解析',
      icon: 'collection'
    },
    { path: 'employees', title: '管理组织', description: '导入员工、调整岗位与权限', icon: 'user' }
  ]
  async function load() {
    loading.value = true
    error.value = ''
    try {
      const [data, list] = await Promise.all([api.analytics(), api.plans(true)])
      summary.value = data
      plans.value = list.slice(0, 5)
      updated.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section>
    <div class="section-heading"
      ><div
        ><span class="eyebrow">TRAINING MANAGEMENT</span><h1>{{ currentUser?.name }}，欢迎回来</h1
        ><p>从课程建设到培训结业，在这里推动团队成长。</p></div
      ><button class="outline-button" :disabled="loading" @click="load">{{
        loading ? '更新中…' : '刷新概览'
      }}</button></div
    >
    <div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重新加载</button></div
    >
    <div v-if="!summary && loading" class="empty-state" role="status">正在读取培训概览…</div>
    <template v-if="summary">
      <div class="admin-metrics">
        <article
          ><span class="metric-icon"><AppIcon name="user" /></span><span>启用员工</span
          ><strong>{{ summary.activeEmployees }}</strong
          ><small>当前人员授权范围</small></article
        >
        <article
          ><span class="metric-icon blue"><AppIcon name="book" /></span><span>已发布课程</span
          ><strong>{{ summary.publishedCourses }}</strong
          ><small>共享培训内容</small></article
        >
        <article
          ><span class="metric-icon amber"><AppIcon name="exam" /></span><span>考试通过率</span
          ><strong>{{ summary.examPassRate }}<em>%</em></strong
          ><small
            >{{ summary.passedAttempts }} / {{ summary.submittedAttempts }} 份已交答卷</small
          ></article
        >
        <article
          ><span class="metric-icon violet"><AppIcon name="medal" /></span><span>计划完成率</span
          ><strong>{{ summary.planCompletionRate }}<em>%</em></strong
          ><small
            >{{ summary.completedPlanAssignments }} /
            {{ summary.planAssignments }} 项参训任务</small
          ></article
        >
      </div>
      <div class="admin-shortcuts"
        ><router-link v-for="item in shortcuts" :key="item.path" :to="`/admin/${item.path}`"
          ><AppIcon :name="item.icon" :size="23" /><div
            ><b>{{ item.title }}</b
            ><small>{{ item.description }}</small></div
          ><AppIcon name="right" :size="16" /></router-link
      ></div>
      <div class="admin-overview-grid">
        <div class="panel"
          ><div class="panel-heading"
            ><h2>最近培训计划</h2
            ><router-link class="text-button" to="/admin/plans">查看全部 →</router-link></div
          ><div v-if="!plans.length" class="empty-state"
            >还没有培训计划，先安排团队的第一场学习。</div
          ><router-link
            v-for="plan in plans"
            :key="plan.id"
            class="admin-plan-row"
            to="/admin/plans"
            ><span class="admin-plan-symbol"><AppIcon name="calendar" /></span
            ><div
              ><b>{{ plan.title }}</b
              ><small
                >{{ plan.startDate }} 至 {{ plan.endDate }} · {{ plan.assigneeCount }} 人</small
              ></div
            ><span class="status-pill" :class="{ draft: plan.status === 'DRAFT' }">{{
              plan.status === 'DRAFT' ? '草稿' : '已发布'
            }}</span></router-link
          ></div
        >
        <div class="panel"
          ><div class="panel-heading"><h2>培训成效</h2><AppIcon name="chart" /></div
          ><div
            v-for="item in [
              {
                title: '课程完成率',
                value: summary.courseCompletionRate,
                text: `${summary.completedEnrollments} / ${summary.enrollments} 条报名记录`
              },
              {
                title: '考试通过率',
                value: summary.examPassRate,
                text: `${summary.passedAttempts} / ${summary.submittedAttempts} 份已交答卷`
              },
              {
                title: '计划完成率',
                value: summary.planCompletionRate,
                text: `${summary.completedPlanAssignments} / ${summary.planAssignments} 项参训任务`
              }
            ]"
            :key="item.title"
            class="admin-rate"
            ><div
              ><b>{{ item.title }}</b
              ><strong>{{ item.value }}%</strong></div
            ><div class="progress-track"><i :style="{ width: `${item.value}%` }" /></div
            ><small>{{ item.text }}</small></div
          ><router-link class="text-button" to="/admin/analytics">查看数据分析 →</router-link></div
        >
      </div>
      <p class="admin-footnote">数据更新于 {{ updated }} · 人员学习数据按当前账号授权范围汇总。</p>
    </template>
  </section>
</template>
