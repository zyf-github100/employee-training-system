<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { download, exportCsv } from '../api/repository'
  import type { Employee, Course } from '../types'
  const s = useTraining(),
    keyword = ref(''),
    dept = ref('全部部门'),
    page = ref(1),
    profile = ref<Employee | null>(null),
    certificate = ref<Course | null>(null)
  const rows = computed(() =>
    s.data.employees.filter(
      (e) =>
        (dept.value === '全部部门' || e.department === dept.value) &&
        (e.name + e.no).includes(keyword.value.trim())
    )
  )
  const finished = computed(() => s.published.filter((c) => c.progress === 100))
  const exams = computed(() => Object.values(s.attempts).filter((a) => a.submitted))
  function exportArchive() {
    const es = s.isAdmin ? rows.value : s.data.employees.filter((e) => e.id === s.employeeId)
    exportCsv(
      '员工培训档案.csv',
      ['姓名', '工号', '部门', '学习进度', '累计学时'],
      es.map((e) => [e.name, e.no, e.department, e.progress + '%', e.hours])
    )
  }
  function certificateDownload(c: Course) {
    const text = `知行企业学习平台\n课程学习完成证明（前端演示）\n\n学员：${s.displayName}\n工号：${s.profile.no}\n课程：${c.title}\n完成进度：100%\n演示编号：ZX-${s.profile.no}-${String(c.id).padStart(4, '0')}\n\n此文件是本地演示记录，不属于正式培训结业证书。`
    download(`学习证明-${c.title}.txt`, new Blob([text], { type: 'text/plain;charset=utf-8' }))
  }
