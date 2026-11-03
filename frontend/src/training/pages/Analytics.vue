<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useMediaQuery } from '@vueuse/core'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { exportCsv } from '../api/repository'
  const s = useTraining(),
    dept = ref('全部部门')
  const compactChart = useMediaQuery('(max-width: 600px)')
  const employees = computed(() =>
    s.data.employees.filter((e) => dept.value === '全部部门' || e.department === dept.value)
  )
  const avg = computed(() =>
    Math.round(
      employees.value.reduce((v, e) => v + e.progress, 0) / Math.max(1, employees.value.length)
    )
  )
  const data = computed(() =>
    departments
      .filter((d) => dept.value === '全部部门' || dept.value === d)
      .map((d) => {
        const es = employees.value.filter((e) => e.department === d)
        return {
          name: d,
          value: Math.round(es.reduce((v, e) => v + e.progress, 0) / Math.max(1, es.length)),
          people: es.length,
          hours: es.reduce((v, e) => v + e.hours, 0)
        }
      })
  )
  const bar = computed(() => ({
    color: ['#34b299'],
    tooltip: { trigger: 'axis' },
    grid: { top: 30, left: 48, right: 20, bottom: 44 },
    xAxis: {
      type: 'category',
      data: data.value.map((d) => d.name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e7edf0' } },
      axisLabel: {
        fontSize: compactChart.value ? 10 : 11,
        lineHeight: 14,
        color: '#7c8b98',
        interval: 0,
        formatter: (value: string) =>
          compactChart.value ? value.slice(0, 2) + '\n' + value.slice(2) : value
      }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#92a0ac' },
      splitLine: { lineStyle: { color: '#eef2f5', type: 'dashed' } }
    },
    series: [
      {
        name: '平均学习进度',
        type: 'bar',
        barMaxWidth: 35,
        data: data.value.map((d, i) => ({
          value: d.value,
          itemStyle: {
            color: ['#34b299', '#88a1d4', '#aa98d1', '#e8b078', '#5ca4b6', '#9fb99a'][i],
            borderRadius: [5, 5, 0, 0]
          }
        }))
      }
    ]
  }))
  const pie = computed(() => ({
    color: ['#2cab92', '#8198cb', '#b79cd2', '#ebb779', '#7ab4bf'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} 门 ({d}%)' },
    legend: {
      bottom: 0,
      itemWidth: 9,
      itemHeight: 9,
      icon: 'circle',
      textStyle: { color: '#81919d', fontSize: 11 }
    },
    series: [
      {
        name: '课程资源',
        type: 'pie',
        radius: ['50%', '72%'],
        center: ['50%', '43%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 5 },
        data: ['通用能力', '专业技能', '管理能力', '合规安全', '新人必修'].map((name) => ({
          name,
          value: s.published.filter((c) => c.category === name).length
        }))
      }
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '32%',
        style: { text: String(s.published.length), fontSize: 36, fontWeight: 600, fill: '#233645' }
      },
      {
        type: 'text',
        left: 'center',
        top: '48%',
        style: { text: '在线课程', fontSize: 12, fill: '#8c9aa4' }
      }
    ]
  }))
  function exportData() {
    exportCsv(
      '部门培训分析.csv',
      ['部门', '员工人数', '平均进度', '学习时长'],
      data.value.map((d) => [d.name, d.people, d.value + '%', d.hours])
    )
  }
</script>
<template>
  <div
    ><PageTitle title="数据分析" description="从数据中发现成长，让每一次培训更有价值。"
      ><el-select v-model="dept" aria-label="统计部门" style="width: 150px"
        ><el-option v-for="d in ['全部部门', ...departments]" :key="d" :value="d" /></el-select
      ><el-button @click="exportData"
        ><AppIcon name="download" :size="15" />导出数据</el-button
      ></PageTitle
    ><div class="analytics-note"
      ><AppIcon name="info" :size="16" />统计范围：{{ dept }} · 当前演示数据快照 ·
      学习进度为员工平均完成度</div
    ><div class="summary-grid"
      ><div
        v-for="m in [
          { label: '学习员工', value: employees.length, unit: '人', icon: 'user', tone: 'blue' },
          { label: '平均学习进度', value: avg, unit: '%', icon: 'target', tone: 'teal' },
          {
            label: '累计学习时长',
            value: employees.reduce((v, e) => v + e.hours, 0),
            unit: '小时',
            icon: 'clock',
            tone: 'orange'
          },
          {
            label: '人均学习时长',
            value: (
              employees.reduce((v, e) => v + e.hours, 0) / Math.max(1, employees.length)
            ).toFixed(1),
            unit: '小时',
            icon: 'chart',
            tone: 'violet'
          }
        ]"
        :key="m.label"
        class="summary-card"
        ><div class="stat-icon" :class="m.tone"><AppIcon :name="m.icon" :size="23" /></div
        ><div
          ><span>{{ m.label }}</span
          ><strong
            >{{ m.value }}<small>{{ m.unit }}</small></strong
          ></div
        ></div
      ></div
    ><div class="analytics-charts"
      ><section class="panel"
        ><div class="panel-heading"
          ><div><h3>部门学习完成度</h3><p>不同团队，同一个成长方向</p></div
          ><span class="tiny-label">COMPLETION</span></div
        ><ChartView :option="bar" :height="290" label="按部门比较员工平均学习进度" /></section
      ><section class="panel"
        ><div class="panel-heading"
          ><div><h3>课程资源分布</h3><p>多元能力，全面成长</p></div></div
        ><ChartView :option="pie" :height="290" label="全平台已发布课程的分类分布" /></section></div
    ><section class="panel"
      ><div class="panel-heading"
        ><h3>部门学习明细</h3><span class="muted small">数据与当前筛选条件一致</span></div
      ><div class="table-scroll"
        ><table class="data-table"
          ><thead
            ><tr
              ><th>部门名称</th><th>员工人数</th><th>平均学习进度</th><th>累计学习时长</th
              ><th>人均学习时长</th></tr
            ></thead
          ><tbody
            ><tr v-for="d in data" :key="d.name"
              ><td
                ><div class="table-title"
                  ><span class="table-icon teal"><AppIcon name="building" :size="18" /></span
                  ><b>{{ d.name }}</b></div
                ></td
              ><td>{{ d.people }} 人</td
              ><td
                ><div class="table-progress"
                  ><el-progress :percentage="d.value" :stroke-width="6" /></div></td
              ><td>{{ d.hours }} 小时</td
              ><td>{{ (d.hours / Math.max(1, d.people)).toFixed(1) }} 小时</td></tr
            ></tbody
          ></table
        ></div
      ></section
    ></div
  >
</template>
