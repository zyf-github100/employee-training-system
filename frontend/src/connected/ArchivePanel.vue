<script setup lang="ts">
  import { nextTick, onMounted, onUnmounted, ref } from 'vue'
  import { use } from 'echarts/core'
  import { BarChart } from 'echarts/charts'
  import { GridComponent, TooltipComponent } from 'echarts/components'
  import { CanvasRenderer } from 'echarts/renderers'
  use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])
  import { ApiError, api, type AnalyticsSummary, type EmployeeArchive } from './api'

  const props = defineProps<{ admin: boolean }>()
  const archive = ref<EmployeeArchive | null>(null)
  const analytics = ref<AnalyticsSummary | null>(null)
  const loading = ref(false)
  const error = ref('')
  const chartEl = ref<HTMLElement | null>(null)
  let chart: import('echarts/core').ECharts | null = null
  let chartObserver: ResizeObserver | null = null
  function apiMessage(e: unknown) {
    error.value = e instanceof ApiError ? e.message : '档案分析接口暂时不可用，请确认后端已启动'
  }
  async function load() {
    loading.value = true
    error.value = ''
    chartObserver?.disconnect()
    chart?.dispose()
    chart = null
    try {
      if (props.admin) {
        analytics.value = await api.analytics()
      } else archive.value = await api.employeeArchive()
    } catch (e) {
      apiMessage(e)
    } finally {
      loading.value = false
    }
    if (props.admin && analytics.value) {
      await nextTick()
      try {
        await renderChart()
      } catch (e) {
        apiMessage(e)
      }
    }
  }
  async function renderChart() {
    if (!chartEl.value || !analytics.value) return
    const echarts = await import('echarts/core')
    if (!chart) {
      chart = echarts.init(chartEl.value)
      chartObserver = new ResizeObserver(() => chart?.resize())
      chartObserver.observe(chartEl.value)
    }
    chart.setOption({
      grid: { left: 40, right: 20, top: 25, bottom: 30 },
      tooltip: { trigger: 'axis', valueFormatter: (value: number) => `${value}%` },
      xAxis: { type: 'category', data: ['课程完成', '考试通过', '计划完成'] },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        {
          type: 'bar',
          barWidth: '38%',
          data: [
            analytics.value.courseCompletionRate,
            analytics.value.examPassRate,
            analytics.value.planCompletionRate
          ],
          itemStyle: { borderRadius: [7, 7, 0, 0], color: '#2f8d81' }
        }
      ]
    })
  }
  function resizeChart() {
    chart?.resize()
  }
  onMounted(load)
  onUnmounted(() => {
    window.removeEventListener('resize', resizeChart)
    chartObserver?.disconnect()
    chart?.dispose()
    chart = null
  })
  onMounted(() => window.addEventListener('resize', resizeChart))
</script>

<template>
  <section class="page-section archive-panel"
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">ARCHIVE / MY LEARNING</span
        ><h1>{{ admin ? '培训数据分析' : '我的培训档案' }}</h1
        ><p>{{
          admin
            ? '按当前账号授权范围汇总员工学习数据；课程与考试内容在管理端共享。'
            : '查看个人培训事实、学习进度、考试成绩和计划完成情况。'
        }}</p></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新数据</button></div
    >
    <div v-if="error" class="api-alert"
      ><b>档案分析接口</b><span>{{ error }}</span
      ><button class="text-button" @click="error = ''">关闭</button></div
    ><div v-if="loading" class="empty-state">正在汇总培训数据…</div>
    <template v-else-if="admin && analytics"
      ><div class="stat-grid analytics-grid"
        ><article
          ><span>启用员工</span><strong>{{ analytics.activeEmployees }}</strong
          ><small>当前授权范围内的启用员工</small></article
        ><article
          ><span>课程完成率</span><strong>{{ analytics.courseCompletionRate }}%</strong
          ><small
            >{{ analytics.completedEnrollments }} / {{ analytics.enrollments }} 次报名</small
          ></article
        ><article
          ><span>考试通过率</span><strong>{{ analytics.examPassRate }}%</strong
          ><small
            >{{ analytics.passedAttempts }} / {{ analytics.submittedAttempts }} 份已提交答卷</small
          ></article
        ><article
          ><span>计划完成率</span><strong>{{ analytics.planCompletionRate }}%</strong
          ><small
            >{{ analytics.completedPlanAssignments }} /
            {{ analytics.planAssignments }} 个员工任务</small
          ></article
        ></div
      ><div class="panel analytics-summary"
        ><h2>完成率对比</h2><div ref="chartEl" class="analytics-chart"></div
        ><p
          >共享内容：已发布课程 {{ analytics.publishedCourses }} 门 · 已发布考试
          {{ analytics.publishedExams }} 场 · 可管理的已发布计划
          {{ analytics.publishedPlans }} 个</p
        ></div
      ></template
    >
    <template v-else-if="archive"
      ><div class="archive-heading panel"
        ><div
          ><span class="eyebrow">EMPLOYEE ARCHIVE</span><h2>{{ archive.name }}</h2
          ><p>{{ archive.employeeNo }} · {{ archive.departmentName || '未分配部门' }}</p></div
        ></div
      ><div class="stat-grid archive-stats"
        ><article
          ><span>课程学习</span><strong>{{ archive.courses.averageProgressPercent }}%</strong
          ><small
            >{{ archive.courses.completed }} / {{ archive.courses.enrolled }} 门完成 ·
            {{ archive.courses.completedMinutes }} 分钟</small
          ></article
        ><article
          ><span>考试通过</span><strong>{{ archive.exams.passed }}</strong
          ><small
            >{{ archive.exams.submitted }} 份已提交 · 平均
            {{ archive.exams.averageScore }} 分</small
          ></article
        ><article
          ><span>培训计划</span><strong>{{ archive.plans.averageProgressPercent }}%</strong
          ><small
            >{{ archive.plans.completed }} / {{ archive.plans.assigned }} 个计划完成</small
          ></article
        ></div
      ><div class="panel archive-records"
        ><div class="panel-heading"><h2>最近培训记录</h2></div
        ><div v-if="archive.recentRecords.length" class="record-list"
          ><article
            v-for="record in archive.recentRecords"
            :key="`${record.recordType}-${record.recordId}`"
            ><span class="status-pill">{{
              record.recordType === 'COURSE'
                ? '课程'
                : record.recordType === 'EXAM'
                  ? '考试'
                  : '计划'
            }}</span
            ><strong>{{ record.title }}</strong
            ><small
              >{{ record.status }} · {{ record.progressPercent }}%<template
                v-if="record.score !== undefined"
              >
                · {{ record.score }} 分</template
              ></small
            ></article
          ></div
        ><p v-else class="muted">暂无培训记录</p></div
      ></template
    >
  </section>
</template>
