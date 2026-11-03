<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { answerIndices, answerText, questionTypeNames } from '../services/assessment'
  import { exportCsv } from '../api/repository'
  import type { BankQuestion, QuestionType } from '../types'
  const s = useTraining(),
    router = useRouter(),
    keyword = ref(''),
    type = ref('全部题型'),
    category = ref('全部分类'),
    difficulty = ref('全部难度'),
    status = ref('全部状态'),
    page = ref(1),
    editor = ref(false),
    editId = ref<number | null>(null),
    preview = ref<BankQuestion | null>(null)
  const categories = ['通用能力', '专业技能', '管理能力', '合规安全', '新人必修']
  const form = reactive({
    text: '',
    type: 'single' as QuestionType,
    category: '通用能力',
    difficulty: '基础' as BankQuestion['difficulty'],
    options: ['', '', '', ''],
    single: 0,
    multiple: [0, 1],
    analysis: ''
  })
  const filtered = computed(() =>
    s.data.questionBank.filter(
      (q) =>
        q.text.toLowerCase().includes(keyword.value.trim().toLowerCase()) &&
        (type.value === '全部题型' || q.type === type.value) &&
        (category.value === '全部分类' || q.category === category.value) &&
        (difficulty.value === '全部难度' || q.difficulty === difficulty.value) &&
        (status.value === '全部状态' || q.status === status.value)
    )
  )
  watch([keyword, type, category, difficulty, status], () => (page.value = 1))
  function openEditor(q?: BankQuestion) {
    editId.value = q?.id || null
    Object.assign(
      form,
      q
        ? {
            text: q.text,
            type: q.type,
            category: q.category,
            difficulty: q.difficulty,
            options: [...q.options],
            single: answerIndices(q.answer)[0] || 0,
            multiple: answerIndices(q.answer),
            analysis: q.analysis
          }
        : {
            text: '',
            type: 'single',
            category: '通用能力',
            difficulty: '基础',
            options: ['', '', '', ''],
            single: 0,
            multiple: [0, 1],
            analysis: ''
          }
    )
    editor.value = true
  }
  function changeType() {
    form.options = form.type === 'boolean' ? ['正确', '错误'] : ['', '', '', '']
    form.single = 0
    form.multiple = [0, 1]
  }
  function save() {
    try {
      const old = s.data.questionBank.find((q) => q.id === editId.value)
      s.saveBankQuestion({
        id: editId.value || Math.max(Date.now(), ...s.data.questionBank.map((q) => q.id + 1)),
        text: form.text,
        type: form.type,
        category: form.category,
        difficulty: form.difficulty,
        options: [...form.options],
        answer: form.type === 'multiple' ? [...form.multiple] : form.single,
        analysis: form.analysis,
        status: old?.status || '启用'
      })
      editor.value = false
      ElMessage.success('题库已保存；已组卷试题快照不受影响')
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '保存失败')
    }
  }
  async function toggle(q: BankQuestion) {
    try {
      await ElMessageBox.confirm(
        q.status === '启用'
          ? '停用后不会出现在新的选题与抽题中，已发布试卷不受影响。'
          : '启用后可用于新的选题与抽题。',
        q.status === '启用' ? '停用试题' : '启用试题',
        { confirmButtonText: '确认', cancelButtonText: '取消' }
      )
      q.status = q.status === '启用' ? '停用' : '启用'
      ElMessage.success('试题状态已更新')
    } catch {}
  }
  function exportQuestions() {
    exportCsv(
      '题库筛选结果.csv',
      ['题号', '题型', '分类', '难度', '状态', '题干', '正确答案', '解析'],
      filtered.value.map((q) => [
        q.id,
        questionTypeNames[q.type],
        q.category,
        q.difficulty,
        q.status,
        q.text,
        answerText(q, q.answer),
        q.analysis
      ])
    )
  }
