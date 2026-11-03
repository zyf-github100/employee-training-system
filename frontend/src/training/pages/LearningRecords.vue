<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useTraining } from '../store'
  import { exportCsv } from '../api/repository'
  const s = useTraining(),
    keyword = ref(''),
    status = ref('全部记录'),
    period = ref('全部时间'),
    page = ref(1)
  const rows = computed(() =>
    Object.values(s.myLearning)
      .map((record) => ({ record, course: s.data.courses.find((c) => c.id === record.courseId) }))
      .filter((row) => row.course)
      .sort((a, b) =>
        (b.record.lastStudiedAt || b.record.enrolledAt).localeCompare(
          a.record.lastStudiedAt || a.record.enrolledAt
        )
      )
  )
  const filtered = computed(() =>
    rows.value.filter(
      ({ record: r, course: c }) =>
        (c!.title + c!.instructor).includes(keyword.value.trim()) &&
        (status.value === '全部记录' ||
          (status.value === '已完成'
            ? r.progress === 100
            : status.value === '学习中'
              ? r.progress > 0 && r.progress < 100
              : r.progress === 0)) &&
        (period.value === '全部时间' ||
          Date.now() - Date.parse(r.lastStudiedAt || r.enrolledAt) <=
            Number(period.value) * 86400000)
    )
  )
  const totalSeconds = computed(() => rows.value.reduce((v, r) => v + r.record.seconds, 0))
  const date = (v: string | null) =>
    v
      ? new Date(v).toLocaleString('zh-CN', {
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        })
      : '尚未学习'
  function exportRows() {
    exportCsv(
      '我的学习记录.csv',
      ['课程', '状态', '进度', '本地计时（秒）', '报名时间', '最近学习时间'],
      filtered.value.map(({ record: r, course: c }) => [
        c!.title,
        r.progress === 100 ? '已完成' : r.progress ? '学习中' : '待学习',
        r.progress + '%',
        r.seconds,
        r.enrolledAt,
        r.lastStudiedAt || ''
      ])
    )
  }
</script>
<template>
  <div
    ><PageTitle title="学习记录" description="每一分投入，都在为未来的自己积蓄力量。"
      ><el-button :disabled="!filtered.length" @click="exportRows"
        ><AppIcon name="download" :size="15" />导出学习记录</el-button
      ><router-link to="/courses"
        ><el-button type="primary"
          >发现新课程<AppIcon name="right" :size="14" /></el-button></router-link
    ></PageTitle>
    <div class="learning-summary"
      ><div
        ><span class="eyebrow">YOUR LEARNING FOOTPRINT</span><h2>成长有迹可循。</h2
        ><p>保持一点好奇，再向前走一小步。</p></div
      ><span
        ><b>{{ rows.length }}</b
        >已报名课程</span
      ><span
        ><b>{{ rows.filter((r) => r.record.progress === 100).length }}</b
        >已完成课程</span
      ><span
        ><b>{{ (totalSeconds / 3600).toFixed(1) }}<small>h</small></b
        >本地演示学时</span
      ></div
    >
    <section class="panel record-panel"
      ><div class="content-tabs"
        ><button
          v-for="v in ['全部记录', '待学习', '学习中', '已完成']"
          :key="v"
          :class="{ active: status === v }"
          @click="
            () => {
              status = v
              page = 1
            }
          "
          >{{ v }}</button
        ></div
      ><div class="filter-row"
        ><el-input
          v-model="keyword"
          clearable
          placeholder="搜索已报名课程或讲师"
          class="search-input"
          @input="page = 1"
          ><template #prefix><AppIcon name="search" :size="16" /></template></el-input
        ><el-select
          v-model="period"
          style="width: 150px"
          aria-label="学习时间范围"
          @change="page = 1"
          ><el-option label="全部时间" value="全部时间" /><el-option
            label="最近 7 天"
            value="7" /><el-option label="最近 30 天" value="30" /></el-select
        ><span class="filter-result">{{ filtered.length }} 条学习记录</span></div
      >
      <div v-if="filtered.length" class="record-list"
        ><article
          v-for="{ record: r, course: c } in filtered.slice((page - 1) * 8, page * 8)"
          :key="r.courseId"
          class="record-row"
          ><div class="record-cover"><CourseCover :theme="c!.theme" compact /></div
          ><div class="record-main"
            ><div
              ><h3>{{ c!.title }}</h3
              ><StatusBadge
                :status="r.progress === 100 ? '已完成' : r.progress ? '学习中' : '待学习'" /></div
            ><p>{{ c!.instructor }} · {{ c!.lessons }} 章节 · {{ c!.category }}</p
            ><div class="record-details"
              ><span
                ><AppIcon name="clock" :size="13" />{{
                  Math.floor(r.seconds / 60)
                }}
                分钟（本地）</span
              ><span>最近学习 {{ date(r.lastStudiedAt) }}</span></div
            ></div
          ><div class="record-progress"
            ><span
              >学习进度<b>{{ r.progress }}%</b></span
            ><el-progress :percentage="r.progress" :show-text="false" :stroke-width="5" /></div
          ><router-link v-if="c!.status === '已发布'" :to="'/learn/' + r.courseId"
            ><el-button
              :type="r.progress === 100 ? 'default' : 'primary'"
              :plain="r.progress === 100"
              >{{
                r.progress === 100 ? '回顾课程' : r.progress ? '继续学习' : '开始学习'
              }}</el-button
            ></router-link
          ><StatusBadge v-else status="已下架" /></article
      ></div>
      <el-empty
        v-else
        :description="
          rows.length ? '没有符合条件的学习记录' : '还没有报名课程，从一门感兴趣的课程开始吧'
        "
        :image-size="100"
        ><router-link to="/courses"
          ><el-button type="primary">去课程中心</el-button></router-link
        ></el-empty
      >
      <div class="pagination-row"
        ><span class="muted small">本地计时用于交互演示，不作为正式有效学时。</span
        ><el-pagination
          v-if="filtered.length > 8"
          v-model:current-page="page"
          :page-size="8"
          :total="filtered.length"
          layout="prev,pager,next"
          background
      /></div>
    </section>
  </div>
</template>
