<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { exportCsv } from '../api/repository'
  import type { Employee } from '../types'
  const s = useTraining(),
    keyword = ref(''),
    dept = ref('全部部门'),
    status = ref('全部状态'),
    page = ref(1),
    editor = ref(false),
    editId = ref<number | null>(null),
    fileInput = ref<HTMLInputElement>(),
    selected = ref<number[]>([])
  const form = reactive({ no: '', name: '', department: departments[0]!, job: '', email: '' })
  const filtered = computed(() =>
    s.data.employees.filter(
      (e) =>
        (dept.value === '全部部门' || e.department === dept.value) &&
        (status.value === '全部状态' || e.status === status.value) &&
        (e.name + e.no + e.job).toLowerCase().includes(keyword.value.trim().toLowerCase())
    )
  )
  const visible = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
  watch([keyword, dept, status], () => {
    page.value = 1
    selected.value = []
  })
  function edit(e?: Employee) {
    editId.value = e?.id ?? null
    Object.assign(
      form,
      e ? { ...e } : { no: '', name: '', department: departments[0], job: '', email: '' }
    )
    editor.value = true
  }
  function save() {
    if (!form.no.trim() || !form.name.trim() || !form.job.trim()) {
      ElMessage.warning('请填写工号、姓名与岗位')
      return
    }
    if (
      form.no.trim().toUpperCase() === 'ADMIN' ||
      s.data.employees.some(
        (e) => e.no.toUpperCase() === form.no.trim().toUpperCase() && e.id !== editId.value
      )
    ) {
      ElMessage.warning('该工号已存在，请使用唯一工号')
      return
    }
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      ElMessage.warning('邮箱格式不正确')
      return
    }
    const e = s.data.employees.find((e) => e.id === editId.value)
    if (e) {
      Object.assign(e, { ...form, no: form.no.trim(), name: form.name.trim() })
      const account = s.data.accounts.find((a) => a.employeeId === e.id)
      if (account) account.no = e.no
    } else
      s.data.employees.unshift({
        ...form,
        no: form.no.trim(),
        name: form.name.trim(),
        id: Date.now(),
        status: '在职',
        progress: 0,
        hours: 0,
        avatar: 'teal'
      })
    editor.value = false
    ElMessage.success('员工信息已保存')
  }
  async function toggle(e: Employee) {
    try {
      await ElMessageBox.confirm(
        `确认${e.status === '在职' ? '停用' : '启用'} ${e.name} 的账号？`,
        '账号状态',
        { confirmButtonText: '确认', cancelButtonText: '取消' }
      )
      e.status = e.status === '在职' ? '已停用' : '在职'
      ElMessage.success('账号状态已更新')
    } catch {}
  }
  function exportRows(template = false) {
    exportCsv(
      template ? '员工导入模板.csv' : '员工名单.csv',
      ['工号', '姓名', '部门', '岗位', '邮箱'],
      template
        ? [['E20260129', '新员工', '产品研发部', '研发工程师', 'new@zhixing.example']]
        : filtered.value
            .filter((e) => !selected.value.length || selected.value.includes(e.id))
            .map((e) => [e.no, e.name, e.department, e.job, e.email])
    )
  }
  function parseCsv(text: string) {
    const rows: string[][] = []
    let row: string[] = [],
      cell = '',
      quoted = false
    for (let i = 0; i < text.length; i++) {
      const c = text[i]
      if (c === '"') {
        if (quoted && text[i + 1] === '"') {
          cell += '"'
          i++
        } else quoted = !quoted
      } else if (c === ',' && !quoted) {
        row.push(cell)
        cell = ''
      } else if ((c === '\n' || c === '\r') && !quoted) {
        if (c === '\r' && text[i + 1] === '\n') i++
        row.push(cell)
        if (row.some(Boolean)) rows.push(row)
        row = []
        cell = ''
      } else cell += c
    }
    if (quoted) throw Error('CSV 引号未闭合')
    row.push(cell)
    if (row.some(Boolean)) rows.push(row)
    return rows
  }
  async function importFile(event: Event) {
    const input = event.target as HTMLInputElement,
      file = input.files?.[0]
    if (!file) return
    try {
      if (file.size > 2 * 1024 * 1024) throw Error('请上传小于 2 MB 的 CSV 文件')
      const rows = parseCsv((await file.text()).replace(/^\uFEFF/, ''))
      const header = rows.shift()
      if (header?.join(',') !== '工号,姓名,部门,岗位,邮箱')
        throw Error('表头不正确，请使用员工导入模板')
      if (!rows.length) throw Error('文件中没有员工记录')
      const seen = new Set(s.data.employees.map((e) => e.no))
      const added: Employee[] = []
      rows.forEach((r, i) => {
        if (!r[0]?.trim() || !r[1]?.trim() || !r[3]?.trim())
          throw Error(`第 ${i + 2} 行缺少工号、姓名或岗位`)
        if (seen.has(r[0].trim())) throw Error(`第 ${i + 2} 行工号重复：${r[0]}`)
        if (!departments.includes(r[2]!)) throw Error(`第 ${i + 2} 行部门不存在`)
        if (r[4] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(r[4]))
          throw Error(`第 ${i + 2} 行邮箱格式错误`)
        seen.add(r[0].trim())
        added.push({
          id: Date.now() + i,
          no: r[0].trim(),
          name: r[1].trim(),
          department: r[2]!,
          job: r[3]!,
          email: r[4] || '',
          status: '在职',
          progress: 0,
          hours: 0,
          avatar: 'blue'
        })
      })
      s.data.employees.unshift(...added)
      dept.value = '全部部门'
      keyword.value = ''
      ElMessage.success(`成功导入 ${added.length} 名员工`)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '导入失败')
    } finally {
      input.value = ''
    }
  }
  function toggleAll(value: boolean) {
    selected.value = value ? visible.value.map((e) => e.id) : []
  }
