<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { api, ApiError, type ImportPreview, type ImportSummary } from './api'
  const emit = defineEmits<{ imported: [] }>()
  const file = ref<File | null>(null),
    preview = ref<ImportPreview | null>(null),
    recent = ref<ImportSummary[]>([])
  const busy = ref(false),
    error = ref(''),
    notice = ref('')
  const labels: Record<string, string> = {
    PREVIEW: '待确认',
    REVIEW_REQUIRED: '需修正',
    COMMITTED: '已导入'
  }
  async function run(task: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
      await task()
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : '网络请求失败，请重试'
    } finally {
      busy.value = false
    }
  }
  function download(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob),
      a = document.createElement('a')
    a.href = url
    a.download = name
    a.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  function select(event: Event) {
    const chosen = (event.target as HTMLInputElement).files?.[0] ?? null
    error.value = ''
    notice.value = ''
    preview.value = null
    file.value = null
    if (chosen && (!chosen.name.toLowerCase().endsWith('.xlsx') || chosen.size > 5 * 1024 * 1024)) {
      error.value = '请选择不超过 5 MB 的 .xlsx 文件'
      return
    }
    file.value = chosen
  }
  async function template() {
    await run(async () => download(await api.importTemplate(), '员工导入模板.xlsx'))
  }
  async function upload() {
    if (!file.value) return
    const chosen = file.value
    await run(async () => {
      preview.value = await api.importPreview(chosen)
      recent.value = await api.importRecent()
      notice.value = '校验完成，尚未创建员工账号。'
    })
  }
  async function commit() {
    if (!preview.value) return
    const id = preview.value.id
    await run(async () => {
      preview.value = await api.importCommit(id)
      recent.value = await api.importRecent()
      if (preview.value.status === 'COMMITTED') {
        notice.value = `已导入 ${preview.value.totalRows} 个未激活员工账号。请下载激活名单。`
        emit('imported')
      } else
        notice.value =
          '提交时发现数据或权限变化，本次没有导入任何账号，请下载校验报告修正后重新上传。'
    })
  }
  async function open(id: string, page = 1) {
    await run(async () => {
      preview.value = await api.importRead(id, page)
    })
  }
  async function report() {
    if (!preview.value) return
    const id = preview.value.id
    await run(async () => download(await api.importReport(id), '员工导入校验报告.xlsx'))
  }
  async function activations() {
    if (!preview.value) return
    if (
      !window.confirm(
        '将为此批次仍未激活的员工生成 24 小时有效的设置密码链接；原未使用链接会失效，已激活账号不变。下载后请逐人交付，避免转发整份名单。'
      )
    )
      return
    const id = preview.value.id
    await run(async () => {
      download(await api.importActivations(id), '员工激活名单.xlsx')
      notice.value =
        '激活名单已下载。请将每人的链接单独交付给对应员工，员工也可在设置密码页手动输入工号和设置码。'
      emit('imported')
    })
  }
  onMounted(() =>
    run(async () => {
      recent.value = await api.importRecent()
    })
  )
</script>
<template>
  <div class="import-panel">
    <div class="import-guide"
      ><div
        ><h2>Excel 批量导入</h2
        ><p>下载模板，填写工号、姓名和部门编码。最多 5,000 行，支持 .xlsx，文件不超过 5 MB。</p
        ><p>全部校验通过后统一导入，已有账号不会被覆盖。新账号通过激活链接自行设置密码。</p></div
      ><button class="outline-button" :disabled="busy" @click="template">下载导入模板</button></div
    >
    <form class="import-upload" @submit.prevent="upload"
      ><label
        >选择员工 Excel 文件<input
          type="file"
          accept=".xlsx"
          :disabled="busy"
          @change="select" /></label
      ><button class="primary-button" :disabled="busy || !file">上传并校验</button></form
    >
    <p v-if="busy" role="status" class="muted">正在处理，请勿重复提交…</p
    ><p v-if="error" role="alert" class="import-error">{{ error }}</p
    ><p v-if="notice" role="status" class="import-notice">{{ notice }}</p>
    <section v-if="preview" class="import-preview"
      ><div class="import-heading"
        ><h3>{{ labels[preview.status] || preview.status }}</h3
        ><span
          >共 {{ preview.totalRows }} 行 · 通过 {{ preview.validRows }} 行 · 问题
          {{ preview.errorRows }} 行</span
        ></div
      ><p v-if="preview.status !== 'COMMITTED'" class="muted"
        >预览有效至
        {{
          new Date(preview.expiresAt).toLocaleString('zh-CN')
        }}。确认时会再次检查工号及部门权限。</p
      ><div class="import-actions"
        ><button
          v-if="preview.status !== 'COMMITTED'"
          class="primary-button"
          :disabled="busy || preview.errorRows > 0"
          @click="commit"
          >确认导入 {{ preview.totalRows }} 人</button
        ><button class="outline-button" :disabled="busy" @click="report">下载校验报告</button
        ><button
          v-if="preview.status === 'COMMITTED'"
          class="primary-button"
          :disabled="busy"
          @click="activations"
          >生成 / 重新生成激活名单</button
        ></div
      >
      <p v-if="preview.errorRows" class="import-error"
        >本批次有校验问题，尚未导入任何账号。可在校验报告中修正员工数据后重新上传。</p
      >
      <div class="import-table-wrap" tabindex="0" aria-label="逐行校验结果，可横向滚动"
        ><table
          ><thead
            ><tr
              ><th>Excel 行号</th><th>工号</th><th>姓名</th><th>部门编码</th><th>校验结果</th></tr
            ></thead
          ><tbody
            ><tr v-for="row in preview.rows" :key="row.rowNumber"
              ><td>{{ row.rowNumber }}</td
              ><td>{{ row.fields.employeeNo || '—' }}</td
              ><td>{{ row.fields.name || '—' }}</td
              ><td>{{ row.fields.departmentCode || '—' }}</td
              ><td :class="{ invalid: row.errors.length }">{{
                row.errors.length ? row.errors.join('；') : '通过'
              }}</td></tr
            ></tbody
          ></table
        ></div
      >
      <div class="import-pagination"
        ><span
          >第 {{ preview.pageNo }} / {{ Math.ceil(preview.totalRows / preview.pageSize) }} 页</span
        ><button
          class="outline-button"
          :disabled="busy || preview.pageNo <= 1"
          @click="open(preview.id, preview.pageNo - 1)"
          >上一页</button
        ><button
          class="outline-button"
          :disabled="busy || preview.pageNo * preview.pageSize >= preview.totalRows"
          @click="open(preview.id, preview.pageNo + 1)"
          >下一页</button
        ></div
      >
    </section>
    <section v-if="recent.length" class="import-history"
      ><h3>我最近的导入</h3
      ><p class="muted">可恢复未过期的预览或查看已导入结果。重复确认同一批次不会重复建号。</p
      ><button v-for="item in recent" :key="item.id" :disabled="busy" @click="open(item.id)"
        ><span
          >{{ new Date(item.createdAt).toLocaleString('zh-CN') }} · {{ item.totalRows }} 人</span
        ><b>{{ labels[item.status] || item.status }}</b></button
      ></section
    >
  </div>
