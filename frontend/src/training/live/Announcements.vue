<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { api, type Announcement } from '../../connected/api'
  import { errorMessage } from './session'
  import { syncPlatform, unreadAnnouncements } from './platform'
  const props = defineProps<{ admin?: boolean }>()
  const rows = ref<Announcement[]>([]),
    page = ref(1),
    total = ref(0),
    loading = ref(false),
    saving = ref(false),
    error = ref('')
  const editor = ref(false),
    selected = ref<Announcement | null>(null),
    detailError = ref(''),
    search = ref(''),
    recipientPage = ref(1)
  const employees = ref<
    Array<{ id: number; name: string; employeeNo: string; departmentName?: string }>
  >([])
  const employeeIds = ref<number[]>([])
  const fresh = () => ({
    requestId: crypto.randomUUID(),
    title: '',
    category: '培训通知',
    content: ''
  })
  const form = reactive(fresh())
  let request = 0,
    lastSubmission = ''
  const filtered = computed(() => {
    const q = search.value.trim().toLowerCase()
    return employees.value.filter((e) =>
      `${e.name} ${e.employeeNo} ${e.departmentName ?? ''}`.toLowerCase().includes(q)
    )
  })
  const visibleRecipients = computed(() =>
    filtered.value.slice((recipientPage.value - 1) * 20, recipientPage.value * 20)
  )
  async function load() {
    const ticket = ++request
    loading.value = true
    error.value = ''
    try {
      const data = await api.announcements(!!props.admin, page.value)
      if (ticket === request) {
        const lastPage = Math.max(1, Math.ceil(data.total / 20))
        if (page.value > lastPage) {
          page.value = lastPage
          await load()
          return
        }
        rows.value = data.list
        total.value = data.total
      }
      if (!props.admin) await syncPlatform(true)
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
    }
  }
  function changePage(delta: number) {
    page.value += delta
    void load()
  }
  async function create() {
    saving.value = true
    try {
      employees.value = await api.eligibleEmployees()
      Object.assign(form, fresh())
      lastSubmission = ''
      employeeIds.value = []
      search.value = ''
      recipientPage.value = 1
      editor.value = true
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  function selectFiltered() {
    employeeIds.value = [
      ...new Set([...employeeIds.value, ...filtered.value.map((e) => e.id)])
    ].slice(0, 5000)
  }
  async function publish() {
    if (saving.value || !employeeIds.value.length) return
    if (employeeIds.value.length > 5000) {
      ElMessage.error('一次最多选择 5000 名员工')
      return
    }
    try {
      await ElMessageBox.confirm(
        `将“${form.title}”发布给已选择的 ${employeeIds.value.length} 名员工。发布后收件名单固定。`,
        '确认发布公告',
        { confirmButtonText: '发布并投递', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    const signature = JSON.stringify({
      title: form.title,
      category: form.category,
      content: form.content,
      employeeIds: [...employeeIds.value].sort((a, b) => a - b)
    })
    if (lastSubmission && lastSubmission !== signature) form.requestId = crypto.randomUUID()
    lastSubmission = signature
    saving.value = true
    try {
      await api.publishAnnouncement({ ...form, employeeIds: employeeIds.value })
      editor.value = false
      page.value = 1
      await load()
      ElMessage.success('公告已发布到所选员工的通知列表')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  function open(row: Announcement) {
    selected.value = row
    detailError.value = ''
  }
  async function markRead() {
    if (!selected.value || saving.value) return
    saving.value = true
    detailError.value = ''
    try {
      const updated = await api.readAnnouncement(selected.value.id)
      selected.value = updated
      const index = rows.value.findIndex((r) => r.id === updated.id)
      if (index >= 0) rows.value[index] = updated
      await syncPlatform(true)
    } catch (e) {
      detailError.value = errorMessage(e)
    } finally {
      saving.value = false
    }
  }
  async function withdraw(row: Announcement) {
    try {
      await ElMessageBox.confirm(
        `撤回“${row.title}”后，收件员工将无法继续查看这条公告。`,
        '撤回公告',
        { confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    saving.value = true
    try {
      await api.withdrawAnnouncement(row.id, row.version)
      await load()
      ElMessage.success('公告已撤回')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section class="service-page"
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">TEAM ANNOUNCEMENTS</span
        ><h1>{{ admin ? '公告管理' : '通知公告' }}</h1
        ><p>{{
          admin
            ? '为授权范围内的员工发布通知，查看阅读情况。'
            : `查看分配给你的培训通知与平台公告，当前 ${unreadAnnouncements} 条未读。`
        }}</p></div
      ><div class="service-actions"
        ><button class="outline-button" :disabled="loading || saving" @click="load">刷新公告</button
        ><button v-if="admin" class="primary-button" :disabled="saving" @click="create"
          >发布公告</button
        ></div
      ></div
    ><div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取公告…</div
    ><div v-else-if="!rows.length" class="panel empty-state">{{
      admin ? '还没有可管理的公告。' : '暂未收到公告。'
    }}</div
    ><div v-else class="service-list"
      ><article v-for="row in rows" :key="row.id" class="panel"
        ><div class="panel-heading"
          ><div
            ><span class="course-meta"
              >{{ row.category }} · {{ new Date(row.publishedAt).toLocaleString('zh-CN') }}</span
            ><h2>{{ row.title }}</h2></div
          ><span
            class="status-pill"
            :class="{ draft: row.status === 'WITHDRAWN' || (!admin && !row.readAt) }"
            >{{
              row.status === 'WITHDRAWN'
                ? '已撤回'
                : admin
                  ? '已发布'
                  : row.readAt
                    ? '已读'
                    : '未读'
            }}</span
          ></div
        ><p class="service-copy service-excerpt">{{ row.content }}</p
        ><div class="service-meta"
          ><span
            >{{ row.publisherName }}发布<template v-if="admin">
              · 当前范围 {{ row.readCount }} / {{ row.recipientCount }} 人已读</template
            ></span
          ><div class="service-actions"
            ><button class="text-button" @click="open(row)">查看正文</button
            ><button
              v-if="admin && row.status === 'PUBLISHED'"
              class="text-button danger"
              :disabled="saving"
              @click="withdraw(row)"
              >撤回公告</button
            ></div
          ></div
        ></article
      ></div
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
      v-model="editor"
      title="发布公告"
      width="820px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><form class="admin-dialog-form" @submit.prevent="publish"
        ><fieldset :disabled="saving"
          ><div class="admin-form-grid"
            ><label>公告标题<input v-model.trim="form.title" required maxlength="80" /></label
            ><label
              >公告类型<select v-model="form.category"
                ><option>培训通知</option
                ><option>课程上新</option
                ><option>平台公告</option></select
              ></label
            ></div
          ><label
            >公告正文<textarea
              v-model.trim="form.content"
              rows="5"
              required
              maxlength="3000"
            /></label
          ><div class="service-recipient-heading"
            ><h3>收件员工 · 已选 {{ employeeIds.length }} 人</h3
            ><button type="button" class="text-button" @click="selectFiltered"
              >选择筛选结果（最多 5000 人）</button
            ><button type="button" class="text-button" @click="employeeIds = []">清空</button></div
          ><input
            v-model="search"
            aria-label="搜索公告收件人"
            placeholder="搜索姓名、工号或部门"
            @input="recipientPage = 1"
          /><div class="service-recipient-list"
            ><label
              v-for="employee in visibleRecipients"
              :key="employee.id"
              class="service-recipient"
              ><input v-model="employeeIds" type="checkbox" :value="employee.id" /><span
                >{{ employee.name
                }}<small
                  >{{ employee.employeeNo }} · {{ employee.departmentName || '未分配部门' }}</small
                ></span
              ></label
            ><p v-if="!filtered.length" class="muted">没有符合条件的已激活员工。</p></div
          ><div class="service-actions"
            ><button
              type="button"
              class="text-button"
              :disabled="recipientPage <= 1"
              @click="recipientPage--"
              >上一页</button
            ><span class="muted"
              >{{ recipientPage }} / {{ Math.max(1, Math.ceil(filtered.length / 20)) }}</span
            ><button
              type="button"
              class="text-button"
              :disabled="recipientPage * 20 >= filtered.length"
              @click="recipientPage++"
              >下一页</button
            ></div
          ></fieldset
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="saving || !employeeIds.length">{{
            saving ? '发布中…' : `发布给 ${employeeIds.length} 名员工`
          }}</button></div
        ></form
      ></el-dialog
    >
    <el-dialog
      :model-value="!!selected"
      :title="selected?.title || '公告正文'"
      width="740px"
      class="responsive-dialog"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      @update:model-value="!$event && (selected = null)"
      :close-on-click-modal="!saving"
      ><template v-if="selected"
        ><p class="muted"
          >{{ selected.category }} · {{ selected.publisherName }} ·
          {{ new Date(selected.publishedAt).toLocaleString('zh-CN') }}</p
        ><p class="service-copy service-notice-body">{{ selected.content }}</p
        ><p v-if="detailError" class="admin-detail-error" role="alert">{{ detailError }}</p
        ><div v-if="!admin" class="admin-dialog-form"
          ><div class="admin-form-actions"
            ><span v-if="selected.readAt" class="muted"
              >已读于 {{ new Date(selected.readAt).toLocaleString('zh-CN') }}</span
            ><button v-else class="primary-button" :disabled="saving" @click="markRead">{{
              saving ? '保存中…' : '标为已读'
            }}</button></div
          ></div
        ></template
      ></el-dialog
    >
  </section>
</template>