</script>
<template>
  <div class="management-page"
    ><PageTitle title="题库管理" description="把知识沉淀为好问题，让每一次测评更有价值。"
      ><el-button @click="exportQuestions"><AppIcon name="download" :size="15" />导出题库</el-button
      ><el-button @click="router.push('/exams/compose')"
        >去组卷<AppIcon name="right" :size="14" /></el-button
      ><el-button type="primary" @click="openEditor()"
        ><AppIcon name="plus" :size="15" />新增试题</el-button
      ></PageTitle
    >
    <div class="summary-grid"
      ><div class="summary-card"
        ><span class="stat-icon teal"><AppIcon name="collection" :size="24" /></span
        ><div
          ><span>题库总量</span
          ><strong>{{ s.data.questionBank.length }}<small>题</small></strong></div
        ></div
      ><div v-for="(label, key) in questionTypeNames" :key="key" class="summary-card"
        ><span
          class="stat-icon"
          :class="key === 'single' ? 'blue' : key === 'multiple' ? 'violet' : 'orange'"
          ><AppIcon
            :name="key === 'multiple' ? 'list' : key === 'boolean' ? 'check' : 'exam'"
            :size="24" /></span
        ><div
          ><span>{{ label }}</span
          ><strong
            >{{ s.data.questionBank.filter((q) => q.type === key && q.status === '启用').length
            }}<small>题可用</small></strong
          ></div
        ></div
      ></div
    >
    <section class="panel"
      ><div class="bank-toolbar"
        ><el-input v-model="keyword" placeholder="搜索题干关键词" clearable
          ><template #prefix><AppIcon name="search" :size="16" /></template></el-input
        ><el-select v-model="type" aria-label="题库题型"
          ><el-option label="全部题型" value="全部题型" /><el-option
            v-for="(label, key) in questionTypeNames"
            :key="key"
            :label="label"
            :value="key" /></el-select
        ><el-select v-model="category" aria-label="题库分类"
          ><el-option v-for="v in ['全部分类', ...categories]" :key="v" :value="v" /></el-select
        ><el-select v-model="difficulty" aria-label="题库难度"
          ><el-option
            v-for="v in ['全部难度', '基础', '进阶', '挑战']"
            :key="v"
            :value="v" /></el-select
        ><el-select v-model="status" aria-label="题库状态"
          ><el-option v-for="v in ['全部状态', '启用', '停用']" :key="v" :value="v" /></el-select
      ></div>
      <div class="table-scroll"
        ><table class="data-table bank-table"
          ><thead
            ><tr><th>试题内容</th><th>分类</th><th>难度</th><th>状态</th><th>操作</th></tr></thead
          ><tbody
            ><tr v-for="q in filtered.slice((page - 1) * 10, page * 10)" :key="q.id"
              ><td
                ><span class="question-kind" :class="q.type">{{ questionTypeNames[q.type] }}</span
                ><button class="bank-title" @click="preview = q">{{ q.text }}</button
                ><small class="block muted"
                  >ID {{ q.id }} · {{ q.options.length }} 个选项</small
                ></td
              ><td>{{ q.category }}</td
              ><td>{{ q.difficulty }}</td
              ><td><StatusBadge :status="q.status" /></td
              ><td
                ><div class="inline-actions"
                  ><button class="text-link" @click="openEditor(q)">编辑</button
                  ><button class="text-link muted-link" @click="toggle(q)">{{
                    q.status === '启用' ? '停用' : '启用'
                  }}</button></div
                ></td
              ></tr
            ></tbody
          ></table
        ></div
      ><el-empty v-if="!filtered.length" description="没有匹配的试题，试试调整筛选条件" /><div
        class="pagination-bar"
        ><span>共 {{ filtered.length }} 道题 · 题库修改不影响已组卷快照</span
        ><el-pagination
          v-model:current-page="page"
          :total="filtered.length"
          :page-size="10"
          layout="prev,pager,next"
          background
      /></div>
    </section>
    <el-dialog
      v-model="editor"
      :title="editId ? '编辑题库试题' : '新增题库试题'"
      width="700px"
      class="responsive-dialog"
      ><el-form label-position="top"
        ><div class="question-form-meta"
          ><el-form-item label="题型"
            ><el-select v-model="form.type" @change="changeType"
              ><el-option
                v-for="(label, key) in questionTypeNames"
                :key="key"
                :label="label"
                :value="key" /></el-select></el-form-item
          ><el-form-item label="知识分类"
            ><el-select v-model="form.category"
              ><el-option v-for="v in categories" :key="v" :value="v" /></el-select></el-form-item
          ><el-form-item label="难度"
            ><el-select v-model="form.difficulty"
              ><el-option
                v-for="v in ['基础', '进阶', '挑战']"
                :key="v"
                :value="v" /></el-select></el-form-item></div
        ><el-form-item label="题干"
          ><el-input
            v-model="form.text"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="描述一个清晰、可判断的问题" /></el-form-item
        ><el-form-item
          v-for="(_, i) in form.options"
          :key="i"
          :label="'选项 ' + String.fromCharCode(65 + i)"
          ><el-input
            v-model="form.options[i]"
            :disabled="form.type === 'boolean'"
            maxlength="300" /></el-form-item
        ><el-form-item label="正确答案"
          ><el-checkbox-group v-if="form.type === 'multiple'" v-model="form.multiple"
            ><el-checkbox v-for="(_, i) in form.options" :key="i" :value="i">{{
              String.fromCharCode(65 + i)
            }}</el-checkbox></el-checkbox-group
          ><el-radio-group v-else v-model="form.single"
            ><el-radio v-for="(_, i) in form.options" :key="i" :value="i">{{
              form.type === 'boolean' ? form.options[i] : String.fromCharCode(65 + i)
            }}</el-radio></el-radio-group
          ></el-form-item
        ><p v-if="form.type === 'multiple'" class="demo-note"
          >多选题至少两个正确选项，演示判分采用全对得分，少选、错选均为 0 分。</p
        ><el-form-item label="答案解析"
          ><el-input
            v-model="form.analysis"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="解释答案背后的知识点" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editor = false">取消</el-button
        ><el-button type="primary" @click="save">保存试题</el-button></template
      ></el-dialog
    >
    <el-drawer :model-value="!!preview" title="试题预览" size="600px" @close="preview = null"
      ><QuestionPreview v-if="preview" :question="preview" :index="0" show-answer
    /></el-drawer>
  </div>
</template>
