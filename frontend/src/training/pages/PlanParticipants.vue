<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { exportCsv } from '../api/repository'
  import { previewRoster, type RosterImportRow } from '../services/roster'
  const s = useTraining(),
    router = useRouter(),
    id = Number(useRoute().params.id)
  const plan = computed(() => s.data.plans.find((p) => p.id === id))
  const initial = () =>
    plan.value?.participantIds
      ? [...plan.value.participantIds]
      : s.data.employees
          .filter(
            (e) =>
              e.status === '在职' &&
              (plan.value?.department === '全体部门' || e.department === plan.value?.department)
          )
          .map((e) => e.id)
  const ids = ref(initial()),
    baseline = ref(JSON.stringify(ids.value)),
    keyword = ref(''),
    department = ref('全部部门'),
    selectedKeyword = ref(''),
    page = ref(1),
    selectedPage = ref(1),
    importOpen = ref(false),
    importRows = ref<RosterImportRow[]>([]),
    importing = ref(false),
    fileInput = ref<HTMLInputElement>()
  const dirty = computed(
    () =>
      JSON.stringify([...ids.value].sort((a, b) => a - b)) !==
      JSON.stringify((JSON.parse(baseline.value) as number[]).sort((a, b) => a - b))
  )
  const candidates = computed(() =>
    s.data.employees.filter(
      (e) =>
        (department.value === '全部部门' || e.department === department.value) &&
        (e.name + e.no).toLowerCase().includes(keyword.value.trim().toLowerCase())
    )
  )
  const selected = computed(() => s.data.employees.filter((e) => ids.value.includes(e.id)))
  const selectedRows = computed(() =>
    selected.value.filter((e) =>
      (e.name + e.no).toLowerCase().includes(selectedKeyword.value.trim().toLowerCase())
    )
  )
  const additions = computed(() => importRows.value.filter((r) => r.status === '新增'))
  const errors = computed(() => importRows.value.filter((r) => r.status === '错误'))
  watch([keyword, department], () => (page.value = 1))
  watch(selectedKeyword, () => (selectedPage.value = 1))
  watch(
    () => selectedRows.value.length,
    (len) => (selectedPage.value = Math.min(selectedPage.value, Math.max(1, Math.ceil(len / 8))))
  )
  function toggle(id: number) {
    if (ids.value.includes(id)) ids.value = ids.value.filter((v) => v !== id)
    else ids.value.push(id)
  }
  function addFiltered() {
    ids.value = [
      ...new Set([
        ...ids.value,
        ...candidates.value.filter((e) => e.status === '在职').map((e) => e.id)
      ])
    ]
    ElMessage.success('已将筛选结果中的在职员工加入待保存名单')
  }
  function exportRows(template = false) {
    exportCsv(
      template ? '参训名单模板.csv' : '参训名单.csv',
      template ? ['工号', '姓名'] : ['工号', '姓名', '部门', '账号状态', '计划完成度'],
      template
        ? [
            [
              s.data.employees.find((e) => e.status === '在职')?.no || 'E20260001',
              s.data.employees.find((e) => e.status === '在职')?.name || '林晓'
            ]
          ]
        : selected.value.map((e) => [
            e.no,
            e.name,
            e.department,
            e.status,
            s.planProgress(plan.value!, e.id) + '%'
          ])
    )
  }
  async function importFile(event: Event) {
    const input = event.target as HTMLInputElement,
      file = input.files?.[0]
    input.value = ''
    if (!file) return
    importing.value = true
    try {
      if (!/\.csv$/i.test(file.name) || file.size > 2 * 1024 * 1024)
        throw Error('请选择小于 2 MB 的 UTF-8 CSV 文件')
      importRows.value = previewRoster(await file.text(), s.data.employees, ids.value)
      importOpen.value = true
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '文件读取失败')
    } finally {
      importing.value = false
    }
  }
  function confirmImport() {
    if (errors.value.length) return
    ids.value = [...new Set([...ids.value, ...additions.value.map((r) => r.employeeId!)])]
    importOpen.value = false
    ElMessage.success('名单已合并，请点击保存名单生效')
  }
  async function save() {
    if (!plan.value) return
    try {
      await ElMessageBox.confirm(
        `保存 ${ids.value.length} 人的精确参训名单？已发布计划会自动分配关联课程。移出名单不删除历史学习记录。`,
        '确认参训名单',
        { confirmButtonText: '保存并生效', cancelButtonText: '继续调整' }
      )
      s.saveRoster(id, ids.value)
      baseline.value = JSON.stringify(ids.value)
      ElMessage.success('参训名单已保存，员工端培训范围已同步')
    } catch (e) {
      if (e instanceof Error) ElMessage.warning(e.message)
    }
  }
  onBeforeRouteLeave(async () => {
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm('参训名单尚未保存，离开将放弃调整。', '有未保存的名单', {
        confirmButtonText: '放弃并离开',
        cancelButtonText: '继续调整'
      })
      return true
    } catch {
      return false
    }
  })