</script>
<template>
  <div
    ><PageTitle title="组织与员工" description="连接每一位伙伴，让团队的成长清晰可见。"
      ><el-button @click="exportRows()"><AppIcon name="download" :size="15" />导出名单</el-button
      ><el-dropdown
        @command="(v: string) => (v === 'template' ? exportRows(true) : fileInput?.click())"
        ><el-button
          ><AppIcon name="upload" :size="15" />导入员工<AppIcon name="down" :size="12" /></el-button
        ><template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item command="template">下载 CSV 模板</el-dropdown-item
            ><el-dropdown-item command="import">上传员工 CSV</el-dropdown-item></el-dropdown-menu
          ></template
        ></el-dropdown
      ><el-button type="primary" @click="edit()"
        ><AppIcon name="plus" :size="15" />新增员工</el-button
      ><input ref="fileInput" type="file" accept=".csv" hidden @change="importFile" /></PageTitle
    ><div class="organization-layout"
      ><aside class="panel department-tree"
        ><div class="panel-heading"><h3>组织架构</h3><AppIcon name="building" :size="18" /></div
        ><button :class="{ active: dept === '全部部门' }" @click="dept = '全部部门'"
          ><AppIcon name="building" :size="17" /><b>{{ s.data.settings.company }}</b
          ><span>{{ s.data.employees.length }}</span></button
        ><button v-for="d in departments" :key="d" :class="{ active: dept === d }" @click="dept = d"
          ><AppIcon name="collection" :size="16" /><span>{{ d }}</span
          ><small>{{ s.data.employees.filter((e) => e.department === d).length }}</small></button
        ><div class="organization-note"
          ><span class="eyebrow">PEOPLE FIRST</span><p>每一位伙伴<br />都是团队的无限可能。</p></div
        ></aside
      ><section class="panel employee-table"
        ><div class="panel-heading"
          ><div
            ><h3
              >{{ dept === '全部部门' ? '全部员工' : dept
              }}<span class="number-badge">{{ filtered.length }}</span></h3
            ><p>管理人员信息与学习状态</p></div
          ></div
        ><div class="filter-row"
          ><el-input
            v-model="keyword"
            placeholder="搜索姓名、工号或岗位"
            clearable
            class="search-input"
            ><template #prefix><AppIcon name="search" :size="16" /></template></el-input
          ><el-select v-model="status" aria-label="员工状态" style="width: 130px"
            ><el-option
              v-for="v in ['全部状态', '在职', '已停用']"
              :key="v"
              :value="v" /></el-select></div
        ><div class="table-scroll"
          ><table class="data-table employee-data"
            ><thead
              ><tr
                ><th
                  ><el-checkbox
                    :model-value="!!visible.length && visible.every((e) => selected.includes(e.id))"
                    aria-label="选择当前页全部员工"
                    @change="(v: boolean | string | number) => toggleAll(!!v)" /></th
                ><th>员工信息</th><th>部门 / 岗位</th><th>学习进度</th><th>状态</th
                ><th>操作</th></tr
              ></thead
            ><tbody
              ><tr v-for="e in visible" :key="e.id"
                ><td
                  ><el-checkbox
                    :model-value="selected.includes(e.id)"
                    :aria-label="'选择 ' + e.name"
                    @change="
                      (v: boolean | string | number) =>
                        v ? selected.push(e.id) : selected.splice(selected.indexOf(e.id), 1)
                    " /></td
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
                ><td><StatusBadge :status="e.status" /></td
                ><td
                  ><div class="inline-actions"
                    ><button class="text-link" @click="edit(e)">编辑</button
                    ><button class="text-link muted-link" @click="toggle(e)">{{
                      e.status === '在职' ? '停用' : '启用'
                    }}</button></div
                  ></td
                ></tr
              ></tbody
            ></table
          ></div
        ><el-empty v-if="!filtered.length" description="暂无匹配员工" /><div class="pagination-bar"
          ><span>{{
            selected.length
              ? '已选择 ' + selected.length + ' 名员工'
              : '共 ' + filtered.length + ' 名员工'
          }}</span
          ><el-pagination
            v-model:current-page="page"
            :total="filtered.length"
            :page-size="10"
            layout="prev,pager,next"
            background
            hide-on-single-page /></div></section></div
    ><el-dialog
      v-model="editor"
      :title="editId ? '编辑员工' : '新增员工'"
      width="560px"
      class="responsive-dialog"
      ><el-form label-position="top"
        ><div class="form-grid"
          ><el-form-item label="工号 *"
            ><el-input
              v-model="form.no"
              maxlength="30"
              placeholder="例如 E20260129" /></el-form-item
          ><el-form-item label="姓名 *"
            ><el-input
              v-model="form.name"
              maxlength="30"
              placeholder="输入员工姓名" /></el-form-item
          ><el-form-item label="所属部门"
            ><el-select v-model="form.department"
              ><el-option v-for="d in departments" :key="d" :value="d" /></el-select></el-form-item
          ><el-form-item label="岗位 *"
            ><el-input v-model="form.job" maxlength="40" /></el-form-item></div
        ><el-form-item label="工作邮箱"
          ><el-input v-model="form.email" maxlength="100" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editor = false">取消</el-button
        ><el-button type="primary" @click="save">保存员工</el-button></template
      ></el-dialog
    ></div
  >
</template>
