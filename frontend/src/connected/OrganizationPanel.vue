<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
  import {
    api,
    ApiError,
    currentUser,
    type Access,
    type Department,
    type Employee,
    type EmployeeForm,
    type AuditEntry,
    type DataScope,
    type SetupLink,
    type BatchResult,
    type InvitationDelivery,
    type BatchHistory
  } from './api'

  import EmployeeImportPanel from './EmployeeImportPanel.vue'
  import EmployeeBatchPanel from './EmployeeBatchPanel.vue'
  import DepartmentLeaderPanel from './DepartmentLeaderPanel.vue'

  const selected = ref<Employee[]>([])
  const batchBusy = ref(false)
  const leaderDepartment = ref<Department | null>(null)
  const leaderEditor = ref<HTMLElement | null>(null)
  const access = ref<Access | null>(null)
  const departments = ref<Department[]>([])
  const employees = ref<Employee[]>([])
  const logs = ref<AuditEntry[]>([])
  const tab = ref<'employees' | 'departments' | 'imports' | 'audit' | 'history'>('employees')
  const busy = ref(false)
  const error = ref('')
  const notice = ref('')
  const employeeEditor = ref<HTMLElement | null>(null)
  const departmentEditor = ref<HTMLElement | null>(null)
  const errorAlert = ref<HTMLElement | null>(null)
  const page = ref(1),
    total = ref(0),
    auditPage = ref(1),
    auditTotal = ref(0)
  const filters = reactive({ keyword: '', department: '', status: '', role: '' })
  const outcome = ref('')
  const history = ref<BatchHistory[]>([]), historyTotal = ref(0), historyPage = ref(1)
  const setupLink = ref<SetupLink | null>(null)
  const invitation = ref<InvitationDelivery | null>(null)
  const setupPanel = ref<HTMLElement | null>(null)
  const setupUrl = computed(() =>
    setupLink.value
      ? `${window.location.origin}/#/account-setup?employeeNo=${encodeURIComponent(setupLink.value.employeeNo)}&token=${encodeURIComponent(setupLink.value.token)}`
      : ''
  )
  const employeeOpen = ref(false),
    employeeId = ref<number | null>(null)
  const departmentOpen = ref(false),
    departmentId = ref<number | null>(null)
  const scopeLabels: Record<DataScope, string> = {
    SELF: '本人',
    DEPARTMENT: '本部门',
    DEPARTMENT_TREE: '本部门及下级',
    CUSTOM: '自定义部门',
    ALL: '全部数据'
  }
  const freshEmployee = (): EmployeeForm => ({
    employeeNo: '',
    name: '',
    departmentId: departments.value.find((d) => d.status === 'ACTIVE')?.id ?? 0,
    jobTitle: '',
    email: '',
    phone: '',
    roleCode: 'EMPLOYEE',
    superAdmin: false,
    dataScope: 'SELF',
    customDepartmentIds: [],
    status: 'ACTIVE',
    activated: true,
    initialPassword: '',
    version: 0
  })
  const employeeForm = reactive<EmployeeForm>(freshEmployee())
  const departmentForm = reactive({
    parentId: null as number | null,
    code: '',
    name: '',
    status: 'ACTIVE',
    sortNo: 0,
    version: 0
  })
  const departmentTree = computed(() => {
    const result: Array<Department & { depth: number }> = [],
      seen = new Set<number>()
    const visit = (d: Department, depth: number) => {
      if (seen.has(d.id)) return
      seen.add(d.id)
      result.push({ ...d, depth })
      departments.value
        .filter((child) => child.parentId === d.id)
        .forEach((child) => visit(child, depth + 1))
    }
    departments.value
      .filter((d) => !departments.value.some((parent) => parent.id === d.parentId))
      .forEach((d) => visit(d, 0))
    return result
  })
  const activeDepartments = computed(() =>
    departmentTree.value.filter((d) => d.status === 'ACTIVE')
  )
  const locked = (e: Employee) => !!e.lockedUntil && new Date(e.lockedUntil).getTime() > Date.now()
  const canEdit = (e: Employee) => access.value?.superAdmin || e.roleCode === 'EMPLOYEE'
  const canBatch = (e: Employee) => canEdit(e) && !e.superAdmin && e.id !== currentUser.value?.id
  const allPageSelected = computed(() => {
    const eligible = employees.value.filter(canBatch)
    return eligible.length > 0 && eligible.every((e) => selected.value.some((s) => s.id === e.id))
  })
  function removeSelected(id: number) {
    selected.value = selected.value.filter((e) => e.id !== id)
  }
  function toggleSelected(e: Employee) {
    if (selected.value.some((s) => s.id === e.id)) {
      removeSelected(e.id)
      return
    }
    if (selected.value.length >= 200) {
      error.value = '每批最多选择 200 人，请先提交或清空选择'
      return
    }
    selected.value = [...selected.value, e]
  }
  function togglePage() {
    const eligible = employees.value.filter(canBatch)
    if (allPageSelected.value) {
      selected.value = selected.value.filter((e) => !eligible.some((p) => p.id === e.id))
      return
    }
    const added = eligible.filter((e) => !selected.value.some((s) => s.id === e.id))
    if (selected.value.length + added.length > 200) {
      error.value = '选择本页将超过 200 人，请先提交或减少选择'
      return
    }
    selected.value = [...selected.value, ...added]
  }
  async function refreshOrganization() {
    selected.value = []
    leaderDepartment.value = null
    await run(async () => {
      departments.value = await api.departments()
      await loadEmployees()
      if (tab.value === 'audit') await loadLogs()
    })
  }
  async function batchDone(result: BatchResult) {
    busy.value = true
    error.value = ''
    selected.value = []
    setupLink.value = null
    notice.value = `批量变更已保存：选择 ${result.requested} 人，实际修改 ${result.changed} 人，解除负责人任命 ${result.clearedLeaders} 个。`
    try {
      departments.value = await api.departments()
      await loadEmployees()
    } catch {
      error.value = '批量操作已成功，列表刷新失败，请点击刷新数据。'
    } finally {
      busy.value = false
    }
  }
  async function editLeader(d: Department) {
    leaderDepartment.value = d
    await nextTick()
    leaderEditor.value?.scrollIntoView({ block: 'start' })
  }
  async function leaderSaved() {
    leaderDepartment.value = null
    await run(async () => {
      departments.value = await api.departments()
      notice.value = '部门负责人已保存，账号权限保持原配置。'
    })
  }
  async function run(action: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
      await action()
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : '网络请求失败，请重试'
      await nextTick()
      errorAlert.value?.scrollIntoView({ block: 'center' })
      errorAlert.value?.focus({ preventScroll: true })
    } finally {
      busy.value = false
    }
  }
  async function loadEmployees() {
    const result = await api.employees(
      page.value,
      filters.keyword,
      filters.department,
      filters.status,
      filters.role
    )
    employees.value = result.list
    total.value = result.total
  }
  async function search() {
    page.value = 1
    await run(loadEmployees)
  }
  async function changePage(step: number) {
    page.value += step
    await run(loadEmployees)
  }
  function closeEmployee() {
    employeeOpen.value = false
    employeeForm.initialPassword = ''
  }
  async function searchLogs() {
    auditPage.value = 1
    await run(loadLogs)
  }
  async function pageLogs(step: number) {
    auditPage.value += step
    await run(loadLogs)
  }
  async function loadLogs() {
    const result = await api.auditLogs(auditPage.value, outcome.value)
    logs.value = result.list
    auditTotal.value = result.total
  }
  async function switchTab(value: typeof tab.value) {
    tab.value = value
    if (value === 'audit') await run(loadLogs)
    if (value === 'history') await run(loadHistory)
  }
  async function loadHistory() { const result = await api.batchHistory(historyPage.value); history.value = result.list; historyTotal.value = result.total }
  async function rollback(item: BatchHistory) {
    if (item.rolledBackAt || !window.confirm('确认回滚这次批量变更？系统会检查目标账号版本，发现后续改动则整批拒绝。')) return
    await run(async () => { await api.rollbackBatch(item.requestId, '管理员回滚'); await loadHistory(); await loadEmployees(); notice.value = '批量变更已回滚' })
  }
  function editEmployee(e?: Employee) {
    employeeId.value = e?.id ?? null
    Object.assign(
      employeeForm,
      freshEmployee(),
      e ? { ...e, customDepartmentIds: [...e.customDepartmentIds], initialPassword: '' } : {}
    )
    employeeOpen.value = true
    void nextTick(() => employeeEditor.value?.scrollIntoView({ block: 'start' }))
    error.value = ''
    notice.value = ''
  }
  function editDepartment(d?: Department) {
    departmentId.value = d?.id ?? null
    Object.assign(
      departmentForm,
      d ?? { parentId: null, code: '', name: '', status: 'ACTIVE', sortNo: 0, version: 0 }
    )
    departmentOpen.value = true
    void nextTick(() => departmentEditor.value?.scrollIntoView({ block: 'start' }))
    error.value = ''
    notice.value = ''
  }
  async function saveEmployee() {
    await run(async () => {
      await api.saveEmployee(employeeId.value, employeeForm)
      employeeOpen.value = false
      employeeForm.initialPassword = ''
      selected.value = []
      departments.value = await api.departments()
      await loadEmployees()
      notice.value = '员工资料已保存；账号状态或权限变更会撤销原登录会话。'
    })
  }
  async function saveDepartment() {
    await run(async () => {
      await api.saveDepartment(departmentId.value, departmentForm)
      departmentOpen.value = false
      departments.value = await api.departments()
      await loadEmployees()
      notice.value = '部门已保存'
    })
  }
  async function unlock(e: Employee) {
    await run(async () => {
      await api.unlockEmployee(e.id, e.version)
      await loadEmployees()
      notice.value = `${e.name} 的登录锁定已解除`
    })
  }
  async function issueLink(e: Employee) {
    if (
      e.activated &&
      !window.confirm(
        `确定为 ${e.name} 重置密码？旧密码和现有登录会话将立即失效，员工需使用新的链接设置密码。`
      )
    )
      return
    await run(async () => {
      setupLink.value = await api.issueSetupLink(e.id, e.version)
      await loadEmployees()
      await nextTick()
      setupPanel.value?.scrollIntoView({ block: 'center' })
    })
  }
  async function copyLink() {
    await run(async () => {
      await navigator.clipboard.writeText(setupUrl.value)
      notice.value = '链接已复制，请单独交付给对应员工。'
    })
  }
  async function deliverInvitation(e: Employee, channel: 'EMAIL' | 'SMS') {
    await run(async () => {
      invitation.value = await api.deliverInvitation(e.id, e.version, channel)
      setupLink.value = {
        userId: invitation.value.userId,
        employeeNo: invitation.value.employeeNo,
        token: invitation.value.setupToken ?? '',
        purpose: e.activated ? 'RESET' : 'ACTIVATE',
        expiresAt: invitation.value.expiresAt
      }
      await loadEmployees()
      notice.value = `${channel === 'EMAIL' ? '邮件' : '短信'}邀请（模拟）已进入投递队列：${invitation.value.destination}`
    })
  }
  watch(
    () => employeeForm.roleCode,
    (role) => {
      if (role === 'EMPLOYEE') {
        employeeForm.superAdmin = false
        employeeForm.dataScope = 'SELF'
        employeeForm.customDepartmentIds = []
      }
    }
  )
  watch(
    () => employeeForm.superAdmin,
    (value) => {
      if (value) employeeForm.dataScope = 'ALL'
    }
  )
  watch(
    () => employeeForm.dataScope,
    (value) => {
      if (value !== 'CUSTOM') employeeForm.customDepartmentIds = []
    }
  )
  onMounted(() =>
    run(async () => {
      access.value = await api.access()
      departments.value = await api.departments()
      await loadEmployees()
    })
  )
