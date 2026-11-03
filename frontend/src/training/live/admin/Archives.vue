<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { api, type Employee, type EmployeeArchive } from '../../../connected/api'
  import { errorMessage } from '../session'
  const employees = ref<Employee[]>([]),
    archive = ref<EmployeeArchive | null>(null)
  const keyword = ref(''),
    page = ref(1),
    total = ref(0),
    loading = ref(false),
    detailLoading = ref(false),
    error = ref(''),
    detailError = ref('')
  let listRequest = 0,
    detailRequest = 0
  const recordTypes: Record<string, string> = { COURSE: '课程', EXAM: '考试', PLAN: '培训计划' }
  const statuses: Record<string, string> = {
    COMPLETED: '已完成',
    LEARNING: '学习中',
    ENROLLED: '已报名',
    SUBMITTED: '已交卷',
    IN_PROGRESS: '进行中',
    ASSIGNED: '待开始',
    PENDING: '待开始'
  }
  async function load() {
    const ticket = ++listRequest
    loading.value = true
    error.value = ''
    try {
      const data = await api.employees(page.value, keyword.value.trim())
      if (ticket === listRequest) {
        employees.value = data.list
        total.value = data.total
      }
    } catch (e) {
      if (ticket === listRequest) error.value = errorMessage(e)
    } finally {
      if (ticket === listRequest) loading.value = false
    }
  }
  async function open(employee: Employee) {
    const ticket = ++detailRequest
    archive.value = null
    detailError.value = ''
    detailLoading.value = true
    try {
      const data = await api.adminEmployeeArchive(employee.id)
      if (ticket === detailRequest) archive.value = data
    } catch (e) {
      if (ticket === detailRequest) detailError.value = errorMessage(e)
    } finally {
      if (ticket === detailRequest) detailLoading.value = false
    }
  }
  function search() {
    page.value = 1
    void load()
  }
  function changePage(delta: number) {
    page.value += delta
    void load()
  }
  function closeDetail() {
    detailRequest++
    archive.value = null
    detailError.value = ''
    detailLoading.value = false
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">EMPLOYEE ARCHIVES</span><h1>员工培训档案</h1
        ><p>查询授权范围内的员工，查看课程、考试和培训计划的实际记录。</p></div
      ><router-link class="outline-button" to="/admin/certificates">查看结业证书</router-link></div
    ><form class="toolbar admin-toolbar" @submit.prevent="search"
      ><input v-model="keyword" aria-label="搜索员工档案" placeholder="搜索员工姓名或工号" /><button
        class="outline-button"
        :disabled="loading"
        >查询</button
      ><span>共 {{ total }} 个账号</span></form
    ><div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取员工…</div
    ><div v-else class="panel admin-table-panel"
      ><div class="admin-table-scroll"
        ><table class="admin-table"
          ><thead
            ><tr
              ><th>员工</th><th>工号</th><th>所属部门</th><th>账号状态</th><th>操作</th></tr
            ></thead
          ><tbody
            ><tr v-for="employee in employees" :key="employee.id"
              ><td
                ><b>{{ employee.name }}</b></td
              ><td>{{ employee.employeeNo }}</td
              ><td>{{ employee.departmentName || '未分配部门' }}</td
              ><td
                ><span class="status-pill" :class="{ draft: employee.status !== 'ACTIVE' }">{{
                  employee.status === 'ACTIVE' ? '启用' : '停用'
                }}</span></td
              ><td><button class="text-button" @click="open(employee)">查看档案</button></td></tr
            ></tbody
          ></table
        ></div
      ><p v-if="!employees.length" class="empty-state">暂无符合条件的员工。</p></div
    ><div v-if="total > 20" class="live-pagination"
      ><button class="outline-button" :disabled="page <= 1 || loading" @click="changePage(-1)"
        >上一页</button
      ><span>{{ page }} / {{ Math.ceil(total / 20) }}</span
      ><button
        class="outline-button"
        :disabled="page * 20 >= total || loading"
        @click="changePage(1)"
        >下一页</button
      ></div
    >
    <el-dialog
      :model-value="!!archive || detailLoading || !!detailError"
      :title="archive ? `${archive.name}的培训档案` : '员工培训档案'"
      width="880px"
      class="responsive-dialog"
      @update:model-value="!$event && closeDetail()"
      ><div v-if="detailLoading" class="empty-state">正在汇总培训记录…</div
      ><p v-else-if="detailError" class="admin-detail-error" role="alert">{{ detailError }}</p
      ><template v-else-if="archive"
        ><p class="muted">{{ archive.employeeNo }} · {{ archive.departmentName || '未分配部门' }}</p
        ><div class="admin-archive-metrics"
          ><article
            ><span>已完成课程</span
            ><strong>{{ archive.courses.completed }} / {{ archive.courses.enrolled }}</strong
            ><small>已完成课程预计 {{ archive.courses.completedMinutes }} 分钟</small></article
          ><article
            ><span>已通过考试</span
            ><strong>{{ archive.exams.passed }} / {{ archive.exams.submitted }}</strong
            ><small>已交答卷均分 {{ archive.exams.averageScore }} 分</small></article
          ><article
            ><span>已完成计划</span
            ><strong>{{ archive.plans.completed }} / {{ archive.plans.assigned }}</strong
            ><small>平均进度 {{ archive.plans.averageProgressPercent }}%</small></article
          ></div
        ><h3>最近培训记录</h3
        ><div class="admin-table-scroll"
          ><table class="admin-table"
            ><thead
              ><tr><th>类型</th><th>培训内容</th><th>状态</th><th>进度 / 成绩</th></tr></thead
            ><tbody
              ><tr
                v-for="record in archive.recentRecords"
                :key="`${record.recordType}-${record.recordId}`"
                ><td>{{ recordTypes[record.recordType] || record.recordType }}</td
                ><td>{{ record.title }}</td
                ><td>{{ statuses[record.status] || record.status }}</td
                ><td>{{
                  record.score != null ? `${record.score} 分` : `${record.progressPercent}%`
                }}</td></tr
              ></tbody
            ></table
          ></div
        ><p v-if="!archive.recentRecords.length" class="empty-state">该员工还没有培训记录。</p
        ><p class="muted">展示最近 10 条记录；课程预计时长不代表实际观看时长。</p></template
      ></el-dialog
    >
  </section>
</template>