</script>
<template>
  <div
    ><PageTitle
      :title="s.isAdmin ? '培训档案' : '我的学习档案'"
      :description="
        s.isAdmin ? '查看员工课程、考试与计划完成情况。' : '查看课程完成、考试成绩和培训计划进度。'
      "
      ><el-button @click="exportArchive"
        ><AppIcon name="download" :size="15" />导出档案</el-button
      ></PageTitle
    ><template v-if="s.isAdmin"
      ><section class="panel"
        ><div class="filter-row"
          ><el-input
            v-model="keyword"
            placeholder="搜索姓名或工号"
            clearable
            class="search-input"
            @input="page = 1"
            ><template #prefix><AppIcon name="search" :size="16" /></template></el-input
          ><el-select
            v-model="dept"
            aria-label="档案所属部门"
            style="width: 160px"
            @change="page = 1"
            ><el-option v-for="d in ['全部部门', ...departments]" :key="d" :value="d" /></el-select
          ><span class="filter-result">共 {{ rows.length }} 份员工档案</span></div
        ><div class="table-scroll"
          ><table class="data-table"
            ><thead
              ><tr
                ><th>员工</th><th>部门 / 岗位</th><th>学习完成度</th><th>累计学时</th
                ><th>档案状态</th><th>操作</th></tr
              ></thead
            ><tbody
              ><tr v-for="e in rows.slice((page - 1) * 10, page * 10)" :key="e.id"
                ><td
                  ><div class="table-title"
                    ><span class="avatar" :class="e.avatar">{{ e.name.slice(0, 1) }}</span
                    ><div
                      ><b>{{ e.name }}</b
                      ><small>{{ e.no }}</small></div
                    ></div
                  ></td
                ><td
                  >{{ e.department }}<small class="block muted">{{ e.job }}</small></td
                ><td
                  ><div class="table-progress"
                    ><el-progress :percentage="e.progress" :stroke-width="5" /></div></td
                ><td
                  ><b>{{ e.hours }}</b> 小时</td
                ><td><StatusBadge status="持续更新" /></td
                ><td
                  ><button class="text-link" @click="profile = e"
                    >查看档案<AppIcon
                      name="right"
                      :size="13" /></button></td></tr></tbody></table></div
        ><el-empty v-if="!rows.length" description="没有找到对应的员工档案" /><div
          class="pagination-bar"
          ><span>档案随学习记录持续积累</span
          ><el-pagination
            v-model:current-page="page"
            :total="rows.length"
            :page-size="10"
            layout="prev,pager,next"
            background /></div></section></template
    ><template v-else
      ><section class="profile-banner"
        ><span class="avatar profile-avatar" :class="s.profile.avatar">{{
          s.displayName.slice(0, 1)
        }}</span
        ><div
          ><span class="eyebrow">学习档案</span
          ><h2
            >{{ s.displayName }} <span>{{ s.profile.job }}</span></h2
          ><p>{{ s.profile.department }} · {{ s.profile.no }}</p></div
        ><div class="profile-metric"
          ><strong>{{ finished.length }}</strong
          ><span>已完成课程</span></div
        ><div class="profile-metric"
          ><strong>{{ exams.length }}</strong
          ><span>已完成测评</span></div
        ></section
      ><div class="section-heading"
        ><div><h2>学习完成记录</h2><p>已完成课程和测评会显示在这里</p></div
        ><span class="muted small">本地记录 · 非正式证书</span></div
      ><div class="certificate-grid"
        ><button v-for="c in finished" :key="c.id" class="certificate-card" @click="certificate = c"
          ><span class="certificate-corner" /><AppIcon name="medal" :size="39" /><span
            class="eyebrow"
            >学习完成证明</span
          ><h3>{{ c.title }}</h3
          ><p>{{ s.displayName }} · 已完成全部课程学习</p
          ><span class="certificate-id"
            >ZX-{{ s.profile.no }}-{{ String(c.id).padStart(4, '0') }}</span
          ><span class="text-link"
            >查看学习证明<AppIcon name="right" :size="13" /></span></button></div
      ><section class="panel journey-panel"
        ><div class="panel-heading"
          ><h3>学习记录时间线</h3><span class="tiny-label">学习记录</span></div
        ><div class="timeline"
          ><article v-for="c in s.published.filter((c) => c.progress > 0)" :key="c.id"
            ><i :class="{ done: c.progress === 100 }" /><div
              ><span class="muted small">{{
                c.progress === 100 ? '已完成课程' : '持续学习中'
              }}</span
              ><h3>{{ c.title }}</h3
              ><p>{{
                c.progress === 100
                  ? '课程已完成，可随时回顾内容。'
                  : `当前进度 ${c.progress}%，继续完成剩余章节。`
              }}</p></div
            ><router-link :to="'/learn/' + c.id" class="text-link"
              >{{ c.progress === 100 ? '回顾' : '继续学习'
              }}<AppIcon name="right" :size="13" /></router-link></article></div></section
    ></template>
    <el-drawer :model-value="!!profile" title="员工培训档案" size="440px" @close="profile = null"
      ><template v-if="profile"
        ><div class="archive-person"
          ><span class="avatar profile-avatar" :class="profile.avatar">{{
            profile.name.slice(0, 1)
          }}</span
          ><h2>{{ profile.name }}</h2
          ><p>{{ profile.no }} · {{ profile.department }}</p></div
        ><div class="detail-pairs"
          ><span
            >岗位<b>{{ profile.job }}</b></span
          ><span
            >账号状态<b>{{ profile.status }}</b></span
          ><span
            >累计学时<b>{{ profile.hours }} 小时</b></span
          ><span
            >平均完成度<b>{{ profile.progress }}%</b></span
          ></div
        ><h3>档案说明</h3
        ><p class="reading-copy muted"
          >这里汇总课程、考试和培训计划的完成情况。</p
        ><el-button
          class="full-width"
          @click="
            exportCsv(
              profile.name + '-培训档案.csv',
              ['工号', '姓名', '部门', '学时', '完成度'],
              [
                [
                  profile.no,
                  profile.name,
                  profile.department,
                  profile.hours,
                  profile.progress + '%'
                ]
              ]
            )
          "
          >导出个人档案</el-button
        ></template
      ></el-drawer
    >
    <el-dialog
      :model-value="!!certificate"
      title="学习完成证明"
      width="620px"
      class="responsive-dialog"
      @close="certificate = null"
      ><div v-if="certificate" class="certificate-preview"
        ><img src="/brand.svg" alt="知行" /><span class="eyebrow">CERTIFICATE OF LEARNING</span
        ><h2>学习完成证明</h2><p>兹证明学员</p><h3>{{ s.displayName }}</h3
        ><p>已完成「{{ certificate.title }}」课程学习</p
        ><span>编号 ZX-{{ s.profile.no }}-{{ String(certificate.id).padStart(4, '0') }}</span
        ><small>本地前端演示 · 非正式结业证书</small></div
      ><template #footer
        ><el-button @click="certificate = null">关闭</el-button
        ><el-button v-if="certificate" type="primary" @click="certificateDownload(certificate)"
          >下载演示证明</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