</script>

<template>
  <section class="page-section org-panel">
    <div class="section-heading"
      ><div
        ><span class="eyebrow">ORGANIZATION</span><h1>组织与员工</h1
        ><p v-if="access"
          >{{ access.superAdmin ? '超级管理员' : '培训 / 人事管理员' }} · 当前数据范围：{{
            scopeLabels[access.dataScope]
          }}</p
        ></div
      ><button class="outline-button" :disabled="busy || batchBusy" @click="refreshOrganization"
        >刷新数据</button
      ></div
    >
    <div class="org-tabs"
      ><button
        :class="{ selected: tab === 'employees' }"
        :disabled="busy || batchBusy"
        @click="switchTab('employees')"
        >员工账号</button
      ><button
        :class="{ selected: tab === 'departments' }"
        :disabled="busy || batchBusy"
        @click="switchTab('departments')"
        >部门树</button
      ><button
        :class="{ selected: tab === 'imports' }"
        :disabled="busy || batchBusy"
        @click="switchTab('imports')"
        >批量导入</button
      ><button
        v-if="access?.superAdmin"
        :class="{ selected: tab === 'audit' }"
        :disabled="busy || batchBusy"
        @click="switchTab('audit')"
        >操作审计</button
       ><button
         :class="{ selected: tab === 'history' }"
         :disabled="busy || batchBusy"
         @click="switchTab('history')"
         >批量变更历史</button
      ></div
    >
    <p v-if="error" ref="errorAlert" class="org-error" role="alert" tabindex="-1">{{ error }}</p
    ><p v-if="notice" class="org-notice" role="status">{{ notice }}</p
    ><p v-if="busy" class="muted" role="status">正在处理…</p>
    <aside v-if="setupLink" ref="setupPanel" class="panel org-setup"
      ><div class="panel-heading"
        ><h2>{{ setupLink.purpose === 'RESET' ? '重置密码链接已生成' : '激活链接已生成' }}</h2
        ><button class="text-button" @click="setupLink = null">关闭链接</button></div
      ><p
        >工号 {{ setupLink.employeeNo }} · 有效至
        {{
          new Date(setupLink.expiresAt).toLocaleString('zh-CN')
        }}。请单独交付给该员工，重新生成会让旧链接失效。</p
      ><label>一次性设置密码链接<textarea :value="setupUrl" readonly rows="3" /></label
      ><button class="outline-button" :disabled="busy || batchBusy" @click="copyLink"
        >复制链接</button
      ></aside
    >
    <template v-if="tab === 'employees'">
      <form class="org-filters" @submit.prevent="search"
        ><label
          >工号或姓名<input
            v-model="filters.keyword"
            maxlength="80"
            placeholder="输入关键词" /></label
        ><label
          >部门<select v-model="filters.department"
            ><option value="">全部授权部门</option
            ><option v-for="d in departmentTree" :key="d.id" :value="String(d.id)"
              >{{ '　'.repeat(d.depth) }}{{ d.name }}</option
            ></select
          ></label
        ><label
          >状态<select v-model="filters.status"
            ><option value="">全部状态</option
            ><option value="ACTIVE">启用</option
            ><option value="DISABLED">停用</option></select
          ></label
        ><label
          >角色<select v-model="filters.role"
            ><option value="">全部角色</option
            ><option value="EMPLOYEE">员工</option
            ><option value="ADMIN">管理员</option></select
          ></label
        ><button class="outline-button" :disabled="busy || batchBusy">查询</button
        ><button
          type="button"
          class="primary-button"
          :disabled="busy || batchBusy || !activeDepartments.length"
          @click="editEmployee()"
          >新增员工</button
        ></form
      >
      <div class="org-batch-select">
        <label
          ><input
            type="checkbox"
            :checked="allPageSelected"
            :disabled="busy || batchBusy || !employees.some(canBatch)"
            @change="togglePage"
          />选择本页可调整账号</label
        >
        <span>已选 {{ selected.length }} 人（最多 200 人，跨页保留）</span>
        <button
          class="text-button"
          :disabled="busy || batchBusy || !selected.length"
          @click="selected = []"
          >清空选择</button
        >
      </div>
      <EmployeeBatchPanel
        v-if="selected.length"
        :selected="selected"
        :departments="departments"
        :super-admin="!!access?.superAdmin"
        @busy="batchBusy = $event"
        @done="batchDone"
        @remove="removeSelected"
      />
      <div v-if="!employees.length && !busy" class="empty-state">当前范围内没有符合条件的员工</div>
      <div class="org-people"
        ><article v-for="e in employees" :key="e.id" class="org-person"
          ><label v-if="canBatch(e)" class="org-person-select"
            ><input
              type="checkbox"
              :checked="selected.some((s) => s.id === e.id)"
              :disabled="busy || batchBusy"
              @change="toggleSelected(e)"
            />选择 {{ e.employeeNo }} · {{ e.name }}</label
          ><div class="org-person-top"
            ><div
              ><strong>{{ e.name }}</strong
              ><small>{{ e.employeeNo }} · {{ e.departmentName || '未分配部门' }}</small></div
            ><span class="org-badge" :class="{ off: e.status !== 'ACTIVE' || !e.activated }">{{
              e.status !== 'ACTIVE'
                ? '已停用'
                : !e.activated
                  ? '未激活'
                  : locked(e)
                    ? '登录锁定'
                    : '启用中'
            }}</span></div
          ><p
            >{{ e.superAdmin ? '超级管理员' : e.roleCode === 'ADMIN' ? '管理员' : '员工'
            }}<span v-if="e.roleCode === 'ADMIN'"> · {{ scopeLabels[e.dataScope] }}</span></p
          ><p class="muted">{{ e.jobTitle || '岗位未填写' }} · {{ e.email || '邮箱未填写' }}</p
          ><div class="org-actions"
            ><button
              v-if="canEdit(e)"
              class="text-button"
              :disabled="busy || batchBusy"
              @click="editEmployee(e)"
              >编辑账号</button
            ><button
              v-if="canEdit(e) && locked(e)"
              class="text-button"
              :disabled="busy || batchBusy"
              @click="unlock(e)"
              >解除登录锁定</button
            ><button
              v-if="canEdit(e) && e.id !== currentUser?.id && e.status === 'ACTIVE'"
              class="text-button"
              :disabled="busy || batchBusy"
              @click="issueLink(e)"
              >{{ e.activated ? '重置密码' : '生成激活链接' }}</button
             ><button
               v-if="canEdit(e) && e.id !== currentUser?.id && e.status === 'ACTIVE' && e.email"
               class="text-button"
               :disabled="busy || batchBusy"
               @click="deliverInvitation(e, 'EMAIL')"
               >发邮件邀请</button
             ><button
               v-if="canEdit(e) && e.id !== currentUser?.id && e.status === 'ACTIVE' && e.phone"
               class="text-button"
               :disabled="busy || batchBusy"
               @click="deliverInvitation(e, 'SMS')"
               >发短信邀请</button
            ><span v-if="e.id === currentUser?.id" class="muted">当前账号</span></div
          ></article
        ></div
      >
      <div class="org-pagination"
        ><span>共 {{ total }} 人 · 第 {{ page }} 页</span
        ><button
          class="outline-button"
          :disabled="busy || batchBusy || page <= 1"
          @click="changePage(-1)"
          >上一页</button
        ><button
          class="outline-button"
          :disabled="busy || batchBusy || page * 20 >= total"
          @click="changePage(1)"
          >下一页</button
        ></div
      >
      <form
        v-if="employeeOpen"
        ref="employeeEditor"
        class="panel org-editor"
        @submit.prevent="saveEmployee"
        ><div class="panel-heading"
          ><h2>{{ employeeId ? '编辑员工账号' : '新增员工账号' }}</h2
          ><button
            type="button"
            class="text-button"
            :disabled="busy || batchBusy"
            @click="closeEmployee"
            >关闭</button
          ></div
        ><div class="form-grid"
          ><label
            >工号<input
              v-model="employeeForm.employeeNo"
              autocomplete="username"
              :disabled="!!employeeId"
              required
              pattern="[A-Za-z0-9_\-]{2,32}"
              maxlength="32" /></label
          ><label>姓名<input v-model="employeeForm.name" required maxlength="40" /></label
          ><label
            >所属部门<select v-model.number="employeeForm.departmentId" required
              ><option :value="0" disabled>请选择部门</option
              ><option v-for="d in activeDepartments" :key="d.id" :value="d.id"
                >{{ '　'.repeat(d.depth) }}{{ d.name }}</option
              ></select
            ></label
          ><label>岗位<input v-model="employeeForm.jobTitle" maxlength="80" /></label
          ><label>邮箱<input v-model="employeeForm.email" type="email" maxlength="120" /></label
          ><label>电话<input v-model="employeeForm.phone" maxlength="30" /></label
          ><label
            >账号状态<select v-model="employeeForm.status"
              ><option value="ACTIVE">启用</option
              ><option value="DISABLED">停用</option></select
            ></label
          ><label
            >激活状态<select v-model="employeeForm.activated"
              ><option :value="true">已激活</option
              ><option :value="false">未激活（禁止登录）</option></select
            ></label
          ><label v-if="!employeeId"
            >初始密码<input
              v-model="employeeForm.initialPassword"
              type="password"
              autocomplete="new-password"
              required
              minlength="8"
              maxlength="72"
              placeholder="至少 8 位，含字母和数字" /></label
          ><template v-if="access?.superAdmin"
            ><label
              >角色<select v-model="employeeForm.roleCode"
                ><option value="EMPLOYEE">员工</option
                ><option value="ADMIN">管理员（培训 / 人事）</option></select
              ></label
            ><template v-if="employeeForm.roleCode === 'ADMIN'"
              ><label
                >管理级别<select v-model="employeeForm.superAdmin"
                  ><option :value="false">普通管理员</option
                  ><option :value="true">超级管理员</option></select
                ></label
              ><label
                >数据范围<select
                  v-model="employeeForm.dataScope"
                  :disabled="employeeForm.superAdmin"
                  ><option v-for="(label, value) in scopeLabels" :key="value" :value="value">{{
                    label
                  }}</option></select
                ></label
              ><fieldset v-if="employeeForm.dataScope === 'CUSTOM'" class="wide org-scope"
                ><legend>授权部门（不自动包含下级）</legend
                ><label v-for="d in activeDepartments" :key="d.id"
                  ><input
                    v-model="employeeForm.customDepartmentIds"
                    type="checkbox"
                    :value="d.id"
                  />{{ d.name }}</label
                ></fieldset
              ></template
            ></template
          ></div
        ><p class="muted"
          >停用、取消激活、调岗或修改权限后，原登录会话立即失效。至少保留一个启用的超级管理员。</p
        ><button class="primary-button" :disabled="busy || batchBusy">保存员工</button></form
      >
    </template>
    <template v-else-if="tab === 'history'">
      <div class="panel"><div class="panel-heading"><h2>批量调岗 / 角色变更</h2><button class="text-button" @click="loadHistory">刷新</button></div>
        <article v-for="item in history" :key="item.requestId" class="admin-paper-question"><div class="org-person-top"><div><strong>{{ item.operation === 'TRANSFER' ? '批量调岗' : '批量角色变更' }}</strong><small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }} · {{ item.result.changed }} 人发生变更</small></div><button class="outline-button" :disabled="busy || !!item.rolledBackAt" @click="rollback(item)">{{ item.rolledBackAt ? '已回滚' : '回滚' }}</button></div><p class="muted">请求 {{ item.requestId }} · 前后快照 {{ item.before.length }} / {{ item.after.length }} 人</p></article>
        <p v-if="!history.length" class="empty-state">暂无批量变更记录。</p>
      </div>
    </template>
    <EmployeeImportPanel v-else-if="tab === 'imports'" @imported="run(loadEmployees)" />
    <template v-else-if="tab === 'departments'">
      <div class="org-actions"
        ><p class="muted">{{
          access?.superAdmin
            ? '支持层级、排序和停用；有有效子部门或在职人员的部门不能停用。'
            : '显示当前授权范围内的部门；部门结构由超级管理员维护。'
        }}</p
        ><button
          v-if="access?.superAdmin"
          class="primary-button"
          :disabled="busy || batchBusy"
          @click="editDepartment()"
          >新增部门</button
        ></div
      >
      <div class="org-departments"
        ><article
          v-for="d in departmentTree"
          :key="d.id"
          :style="{ paddingLeft: `${16 + Math.min(d.depth, 5) * 18}px` }"
          ><div
            ><strong>{{ d.name }}</strong
            ><small
              >{{ d.code }} · 排序 {{ d.sortNo }} ·
              {{ d.status === 'ACTIVE' ? '启用' : '停用' }}</small
            ><small
              >负责人：{{
                d.leaderName ? `${d.leaderName} · ${d.leaderEmployeeNo}` : '未设置'
              }}</small
            ></div
          ><button
            v-if="access?.superAdmin"
            class="text-button"
            :disabled="busy || batchBusy"
            @click="editDepartment(d)"
            >编辑部门</button
          ><button
            v-if="access?.superAdmin && d.status === 'ACTIVE'"
            class="text-button"
            :disabled="busy || batchBusy"
            @click="editLeader(d)"
            >设置负责人</button
          ></article
        ></div
      ><p v-if="!departmentTree.length && !busy" class="empty-state">暂无可见部门</p>
      <div v-if="leaderDepartment && access?.superAdmin" ref="leaderEditor"
        ><DepartmentLeaderPanel
          :key="leaderDepartment.id"
          :department="leaderDepartment"
          @close="leaderDepartment = null"
          @saved="leaderSaved"
      /></div>
      <form
        v-if="departmentOpen && access?.superAdmin"
        ref="departmentEditor"
        class="panel org-editor"
        @submit.prevent="saveDepartment"
        ><div class="panel-heading"
          ><h2>{{ departmentId ? '编辑部门' : '新增部门' }}</h2
          ><button
            type="button"
            class="text-button"
            :disabled="busy || batchBusy"
            @click="departmentOpen = false"
            >关闭</button
          ></div
        ><div class="form-grid"
          ><label>部门名称<input v-model="departmentForm.name" required maxlength="80" /></label
          ><label
            >部门编码<input
              v-model="departmentForm.code"
              required
              pattern="[A-Za-z0-9_\-]{1,40}"
              maxlength="40" /></label
          ><label
            >上级部门<select v-model="departmentForm.parentId"
              ><option :value="null">顶级部门</option
              ><option
                v-for="d in departmentTree.filter((item) => item.id !== departmentId)"
                :key="d.id"
                :value="d.id"
                >{{ '　'.repeat(d.depth) }}{{ d.name }}</option
              ></select
            ></label
          ><label
            >状态<select v-model="departmentForm.status"
              ><option value="ACTIVE">启用</option
              ><option value="DISABLED">停用</option></select
            ></label
          ><label
            >排序<input
              v-model.number="departmentForm.sortNo"
              type="number"
              min="0"
              max="100000"
              required /></label></div
        ><button class="primary-button" :disabled="busy || batchBusy">保存部门</button></form
      >
    </template>
    <template v-else>
      <form class="org-filters" @submit.prevent="searchLogs"
        ><label
          >操作结果<select v-model="outcome"
            ><option value="">全部结果</option
            ><option value="SUCCESS">成功</option
            ><option value="DENIED">权限拒绝</option
            ><option value="FAILED">失败</option></select
          ></label
        ><button class="outline-button" :disabled="busy || batchBusy">查询日志</button></form
      ><p class="muted"
        >记录管理接口的新增、修改、发布和删除请求，包括失败与权限拒绝；不保存请求正文、密码或令牌。</p
      ><div class="org-audit"
        ><article v-for="log in logs" :key="log.id"
          ><div
            ><strong>{{ log.actorName || '未认证请求' }}</strong
            ><span class="org-badge" :class="{ off: log.outcome !== 'SUCCESS' }"
              >{{
                log.outcome === 'SUCCESS' ? '成功' : log.outcome === 'DENIED' ? '权限拒绝' : '失败'
              }}
              · {{ log.httpStatus }}</span
            ></div
          ><code>{{ log.action }} {{ log.target }}</code
          ><small
            >{{ new Date(log.occurredAt).toLocaleString('zh-CN') }} · #{{ log.id }}</small
          ></article
        ></div
      ><p v-if="!logs.length && !busy" class="empty-state">暂无操作记录</p
      ><div class="org-pagination"
        ><span>共 {{ auditTotal }} 条 · 第 {{ auditPage }} 页</span
        ><button
          class="outline-button"
          :disabled="busy || batchBusy || auditPage <= 1"
          @click="pageLogs(-1)"
          >上一页</button
        ><button
          class="outline-button"
          :disabled="busy || batchBusy || auditPage * 20 >= auditTotal"
          @click="pageLogs(1)"
          >下一页</button
        ></div
      >
    </template>
  </section>
