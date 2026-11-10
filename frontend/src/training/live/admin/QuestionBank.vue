<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { api, type BankQuestion } from '../../../connected/api'
  import { errorMessage } from '../session'
  const questions = ref<BankQuestion[]>([]),
    keyword = ref(''),
    status = ref(''),
    type = ref('')
  const loading = ref(false),
    saving = ref(false),
    error = ref(''),
    editor = ref(false),
    editingId = ref<number | null>(null),
    importInput = ref<HTMLInputElement>()
  const labels: Record<string, string> = { single: '单选', multiple: '多选', boolean: '判断', subjective: '主观题' }
  const fresh = () => ({
    type: 'single' as BankQuestion['type'],
    stem: '',
    options: ['', ''],
    answer: [] as number[],
    analysis: '',
    referenceAnswer: '',
    gradingGuide: '',
    category: '',
    difficulty: '基础',
    status: 'ENABLED',
    version: 0
  })
  const form = reactive(fresh())
  const visible = computed(() =>
    questions.value.filter((q) => !type.value || q.type === type.value)
  )
  let request = 0
  async function load() {
    const ticket = ++request
    loading.value = true
    error.value = ''
    try {
      const data = await api.questions(keyword.value.trim(), status.value)
      if (ticket === request) questions.value = data
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
    }
  }
  function edit(q?: BankQuestion) {
    editingId.value = q?.id ?? null
    Object.assign(
      form,
      q
        ? { ...q, options: [...q.options], answer: [...q.answer], analysis: q.analysis ?? '' }
        : fresh()
    )
    editor.value = true
  }
  function changeType() {
    form.options = form.type === 'boolean' ? ['正确', '错误'] : form.type === 'subjective' ? [] : ['', '']
    form.answer = []
  }
  function choose(index: number) {
    if (form.type !== 'multiple') form.answer = [index]
    else
      form.answer = form.answer.includes(index)
        ? form.answer.filter((a) => a !== index)
        : [...form.answer, index]
  }
  function removeOption(index: number) {
    form.options.splice(index, 1)
    form.answer = form.answer.filter((a) => a !== index).map((a) => (a > index ? a - 1 : a))
  }
  async function save() {
    if (saving.value) return
    if (form.type !== 'subjective' && (!form.answer.length || (form.type === 'multiple' && form.answer.length < 2))) {
      ElMessage.error(form.type === 'multiple' ? '多选题请至少选择两个正确答案' : '请选择正确答案')
      return
    }
    if (form.type !== 'subjective' && new Set(form.options.map((o) => o.trim())).size !== form.options.length) {
      ElMessage.error('选项内容不能重复')
      return
    }
    if (form.type === 'subjective' && !form.referenceAnswer.trim() && !form.gradingGuide.trim()) {
      ElMessage.error('主观题请至少填写参考答案或评分标准')
      return
    }
    saving.value = true
    try {
      const body = { ...form, options: form.options.map((o) => o.trim()) }
      if (editingId.value) await api.updateQuestion(editingId.value, body)
      else await api.createQuestion(body)
      editor.value = false
      await load()
      ElMessage.success('题目已保存')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
    }
  }
  async function importQuestions(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return
    saving.value = true
    try {
      const result = await api.importQuestions(file)
      ElMessage.success(`导入完成：${result.imported} 道，${result.rejected} 道被拒绝`)
      await load()
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      saving.value = false
      if (importInput.value) importInput.value.value = ''
    }
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">QUESTION BANK</span><h1>题库管理</h1
        ><p>积累可靠的测评内容，维护题目、正确答案和解析。</p></div
      ><div class="section-actions"
        ><label class="outline-button file-button">批量导入<input ref="importInput" type="file" accept=".csv,.xlsx,.xls" hidden @change="importQuestions" /></label
        ><button class="primary-button" @click="edit()">新增题目</button></div></div
    >
    <form class="toolbar admin-toolbar" @submit.prevent="load"
      ><input v-model="keyword" aria-label="搜索题目" placeholder="搜索题干或分类" /><select
        v-model="type"
        aria-label="题型筛选"
        ><option value="">全部题型</option
        ><option v-for="(name, code) in labels" :key="code" :value="code">{{
          name
        }}</option></select
      ><select v-model="status" aria-label="题目状态" @change="load"
        ><option value="">全部状态</option
        ><option value="ENABLED">已启用</option
        ><option value="DISABLED">已停用</option></select
      ><button class="outline-button" :disabled="loading">查询</button
      ><span>{{ visible.length }} 道题目</span></form
    >
    <div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取题库…</div
    ><div v-else-if="!visible.length" class="panel empty-state">暂无符合条件的题目。</div>
    <div v-else class="admin-question-list"
      ><article v-for="q in visible" :key="q.id" class="panel"
        ><div class="panel-heading"
          ><div class="admin-tags"
            ><span class="status-pill">{{ labels[q.type] }}</span
            ><span class="muted">{{ q.category }} · {{ q.difficulty }}</span
            ><span v-if="q.status !== 'ENABLED'" class="status-pill draft">已停用</span></div
          ><button class="text-button" @click="edit(q)">编辑题目</button></div
        ><h2>{{ q.stem }}</h2
        ><div class="admin-question-options"
          ><div v-for="(option, i) in q.options" :key="i" :class="{ correct: q.answer.includes(i) }"
            ><b>{{ String.fromCharCode(65 + i) }}</b
            ><span>{{ option }}</span
            ><small v-if="q.answer.includes(i)">正确答案</small></div
          ></div
        ><p v-if="q.analysis" class="admin-analysis">解析：{{ q.analysis }}</p></article
      ></div
    >
    <el-dialog
      v-model="editor"
      :title="editingId ? '编辑题目' : '新增题目'"
      width="720px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      ><form class="admin-dialog-form" @submit.prevent="save"
        ><div class="admin-form-grid"
          ><label
            >题型<select v-model="form.type" :disabled="saving" @change="changeType"
              ><option v-for="(name, code) in labels" :key="code" :value="code">{{
                name
              }}</option></select
            ></label
          ><label
            >分类<input
              v-model.trim="form.category"
              maxlength="80"
              placeholder="例如：安全合规"
              :disabled="saving" /></label></div
        ><label
          >题干<textarea
            v-model.trim="form.stem"
            rows="3"
            maxlength="1000"
            required
            :disabled="saving"
          /></label
        ><fieldset v-if="form.type !== 'subjective'" :disabled="saving"
          ><legend
            >选项与答案 ·
            {{ form.type === 'multiple' ? '勾选至少两个正确答案' : '选择一个正确答案' }}</legend
          ><div v-for="(_, index) in form.options" :key="index" class="admin-option-editor"
            ><input
              :type="form.type === 'multiple' ? 'checkbox' : 'radio'"
              name="correct-answer"
              :aria-label="`选项 ${String.fromCharCode(65 + index)} 为正确答案`"
              :checked="form.answer.includes(index)"
              @change="choose(index)"
            /><b>{{ String.fromCharCode(65 + index) }}</b
            ><input
              v-model="form.options[index]"
              :aria-label="`选项 ${String.fromCharCode(65 + index)}`"
              required
              maxlength="300"
            /><button
              v-if="form.options.length > 2 && form.type !== 'boolean'"
              type="button"
              class="text-button"
              :aria-label="`删除选项 ${String.fromCharCode(65 + index)}`"
              @click="removeOption(index)"
              >移除</button
            ></div
          ><button
            v-if="form.options.length < 8 && form.type !== 'boolean'"
            type="button"
            class="text-button"
            @click="form.options.push('')"
            >＋ 添加选项</button
          ></fieldset
        ><template v-if="form.type === 'subjective'"
          ><label>参考答案<textarea v-model="form.referenceAnswer" rows="3" maxlength="5000" required /></label
          ><label>评分标准<textarea v-model="form.gradingGuide" rows="3" maxlength="5000" required /></label
        ></template
        ><label
          >答案解析<textarea
            v-model="form.analysis"
            rows="3"
            maxlength="5000"
            :disabled="saving"
          /></label
        ><div class="admin-form-grid"
          ><label
            >难度<select v-model="form.difficulty" :disabled="saving"
              ><option>基础</option
              ><option>进阶</option
              ><option>高级</option></select
            ></label
          ><label
            >状态<select v-model="form.status" :disabled="saving"
              ><option value="ENABLED">启用</option
              ><option value="DISABLED">停用</option></select
            ></label
          ></div
        ><p class="muted">题库修改不会改变已生成试卷中的题目快照。</p
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="saving" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="saving">{{
            saving ? '保存中…' : '保存题目'
          }}</button></div
        ></form
      ></el-dialog
    >
  </section>
</template>