</script>
<template>
  <div v-if="plan" class="management-page"
    ><button class="back-link" @click="router.push('/plans')"
      ><AppIcon name="left" :size="15" />返回培训计划</button
    ><PageTitle title="参训名单" :description="plan.title"
      ><el-button @click="exportRows()"
        ><AppIcon name="download" :size="15" />导出当前名单</el-button
      ><el-button type="primary" :disabled="!dirty && !!plan.participantIds" @click="save"
        >保存名单</el-button
      ></PageTitle
    >
    <div class="management-banner"
      ><span class="stat-icon blue"><AppIcon name="user" :size="29" /></span
      ><div
        ><span class="eyebrow">BRING THE RIGHT PEOPLE TOGETHER</span
        ><h2>把学习机会，送到每一位伙伴。</h2
        ><p>按部门添加、逐人调整，也可以通过 CSV 批量导入。</p></div
      ><div class="management-metrics"
        ><span
          ><b>{{ selected.length }}</b
          >名参训员工</span
        ><span
          ><b>{{ selected.filter((e) => s.planProgress(plan!, e.id) === 100).length }}</b
          >人已完成</span
        ></div
      ></div
    >
    <p v-if="!plan.participantIds" class="analytics-note"
      ><AppIcon
        name="info"
        :size="16"
      />这是旧版部门范围计划。当前为建议名单；保存后切换为精确人员名单，统计按实际成员重新计算。</p
    >
    <div class="roster-layout"
      ><section class="panel roster-candidates"
        ><div class="panel-heading"
          ><div><h3>从组织中选择</h3><p>仅在职员工可新增参训</p></div
          ><span class="muted small">{{ candidates.length }} 人</span></div
        ><div class="roster-filters"
          ><el-input v-model="keyword" placeholder="搜索员工姓名或工号" clearable
            ><template #prefix><AppIcon name="search" :size="15" /></template></el-input
          ><el-select v-model="department" aria-label="候选员工部门"
            ><el-option
              v-for="d in ['全部部门', ...departments]"
              :key="d"
              :value="d" /></el-select></div
        ><div class="roster-bulk"
          ><span>{{ candidates.filter((e) => e.status === '在职').length }} 名在职员工</span
          ><button class="text-link" @click="addFiltered">添加筛选结果</button></div
        >
        <div class="employee-choice-list"
          ><label
            v-for="e in candidates.slice((page - 1) * 8, page * 8)"
            :key="e.id"
            :class="{ checked: ids.includes(e.id), disabled: e.status !== '在职' }"
            ><input
              type="checkbox"
              :checked="ids.includes(e.id)"
              :disabled="e.status !== '在职' && !ids.includes(e.id)"
              :aria-label="'选择员工 ' + e.no + ' ' + e.name"
              @change="toggle(e.id)" /><span class="avatar" :class="e.avatar">{{
              e.name.slice(0, 1)
            }}</span
            ><span
              ><b>{{ e.name }}</b
              ><small>{{ e.no }} · {{ e.department }}</small></span
            ><AppIcon v-if="ids.includes(e.id)" name="check" :size="16" /><StatusBadge
              v-else-if="e.status !== '在职'"
              :status="e.status" /></label></div
        ><el-empty v-if="!candidates.length" description="没有匹配的员工" :image-size="75" /><div
          class="roster-pagination"
          ><el-pagination
            v-model:current-page="page"
            :total="candidates.length"
            :page-size="8"
            layout="prev,pager,next"
            small
            background
        /></div> </section
      ><section class="panel roster-selected"
        ><div class="panel-heading"
          ><div
            ><h3
              >本次参训名单 <span class="inline-count">{{ ids.length }}</span></h3
            ><p>保存后按此名单展示员工端培训</p></div
          ><span class="edit-state" :class="{ dirty: dirty || !plan.participantIds }">{{
            dirty ? '待保存' : plan.participantIds ? '已同步' : '建议名单待确认'
          }}</span></div
        ><div class="roster-import-bar"
          ><el-button size="small" :loading="importing" @click="fileInput?.click()"
            ><AppIcon name="upload" :size="14" />导入 CSV 名单</el-button
          ><button class="text-link" @click="exportRows(true)">下载模板</button
          ><input
            ref="fileInput"
            type="file"
            class="sr-only-input"
            accept=".csv"
            aria-label="导入参训名单文件"
            @change="importFile" /></div
        ><div class="selected-search"
          ><el-input v-model="selectedKeyword" clearable placeholder="在已选名单中搜索" /></div
        ><div class="table-scroll"
          ><table class="data-table"
            ><thead
              ><tr><th>员工</th><th>部门</th><th>计划进度</th><th>操作</th></tr></thead
            ><tbody
              ><tr
                v-for="e in selectedRows.slice((selectedPage - 1) * 8, selectedPage * 8)"
                :key="e.id"
                ><td
                  ><b>{{ e.name }}</b
                  ><small class="block muted"
                    >{{ e.no }}<span v-if="e.status !== '在职'"> · 已停用</span></small
                  ></td
                ><td>{{ e.department }}</td
                ><td
                  ><div class="table-progress"
                    ><el-progress
                      :percentage="s.planProgress(plan, e.id)"
                      :stroke-width="5" /></div></td
                ><td
                  ><button
                    class="text-link muted-link"
                    :aria-label="'移出参训 ' + e.no"
                    @click="toggle(e.id)"
                    >移出</button
                  ></td
                ></tr
              ></tbody
            ></table
          ></div
        ><el-empty
          v-if="!selectedRows.length"
          :description="
            selected.length ? '没有匹配的已选员工' : '从左侧选择员工，或导入一份参训名单'
          "
          :image-size="90"
        /><div class="roster-pagination"
          ><el-pagination
            v-model:current-page="selectedPage"
            :total="selectedRows.length"
            :page-size="8"
            layout="prev,pager,next"
            small
            background /></div
        ><div class="roster-footnote"
          ><AppIcon name="info" :size="16" /><p
            >名单变更不会删除员工个人学习记录。已发布计划保存后自动分配可用课程；草稿仅保存名单。</p
          ></div
        >
      </section></div
    >
    <el-dialog v-model="importOpen" title="核对导入名单" width="750px" class="responsive-dialog"
      ><div class="import-summary"
        ><span
          >新增 <b>{{ additions.length }}</b></span
        ><span
          >重复 / 已在名单 <b>{{ importRows.length - additions.length - errors.length }}</b></span
        ><span :class="{ 'import-error': errors.length }"
          >错误 <b>{{ errors.length }}</b></span
        ></div
      ><p class="demo-note"
        >导入采用追加合并，不替换现有名单。全部 {{ importRows.length }} 条均已校验；预览最多 200
        条，错误优先。出现错误时整批不导入，请修正文件后重试。</p
      ><div class="table-scroll import-preview"
        ><table class="data-table"
          ><thead
            ><tr><th>行号</th><th>工号</th><th>姓名</th><th>检查结果</th></tr></thead
          ><tbody
            ><tr
              v-for="r in [...errors, ...importRows.filter((r) => r.status !== '错误')].slice(
                0,
                200
              )"
              :key="r.line"
              ><td>{{ r.line }}</td
              ><td>{{ r.no || '—' }}</td
              ><td>{{ r.name || '—' }}</td
              ><td :class="{ 'import-error': r.status === '错误' }">{{
                r.error || r.status
              }}</td></tr
            ></tbody
          ></table
        ></div
      ><template #footer
        ><el-button @click="importOpen = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!!errors.length || !additions.length"
          @click="confirmImport"
          >确认导入 {{ additions.length }} 人</el-button
        ></template
      ></el-dialog
    > </div
  ><el-empty v-else description="培训计划不存在" />
</template>