</template>

<style scoped>
  .org-batch-select {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    margin: 20px 0;
    font-size: 13px;
    color: #4a6a60;
  }
  .org-batch-select label,
  .org-person-select {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .org-person-select {
    font-size: 13px;
    color: #55766b;
    padding-bottom: 14px;
  }
  .org-batch-select input[type='checkbox'],
  .org-person-select input[type='checkbox'] {
    width: 16px;
    height: 16px;
    flex: 0 0 16px;
    margin: 0;
    padding: 0;
  }
  .org-panel {
    min-width: 0;
  }
  .org-tabs {
    display: flex;
    gap: 8px;
    border-bottom: 1px solid #e2e8f0;
    margin-bottom: 24px;
    flex-wrap: wrap;
  }
  .org-tabs button {
    padding: 12px 18px;
    border: 0;
    background: transparent;
    color: #64748b;
    cursor: pointer;
    font-weight: 600;
  }
  .org-tabs .selected {
    color: #24685c;
    border-bottom: 3px solid #24685c;
  }
  .org-filters {
    display: flex;
    align-items: end;
    gap: 12px;
    flex-wrap: wrap;
    margin: 20px 0;
  }
  .org-filters label {
    display: grid;
    gap: 7px;
    font-size: 13px;
    min-width: 130px;
    flex: 1;
  }
  .org-people {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
    gap: 16px;
  }
  .org-person,
  .org-audit article {
    padding: 20px;
    background: white;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    min-width: 0;
  }
  .org-person-top,
  .org-audit article > div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: start;
  }
  .org-person strong {
    font-size: 17px;
  }
  .org-person small,
  .org-departments small,
  .org-audit small {
    display: block;
    color: #64748b;
    font-size: 12px;
    margin-top: 6px;
  }
  .org-person p {
    font-size: 13px;
    overflow-wrap: anywhere;
    margin: 14px 0;
  }
  .org-badge {
    font-size: 12px;
    color: #267357;
    background: #e7f4ed;
    border-radius: 20px;
    padding: 4px 9px;
    white-space: nowrap;
  }
  .org-badge.off {
    background: #fff0df;
    color: #986124;
  }
  .org-actions,
  .org-pagination {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }
  .org-pagination {
    justify-content: end;
    margin-top: 22px;
    font-size: 13px;
  }
  .org-pagination span {
    margin-right: auto;
    color: #64748b;
  }
  .org-editor {
    margin-top: 24px;
    scroll-margin: 160px;
  }
  .org-editor .form-grid {
    margin: 20px 0;
  }
  .org-editor label {
    display: grid;
    gap: 7px;
    font-size: 13px;
  }
  .org-editor input,
  .org-editor select,
  .org-filters input,
  .org-filters select {
    box-sizing: border-box;
    width: 100%;
    min-width: 0;
    padding: 10px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    background: white;
    color: #1e293b;
  }
  .org-editor input:disabled {
    background: #f1f5f9;
  }
  .org-editor p {
    font-size: 13px;
    line-height: 1.6;
    margin: 20px 0;
  }
  .org-scope {
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
  }
  .org-scope label {
    display: flex;
    align-items: center;
  }
  .org-scope input {
    width: auto;
  }
  .org-departments {
    background: white;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    margin-top: 20px;
    overflow: hidden;
  }
  .org-departments article {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 16px;
    border-bottom: 1px solid #edf1f4;
  }
  .org-departments article:last-child {
    border: 0;
  }
  .org-departments strong {
    overflow-wrap: anywhere;
  }
  .org-departments button {
    flex-shrink: 0;
  }
  .org-error,
  .org-notice {
    padding: 14px 16px;
    border-radius: 8px;
    line-height: 1.5;
  }
  .org-error {
    color: #a83232;
    background: #fff0ef;
  }
  .org-notice {
    color: #216448;
    background: #eaf7ef;
  }
  .org-audit {
    display: grid;
    gap: 12px;
  }
  .org-audit code {
    display: block;
    margin: 12px 0;
    font-size: 12px;
    overflow-wrap: anywhere;
  }
  .org-panel button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .org-panel button:focus-visible,
  .org-panel input:focus-visible,
  .org-panel select:focus-visible {
    outline: 3px solid #7db6a4;
    outline-offset: 3px;
  }
  @media (max-width: 600px) {
    .org-filters label {
      min-width: 100%;
      flex: none;
    }
    .org-editor .form-grid {
      grid-template-columns: 1fr;
    }
    .org-editor .wide {
      grid-column: auto;
    }
    .org-pagination {
      gap: 8px;
    }
    .org-pagination span {
      width: 100%;
    }
    .org-person {
      padding: 16px;
    }
    .org-panel .section-heading {
      align-items: start;
      gap: 12px;
    }
  }
  .org-setup {
    margin: 20px 0;
    scroll-margin: 160px;
  }
  .org-setup p {
    font-size: 13px;
    line-height: 1.7;
    color: #627a75;
  }
  .org-setup label {
    display: grid;
    gap: 8px;
    font-size: 13px;
    margin: 16px 0;
  }
  .org-setup textarea {
    width: 100%;
    box-sizing: border-box;
    resize: vertical;
    overflow-wrap: anywhere;
    border: 1px solid #c8dad2;
    border-radius: 8px;
    padding: 12px;
  }
</style>
