<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { api, type FeedbackRecord } from '../../connected/api'
  import { errorMessage } from './session'
  const props = defineProps<{ admin?: boolean }>()
  const rows = ref<FeedbackRecord[]>([]),
    page = ref(1),
    total = ref(0),
    status = ref(''),
    keyword = ref('')
  const loading = ref(false),
    saving = ref(false),
    error = ref(''),
    editor = ref(false)
  const selected = ref<FeedbackRecord | null>(null),
    reply = ref('')
  const fresh = () => ({ requestId: crypto.randomUUID(), title: '', type: '课程建议', content: '' })
  const form = reactive(fresh())
  let request = 0,
    lastSubmission = ''
  async function load() {
    const ticket = ++request
    loading.value = true
    error.value = ''
    try {
      const data = await api.feedback(!!props.admin, page.value, status.value, keyword.value.trim())
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
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
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
  function create() {
    Object.assign(form, fresh())
    lastSubmission = ''
    editor.value = true
  }
  function editReply(row: FeedbackRecord) {
    selected.value = row
    reply.value = row.reply ?? ''
  }
  async function submit() {
    if (saving.value) return
    const signature = JSON.stringify({ title: form.title, type: form.type, content: form.content })
    if (lastSubmission && lastSubmission !== signature) form.requestId = crypto.randomUUID()
    lastSubmission = signature
    saving.value = true
    try {
      await api.submitFeedback(form)
      editor.value = false
      page.value = 1
      status.value = ''
      keyword.value = ''
      await load()
      ElMessage.success('反馈已提交')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  async function respond() {
    if (!selected.value || saving.value) return
    saving.value = true
    try {
      await api.replyFeedback(selected.value.id, reply.value.trim(), selected.value.version)
      selected.value = null
      await load()
      ElMessage.success('回复已保存，员工可在反馈中查看')
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
        ><span class="eyebrow">YOUR VOICE MATTERS</span
        ><h1>{{ admin ? '交流与反馈' : '我的反馈' }}</h1
        ><p>{{
          admin
            ? '倾听授权范围内员工的声音，及时回复问题与建议。'
            : '提交学习中的问题和建议，查看培训团队的回复。'
        }}</p></div
      ><button v-if="!admin" class="primary-button" @click="create">提交反馈</button></div
    >
    <div class="service-hero"
      ><AppIcon name="chat" :size="32" /><div
        ><h2>好的学习体验，从倾听开始</h2><p>每一个问题和建议，都值得被认真对待。</p></div
      ></div
    >
    <form class="toolbar admin-toolbar" @submit.prevent="search"
      ><input
        v-model="keyword"
        aria-label="搜索反馈"
        maxlength="80"
        placeholder="搜索标题或内容"
      /><select v-model="status" aria-label="反馈状态" @change="search"
        ><option value="">全部反馈</option
        ><option value="PENDING">待处理</option
        ><option value="REPLIED">已回复</option></select
      ><button class="outline-button" :disabled="loading">查询</button
      ><button type="button" class="text-button" :disabled="loading" @click="load">刷新</button
      ><span>共 {{ total }} 条</span></form
    >
    <div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取反馈…</div
    ><div v-else-if="!rows.length" class="panel empty-state">暂无符合条件的反馈。</div
    ><div v-else class="service-list"
      ><article v-for="row in rows" :key="row.id" class="panel"
        ><div class="panel-heading"
          ><div
            ><span class="course-meta"
              >{{ row.type }} · {{ new Date(row.createdAt).toLocaleString('zh-CN') }}</span
            ><h2>{{ row.title }}</h2></div
          ><span class="status-pill" :class="{ draft: row.status === 'PENDING' }">{{
            row.status === 'REPLIED' ? '已回复' : '待处理'
          }}</span></div
        ><p class="service-copy">{{ row.content }}</p
        ><div class="service-meta"
          ><span
            >{{ row.authorName
            }}<template v-if="admin">
              · {{ row.employeeNo }} · {{ row.departmentName || '未分配部门' }}</template
            ></span
          ><button v-if="admin" class="text-button" :disabled="saving" @click="editReply(row)">{{
            row.reply ? '编辑回复' : '回复反馈'
          }}</button></div
        ><div v-if="row.reply" class="service-reply"
          ><b>{{ row.repliedByName || '培训团队' }}的回复</b
          ><p class="service-copy">{{ row.reply }}</p
          ><small>{{
            row.repliedAt ? new Date(row.repliedAt).toLocaleString('zh-CN') : ''
          }}</small></div
        ></article
      ></div
    >
    <div v-if="total > 20" class="live-pagination"
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
      title="提交反馈"
      width="620px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><form class="admin-dialog-form" @submit.prevent="submit"
        ><fieldset :disabled="saving"
          ><label
            >反馈类型<select v-model="form.type"
              ><option>课程建议</option
              ><option>课程提问</option
              ><option>学习反馈</option
              ><option>系统问题</option></select
            ></label
          ><label>标题<input v-model.trim="form.title" required maxlength="80" /></label
          ><label
            >详细内容<textarea
              v-model.trim="form.content"
              rows="6"
              required
              maxlength="2000"
            /></label></fieldset
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="saving">{{
            saving ? '提交中…' : '提交反馈'
          }}</button></div
        ></form
      ></el-dialog
    >
    <el-dialog
      :model-value="!!selected"
      title="回复员工反馈"
      width="620px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      @update:model-value="!$event && (selected = null)"
      ><form v-if="selected" class="admin-dialog-form" @submit.prevent="respond"
        ><h3>{{ selected.title }}</h3
        ><p class="service-copy">{{ selected.content }}</p
        ><label
          >回复内容<textarea
            v-model.trim="reply"
            rows="6"
            maxlength="2000"
            required
            :disabled="saving"
          /></label
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="selected = null"
            >取消</button
          ><button class="primary-button" :disabled="saving">{{
            saving ? '保存中…' : '保存回复'
          }}</button></div
        ></form
      ></el-dialog
    >
  </section>
</template>
