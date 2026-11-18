<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import {
    api,
    type Department,
    type ReportQuery,
    type ReportType
  } from '../../../connected/api'
  import { errorMessage } from '../session'
  import AppIcon from '../../components/AppIcon.vue'

  type ReportCard = {
    key: ReportType
    title: string
    description: string
    icon: string
    tone: string
    usesDates: boolean
    fields: string[]
    file: string
  }

  const reports: ReportCard[] = [
    {
      key: 'employees',
      title: '员工培训档案',
      description: '每人一行，汇总课程完成、学时、考试、计划与证书，适合 HR 归档。',
      icon: 'user',
      tone: 'teal',
      usesDates: false,
      fields: ['课程完成数', '完成学时', '考试通过', '计划完成', '证书数'],
      file: '员工培训档案'
    },
    {
      key: 'learning',
      title: '学习明细',
      description: '章节级学习时长与完成时间，用于核对培训过程和学习投入。',
      icon: 'book',
      tone: 'blue',
      usesDates: true,
      fields: ['章节进度', '学习时长', '完成时间'],
      file: '学习明细'
    },
    {
      key: 'exams',
      title: '考试成绩',
      description: '每次交卷的分数、通过状态、完整性与阅卷状态，便于复核。',
      icon: 'exam',
      tone: 'violet',
      usesDates: true,
      fields: ['分数', '是否通过', '完整性', '阅卷状态'],
      file: '考试成绩'
    },
    {
      key: 'plans',
      title: '计划完成情况',
      description: '每个人的计划进度、开始与完成时间，以及计划终止原因。',
      icon: 'calendar',
      tone: 'amber',
      usesDates: true,
      fields: ['任务状态', '进度', '完成时间', '终止原因'],
      file: '计划完成情况'
    }
  ]

  const departments = ref<Department[]>([])
  const filters = reactive({ keyword: '', departmentId: '', from: '', to: '' })
  const busy = ref<ReportType | ''>('')
  const error = ref('')
  const notice = ref('')
  const departmentOptions = computed(() =>
    [...departments.value].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  )

  function message(text: string) {
    notice.value = text
    window.setTimeout(() => {
      if (notice.value === text) notice.value = ''
    }, 3000)
  }
  function reset() {
    filters.keyword = ''
    filters.departmentId = ''
    filters.from = ''
    filters.to = ''
  }
  function stamp() {
    const now = new Date()
    const pad = (value: number) => String(value).padStart(2, '0')
    return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
  }
  function save(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  async function download(report: ReportCard) {
    if (busy.value) return
    error.value = ''
    if (report.usesDates && filters.from && filters.to && filters.from > filters.to) {
      error.value = '开始日期不能晚于结束日期'
      return
    }
    const query: ReportQuery = {}
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    if (filters.departmentId) query.departmentId = Number(filters.departmentId)
    if (report.usesDates) {
      if (filters.from) query.from = filters.from
      if (filters.to) query.to = filters.to
    }
    busy.value = report.key
    try {
      const blob = await api.report(report.key, query)
      save(blob, `${report.file}-${stamp()}.xlsx`)
      message(`${report.title}已导出`)
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = ''
    }
  }
  onMounted(async () => {
    try {
      departments.value = await api.departments()
    } catch (e) {
      error.value = errorMessage(e)
    }
  })
</script>

<template>
  <section class="page-section report-panel">
    <header class="report-hero">
      <div>
        <span class="eyebrow">TRAINING / REPORTS</span>
        <h1>报表导出</h1>
        <p>把培训过程导出为 Excel，交给 HR、审计或业务负责人。</p>
      </div>
      <div class="report-hero-badge">
        <AppIcon name="download" :size="18" />
        <span>受当前数据范围限制</span>
      </div>
    </header>

    <div v-if="error" class="api-alert" role="alert">
      <b>导出失败</b><span>{{ error }}</span>
      <button class="text-button" @click="error = ''">关闭</button>
    </div>
    <div v-if="notice" class="toast" role="status">{{ notice }}</div>

    <section class="report-filters">
      <div class="filter-head">
        <div>
          <h2>筛选条件</h2>
          <p>关键词和部门适用于全部报表；日期只作用于明细类报表。</p>
        </div>
        <button class="text-button" @click="reset">重置</button>
      </div>
      <div class="filter-grid">
        <label class="field">
          <span>员工</span>
          <input v-model="filters.keyword" maxlength="80" placeholder="工号或姓名" />
        </label>
        <label class="field">
          <span>部门</span>
          <select v-model="filters.departmentId">
            <option value="">全部部门</option>
            <option v-for="dept in departmentOptions" :key="dept.id" :value="dept.id">
              {{ dept.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>开始日期</span>
          <input v-model="filters.from" type="date" />
        </label>
        <label class="field">
          <span>结束日期</span>
          <input v-model="filters.to" type="date" />
        </label>
      </div>
      <p class="filter-note">
        员工档案导出全量汇总，不受日期限制；学习明细、考试成绩和计划完成按最后活动/交卷/完成时间筛选。单次最多 50,000 行。
      </p>
    </section>

    <div class="report-grid">
      <article v-for="report in reports" :key="report.key" class="report-card" :class="`tone-${report.tone}`">
        <div class="report-card-head">
          <span class="report-icon"><AppIcon :name="report.icon" :size="22" /></span>
          <span class="report-tag">{{ report.usesDates ? '按日期筛选' : '全量汇总' }}</span>
        </div>
        <h3>{{ report.title }}</h3>
        <p>{{ report.description }}</p>
        <ul class="report-fields">
          <li v-for="field in report.fields" :key="field">{{ field }}</li>
        </ul>
        <button class="primary-button" :disabled="!!busy" @click="download(report)">
          <AppIcon name="download" :size="16" />
          {{ busy === report.key ? '正在生成…' : '导出 Excel' }}
        </button>
      </article>
    </div>

    <p class="report-footnote">导出操作会记录在管理审计中；文件为 .xlsx，可用 Excel 或 WPS 打开。</p>
  </section>
</template>

<style scoped>
  .report-panel {
    --ink: #17211f;
    --muted: #6b7a75;
    --line: #e4eae7;
    --soft: #eef7f3;
  }
  .report-hero {
    display: flex;
    gap: 24px;
    align-items: flex-end;
    justify-content: space-between;
    margin-bottom: 22px;
  }
  .report-hero h1 {
    margin: 6px 0 8px;
    font-size: 32px;
    letter-spacing: -0.02em;
    color: var(--ink);
  }
  .report-hero p {
    max-width: 620px;
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: var(--muted);
  }
  .report-hero-badge {
    display: inline-flex;
    flex-shrink: 0;
    gap: 8px;
    align-items: center;
    padding: 9px 14px;
    font-size: 12px;
    color: #1f6f5f;
    background: #eef7f3;
    border: 1px solid #d6ebe3;
    border-radius: 999px;
  }
  .report-filters {
    padding: 20px 22px;
    margin-bottom: 18px;
    background: #fff;
    border: 1px solid var(--line);
    border-radius: 16px;
  }
  .filter-head {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    justify-content: space-between;
  }
  .filter-head h2 {
    margin: 0 0 4px;
    font-size: 15px;
    color: var(--ink);
  }
  .filter-head p {
    margin: 0;
    font-size: 12px;
    color: var(--muted);
  }
  .filter-head .text-button {
    flex-shrink: 0;
    white-space: nowrap;
  }
  .filter-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
    margin-top: 16px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #3d4c48;
  }
  .field input,
  .field select {
    width: 100%;
    padding: 10px 12px;
    font: inherit;
    font-weight: 400;
    color: #1f2a27;
    background: #fbfcfc;
    border: 1px solid #dce4e1;
    border-radius: 9px;
  }
  .field input:focus,
  .field select:focus {
    background: #fff;
    border-color: #2f8a74;
    outline: none;
    box-shadow: 0 0 0 3px rgba(47, 138, 116, 0.12);
  }
  .filter-note {
    margin: 14px 0 0;
    font-size: 11px;
    line-height: 1.6;
    color: #8a9793;
  }
  .report-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
  .report-card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 22px;
    background: #fff;
    border: 1px solid var(--line);
    border-top: 3px solid #cfd9d5;
    border-radius: 16px;
    box-shadow: 0 1px 2px rgba(18, 50, 40, 0.03);
    transition:
      box-shadow 0.2s,
      transform 0.2s;
  }
  .report-card:hover {
    box-shadow: 0 10px 28px rgba(18, 50, 40, 0.08);
    transform: translateY(-2px);
  }
  .report-card.tone-teal {
    border-top-color: #1f7a66;
  }
  .report-card.tone-blue {
    border-top-color: #3b7ea1;
  }
  .report-card.tone-violet {
    border-top-color: #7a6aa8;
  }
  .report-card.tone-amber {
    border-top-color: #c08a2e;
  }
  .report-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .report-icon {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    color: #1f7a66;
    background: var(--soft);
    border-radius: 14px;
  }
  .tone-blue .report-icon {
    color: #2f6f8f;
    background: #edf5fa;
  }
  .tone-violet .report-icon {
    color: #6b5b9a;
    background: #f2effa;
  }
  .tone-amber .report-icon {
    color: #8a6118;
    background: #fbf4e5;
  }
  .report-tag {
    padding: 4px 9px;
    font-size: 11px;
    color: #6b7a75;
    background: #f3f6f5;
    border-radius: 999px;
  }
  .report-card h3 {
    margin: 4px 0 0;
    font-size: 18px;
    color: var(--ink);
  }
  .report-card > p {
    margin: 0;
    font-size: 13px;
    line-height: 1.65;
    color: var(--muted);
  }
  .report-fields {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 0;
    margin: 4px 0;
    list-style: none;
  }
  .report-fields li {
    padding: 4px 9px;
    font-size: 11px;
    color: #4f605b;
    background: #f7faf9;
    border: 1px solid #edf1ef;
    border-radius: 6px;
  }
  .report-card .primary-button {
    display: inline-flex;
    gap: 8px;
    align-items: center;
    justify-content: center;
    align-self: flex-start;
    margin-top: auto;
  }
  .report-footnote {
    margin: 18px 0 0;
    font-size: 11px;
    color: #93a09c;
  }
  @media (max-width: 980px) {
    .filter-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .report-grid {
      grid-template-columns: 1fr;
    }
  }
  @media (max-width: 640px) {
    .report-hero {
      flex-direction: column;
      align-items: flex-start;
    }
    .report-hero-badge {
      align-self: flex-start;
    }
    .report-filters {
      padding: 16px;
    }
    .filter-grid {
      grid-template-columns: 1fr 1fr;
      gap: 10px 12px;
    }
    .field input,
    .field select {
      padding: 9px 10px;
    }
    .report-card .primary-button {
      width: 100%;
    }
  }
  @media (max-width: 360px) {
    .filter-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