</template>
<style scoped>
  .import-panel {
    min-width: 0;
  }
  .import-guide {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 24px;
    background: #f0f7f4;
    border: 1px solid #dce9e4;
    padding: 24px;
    border-radius: 12px;
  }
  .import-guide h2 {
    margin: 0 0 12px;
  }
  .import-guide p {
    color: #627a76;
    font-size: 14px;
    line-height: 1.7;
    margin: 6px 0;
  }
  .import-guide button {
    flex-shrink: 0;
  }
  .import-upload {
    display: flex;
    gap: 18px;
    align-items: end;
    flex-wrap: wrap;
    margin: 24px 0;
  }
  .import-upload label {
    display: grid;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }
  .import-upload input {
    max-width: 100%;
    padding: 12px;
    border: 1px dashed #b4cbc4;
    border-radius: 8px;
    background: #fff;
  }
  .import-error,
  .import-notice {
    padding: 14px;
    border-radius: 8px;
    line-height: 1.7;
    font-size: 14px;
  }
  .import-error {
    background: #fff0ed;
    color: #a33c31;
  }
  .import-notice {
    background: #e8f7ee;
    color: #286944;
  }
  .import-heading,
  .import-actions,
  .import-pagination {
    display: flex;
    gap: 14px;
    align-items: center;
    flex-wrap: wrap;
  }
  .import-heading span,
  .import-pagination span {
    font-size: 13px;
    color: #64748b;
  }
  .import-actions {
    margin: 20px 0;
  }
  .import-table-wrap {
    overflow-x: auto;
    border: 1px solid #dce5e2;
    border-radius: 10px;
    background: #fff;
  }
  .import-table-wrap table {
    border-collapse: collapse;
    width: 100%;
    min-width: 650px;
    font-size: 13px;
  }
  .import-table-wrap th,
  .import-table-wrap td {
    padding: 14px;
    text-align: left;
    border-bottom: 1px solid #e9efec;
    max-width: 350px;
    overflow-wrap: anywhere;
  }
  .import-table-wrap th {
    background: #eef5f2;
    white-space: nowrap;
  }
  .import-table-wrap td.invalid {
    color: #a33c31;
  }
  .import-pagination {
    justify-content: end;
    margin: 18px 0;
  }
  .import-pagination span {
    margin-right: auto;
  }
  .import-history {
    margin-top: 32px;
  }
  .import-history > button {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    width: 100%;
    padding: 16px;
    margin: 10px 0;
    border: 1px solid #dce5e2;
    border-radius: 8px;
    background: white;
    color: #2d625d;
    text-align: left;
    cursor: pointer;
  }
  .import-panel button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .import-panel .muted {
    font-size: 13px;
    line-height: 1.6;
  }
  .import-panel button:focus-visible,
  .import-table-wrap:focus-visible {
    outline: 3px solid #75b4a4;
    outline-offset: 3px;
  }
  @media (max-width: 650px) {
    .import-guide {
      flex-direction: column;
      align-items: start;
      padding: 18px;
    }
    .import-upload {
      align-items: stretch;
      flex-direction: column;
    }
    .import-actions button {
      width: 100%;
    }
    .import-history > button {
      flex-direction: column;
    }
    .import-pagination {
      gap: 8px;
    }
    .import-pagination span {
      width: 100%;
    }
  }
</style>
