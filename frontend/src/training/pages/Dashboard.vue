<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useMediaQuery } from '@vueuse/core'
  import { useRouter } from 'vue-router'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { exportCsv } from '../api/repository'
  import ArtCountTo from '../../components/core/text-effect/art-count-to/index.vue'
  const s = useTraining(),
    router = useRouter(),
    period = ref('近 7 天'),
    selectedDay = ref(14)
  const todayLabel = computed(() =>
    new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  )
  const nextCourse = computed(() => s.published.find((c) => c.progress > 0 && c.progress < 100) || s.published.find((c) => !s.myLearning[c.id]))
  const nextExam = computed(() => s.accessibleExams.find((e) => e.status === '进行中' && !s.attempts[e.id]?.submitted))
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  const myFinished = computed(() => s.published.filter((c) => c.progress === 100).length)
  const cards = computed(() =>
    s.isAdmin
      ? [
          {
            label: '参与培训员工',
            value: s.data.employees.filter((e) => e.status === '在职').length,
            unit: '人',
            icon: 'user',
            tone: 'blue',
            note: '覆盖 6 个业务部门'
          },
          {
            label: '在线课程资源',
            value: s.published.length,
            unit: '门',
            icon: 'book',
            tone: 'violet',
            note: `${s.data.courses.filter((c) => c.status === '草稿').length} 门课程待发布`
          },
          {
            label: '培训完成率',
            value: s.completionRate,
            unit: '%',
            icon: 'target',
            tone: 'teal',
            note: '按有效培训任务统计'
          },
          {
            label: '累计学习时长',
            value: s.totalHours,
            unit: '小时',
            icon: 'clock',
            tone: 'orange',
            note: '每一步成长，都值得记录'
          }
        ]
      : [
          {
            label: '进行中的课程',
            value: s.published.filter((c) => c.progress > 0 && c.progress < 100).length,
            unit: '门',
            icon: 'book',
            tone: 'blue',
            note: '已报名且尚未完成'
          },
          {
            label: '已完成课程',
            value: myFinished.value,
            unit: '门',
            icon: 'finished',
            tone: 'teal',
            note: '已完成全部章节'
          },
          {
            label: '待完成考试',
            value: s.accessibleExams.filter(
              (e) => e.status === '进行中' && !s.attempts[e.id]?.submitted
            ).length,
            unit: '场',
            icon: 'exam',
            tone: 'violet',
            note: '按截止时间优先处理'
          },
          {
            label: '培训完成率',
            value: s.completionRate,
            unit: '%',
            icon: 'target',
            tone: 'orange',
            note: '按当前培训计划计算'
          }
        ]
  )
  const chartOption = computed(() => {
    const monthly = period.value === '近 30 天'
    return {
      color: ['#25ab92', '#7c91c9'],
      textStyle: { fontFamily: 'inherit' },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#fff',
        borderColor: '#e6ebef',
        textStyle: { color: '#344454' }
      },
      legend: {
        top: 0,
        right: 8,
        itemWidth: 9,
        itemHeight: 9,
        icon: 'circle',
        textStyle: { color: '#86939f', fontSize: 11 }
      },
      grid: { top: 48, right: 12, left: 40, bottom: 25 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: monthly
          ? ['08/16', '08/21', '08/26', '08/31', '09/05', '09/10', '09/14']
          : ['09/08', '09/09', '09/10', '09/11', '09/12', '09/13', '09/14'],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a0aa', fontSize: 11, margin: 14 }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#94a0aa', fontSize: 11 },
        splitLine: { lineStyle: { color: '#eef1f4', type: 'dashed' } }
      },
      series: [
        {
          name: '学习时长（小时）',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          showSymbol: false,
          data: monthly ? [92, 180, 143, 253, 206, 285, 314] : [96, 142, 120, 188, 162, 232, 208],
          lineStyle: { width: 3 },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(37,171,146,.18)' },
                { offset: 1, color: 'rgba(37,171,146,0)' }
              ]
            }
          }
        },
        {
          name: '活跃学习人数',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: monthly ? [43, 62, 54, 81, 76, 106, 113] : [42, 58, 50, 86, 66, 105, 98],
          lineStyle: { width: 2, type: 'dashed' }
        }
      ]
    }
  })
  const departmentProgress = computed(() =>
    departments
      .slice(0, 5)
      .map((d, i) => ({
        name: d,
        value: Math.round(
          s.data.employees.filter((e) => e.department === d).reduce((a, e) => a + e.progress, 0) /
            Math.max(1, s.data.employees.filter((e) => e.department === d).length)
        ),
        tone: ['#27ae97', '#7892cb', '#ad94d1', '#e8b376', '#9eb8a8'][i]
      }))
      .sort((a, b) => b.value - a.value)
  )
  const selectedNotice = ref<number | null>(null)
  function exportReport() {
    exportCsv(
      '培训概览.csv',
      ['指标', '数值', '单位'],
      cards.value.map((c) => [c.label, c.value, c.unit])
    )
  }
</script>
<template>
  <div class="dashboard-page"
    ><PageTitle
      :title="s.isAdmin ? `你好，${s.displayName}` : `你好，${s.displayName}`"
      :description="
        s.isAdmin
          ? '掌握每一份成长，让团队的潜力持续发生。'
          : '查看待完成任务，继续上次学习内容。'
      "
      ><span class="date-label"
        ><AppIcon name="calendar" :size="16" />{{ todayLabel }}</span
      ><el-button v-if="s.isAdmin" @click="exportReport"
        ><AppIcon name="download" :size="15" />导出报告</el-button
      ></PageTitle
    >
    <section v-if="s.isAdmin" class="welcome-banner"
      ><div class="banner-copy"
        ><span class="banner-eyebrow"><i /> LEARNING NEVER STOPS</span
        ><h2
          ><template v-if="s.isAdmin">让成长，持续发生。</template
          ><template v-else
            >把热爱，<span class="mobile-hero-line">变成更好的自己。</span></template
          ></h2
        ><p>{{
          s.isAdmin
            ? '连接知识与实践，为每一位伙伴点亮成长之路。'
            : '你的成长旅程正在继续，今天也为未来积蓄一点力量。'
        }}</p
        ><div class="banner-actions"
          ><button @click="router.push(s.isAdmin ? '/plans?create=1' : '/learn/1')"
            >{{ s.isAdmin ? '创建培训计划' : '继续我的学习'
            }}<AppIcon name="right" :size="17" /></button
          ><button class="ghost" @click="router.push(s.isAdmin ? '/analytics' : '/plans')"
            >{{ s.isAdmin ? '查看学习数据' : '查看学习计划'
            }}<AppIcon name="right" :size="15" /></button></div></div
      ><div class="banner-art" aria-hidden="true"
        ><div class="orb orb-one" /><div class="orb orb-two" /><div class="orbit" /><span
          class="art-spark spark-one"
          >✦</span
        ><span class="art-spark spark-two">✧</span
        ><div class="book-stack"
          ><div class="book book-bottom"><span>GROWTH</span></div
          ><div class="book book-middle"><span>EXPLORE</span></div
          ><div class="book book-top"
            ><span>LEARN</span><AppIcon name="book" :size="55" /></div></div
        ><div class="floating-card"
          ><span class="floating-icon"><AppIcon name="chart" :size="20" /></span
          ><div><b>成长，正在发生</b><small>KEEP MOVING FORWARD</small></div></div
        ><div class="floating-badge"><AppIcon name="medal" :size="27" /></div></div
    ></section>
    <section v-else class="employee-overview-strip"
      ><div class="overview-lead"
        ><span class="eyebrow">本周学习安排</span
        ><h2>从待完成任务开始</h2
        ><p>优先完成即将截止的课程和考试，学习进度会自动保存。</p></div
      ><div class="overview-items"
        ><div><span>进行中课程</span><b>{{ s.published.filter((c) => c.progress > 0 && c.progress < 100).length }}</b></div
        ><div><span>待完成考试</span><b>{{ s.accessibleExams.filter((e) => e.status === '进行中' && !s.attempts[e.id]?.submitted).length }}</b></div
        ><div><span>培训计划</span><b>{{ s.accessiblePlans.filter((p) => p.status !== '已完成').length }}</b></div></div
      ><router-link class="overview-action" to="/plans">查看培训计划<AppIcon name="right" :size="15" /></router-link
    ></section>
    <div class="stats-grid"
      ><article v-for="(card, i) in cards" :key="card.label" class="stat-card"
        ><div class="stat-top"
          ><span>{{ card.label }}</span
          ><div class="stat-icon" :class="card.tone"
            ><AppIcon :name="card.icon" :size="20" /></div></div
        ><div class="stat-value"
          ><b v-if="reduceMotion">{{ card.value.toLocaleString('zh-CN') }}</b
          ><ArtCountTo v-else :target="card.value" :duration="800" separator="," /><span>{{
            card.unit
          }}</span
          ><svg class="sparkline" viewBox="0 0 90 30" aria-hidden="true">
            <path
              :d="
                [
                  'M0 26 12 20 22 23 34 14 45 17 55 8 67 12 78 3 90 6',
                  'M0 25 12 24 24 15 35 17 45 8 55 13 66 9 78 4 90 3',
                  'M0 27 10 22 22 24 34 15 45 18 58 7 67 11 79 3 90 6',
                  'M0 26 12 21 24 23 34 12 46 16 56 8 68 11 80 3 90 0'
                ][i]
              "
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            /></svg></div
        ><div class="stat-foot"
          ><span class="positive"><AppIcon name="check" :size="11" /></span>{{ card.note }}</div
        ></article
      ></div
    >
    <template v-if="s.isAdmin"
      ><div class="dashboard-charts"
        ><section class="panel trend-panel"
          ><div class="panel-heading"
            ><div><h3>学习活跃趋势</h3><p>持续积累，见证团队的每一步成长</p></div
            ><el-select v-model="period" aria-label="学习趋势时间范围" style="width: 112px"
              ><el-option
                v-for="v in ['近 7 天', '近 30 天']"
                :key="v"
                :label="v"
                :value="v" /></el-select></div
          ><ChartView
            :option="chartOption"
            :height="224"
            label="演示学习趋势：学习时长与活跃学习人数"
          /><div class="chart-caption">示例趋势数据 · 截至 2026 年 9 月 14 日</div></section
        ><section class="panel department-panel"
          ><div class="panel-heading"
            ><div><h3>部门学习进度</h3><p>共同成长，一个都不掉队</p></div
            ><router-link to="/analytics" class="text-link"
              ><AppIcon name="right" :size="17" /></router-link></div
          ><div class="department-rows"
            ><div v-for="(d, i) in departmentProgress" :key="d.name" class="department-row"
              ><div
                ><span class="rank" :class="{ leading: i < 3 }">{{
                  String(i + 1).padStart(2, '0')
                }}</span
                ><b>{{ d.name }}</b
                ><strong>{{ d.value }}<small>%</small></strong></div
              ><el-progress
                :percentage="d.value"
                :color="d.tone"
                :show-text="false"
                :stroke-width="6" /></div></div></section
      ></div>
      <div class="dashboard-lower"
        ><section class="panel"
          ><div class="panel-heading"
            ><div class="heading-inline"
              ><h3>正在进行的培训</h3
              ><span class="number-badge">{{
                s.planViews.filter((p) => p.status === '进行中').length
              }}</span></div
            ><router-link to="/plans" class="text-link"
              >查看全部<AppIcon name="right" :size="13" /></router-link></div
          ><div class="table-scroll"
            ><table class="data-table compact-table"
              ><thead
                ><tr
                  ><th>培训计划</th><th>参与人数</th><th>完成进度</th><th>截止日期</th
                  ><th /></tr></thead
              ><tbody
                ><tr
                  v-for="(p, i) in s.planViews.filter((p) => p.status === '进行中').slice(0, 3)"
                  :key="p.id"
                  ><td
                    ><div class="table-title"
                      ><span class="table-icon" :class="['teal', 'blue', 'orange'][i]"
                        ><AppIcon name="calendar" :size="19" /></span
                      ><div
                        ><b>{{ p.title }}</b
                        ><small>{{ p.courseIds.length }} 门课程 · {{ p.department }}</small></div
                      ></div
                    ></td
                  ><td>{{ p.participants }}<span class="muted"> 人</span></td
                  ><td
                    ><div class="table-progress"
                      ><el-progress
                        :percentage="Math.round((p.completed / p.participants) * 100)"
                        :stroke-width="5" /></div></td
                  ><td class="muted">{{ p.end.slice(5) }}</td
                  ><td
                    ><button
                      class="icon-button"
                      :aria-label="'查看 ' + p.title"
                      @click="router.push('/plans')"
                      ><AppIcon
                        name="right"
                        :size="15" /></button></td></tr></tbody></table></div></section
        ><section class="panel announcements"
          ><div class="panel-heading"><h3>通知公告</h3><span class="tiny-label">LATEST</span></div
          ><button
            v-for="(n, i) in s.data.notices"
            :key="n.id"
            class="announcement-row"
            @click="
              () => {
                selectedNotice = n.id
                n.read = true
              }
            "
            ><span class="notice-dot" :class="['teal', 'blue', 'orange'][i]" /><span
              ><b>{{ n.title }}</b
              ><small>{{ n.category }}<span>·</span>{{ n.date.slice(5) }}</small></span
            ><AppIcon name="right" :size="13" /></button></section></div
    ></template>
    <template v-else
      ><div class="section-heading"
        ><div><h2>继续学习</h2><p>从上次进度继续完成已报名课程</p></div
        ><router-link to="/courses" class="text-link"
          >探索全部课程<AppIcon name="right" :size="14" /></router-link></div
      ><div class="course-grid dashboard-courses"
        ><CourseCard
          v-for="c in s.published.filter((c) => c.progress < 100).slice(0, 3)"
          :key="c.id"
          :course="c"
          employee
          @open="router.push('/learn/' + c.id)" /></div
      ><section class="panel employee-agenda"
        ><div class="panel-heading"
          ><div><h3>本周学习安排</h3><p>按计划完成课程和测评</p></div
          ><router-link to="/plans" class="text-link"
            >我的培训<AppIcon name="right" :size="14" /></router-link></div
        ><div class="week-strip"
          ><button
            v-for="(day, i) in ['一', '二', '三', '四', '五', '六', '日']"
            :key="day"
            :class="{ selected: selectedDay === 14 + i }"
            @click="selectedDay = 14 + i"
            ><small>周{{ day }}</small
            ><b>{{ 14 + i }}</b
            ><i v-if="i === 0 || i === 4" /></button></div
        ><div class="agenda-detail"
          ><span class="table-icon teal"><AppIcon name="book" /></span
          ><div
            ><b>{{
              selectedDay === 18 && nextExam
                ? nextExam.title
                : selectedDay === 14 && nextCourse
                  ? nextCourse.title
                  : '暂无必修安排'
            }}</b
            ><p>{{
              selectedDay === 18 && nextExam
                ? `截止 ${nextExam.deadline} · ${nextExam.duration} 分钟`
                : selectedDay === 14 && nextCourse
                  ? `${nextCourse.progress ? `当前进度 ${nextCourse.progress}%` : '尚未报名'} · ${nextCourse.lessons} 课时`
                  : '当前没有待完成的必修安排'
            }}</p></div
          ><el-button @click="router.push(selectedDay === 18 && nextExam ? '/exams' : '/courses')">{{
            selectedDay === 18 && nextExam ? '前往考试' : '查看课程'
          }}</el-button></div
        ></section
      ></template
    >
    <el-dialog
      :model-value="selectedNotice !== null"
      title="通知公告"
      width="520px"
      class="responsive-dialog"
      @close="selectedNotice = null"
      ><template v-if="selectedNotice"
        ><h2>{{ s.data.notices.find((n) => n.id === selectedNotice)?.title }}</h2
        ><p class="muted">{{ s.data.notices.find((n) => n.id === selectedNotice)?.date }}</p
        ><p class="reading-copy">{{
          s.data.notices.find((n) => n.id === selectedNotice)?.content
        }}</p></template
      ></el-dialog
    >
  </div>
</template>
