<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import {
    api,
    ApiError,
    type Employee,
    type Department,
    type DataScope,
    type Role,
    type BatchResult
  } from './api'
  const props = defineProps<{
    selected: Employee[]
    departments: Department[]
    superAdmin: boolean
  }>()
  const emit = defineEmits<{
    done: [result: BatchResult]
    busy: [value: boolean]
    remove: [id: number]
  }>()
  const operation = ref<'TRANSFER' | 'ROLES'>('TRANSFER')
  const departmentId = ref<number | null>(null),
    updateJobTitle = ref(false),
    jobTitle = ref('')
  const role = ref<Role>('EMPLOYEE'),
    scope = ref<DataScope>('SELF'),
    custom = ref<number[]>([])
  const busy = ref(false),
    error = ref('')
  const activeDepartments = computed(() => props.departments.filter((d) => d.status === 'ACTIVE'))
  const scopeLabels: Record<DataScope, string> = {
    SELF: '本人',
    DEPARTMENT: '本部门',
    DEPARTMENT_TREE: '本部门及下级',
    CUSTOM: '指定部门',
    ALL: '全部数据'
  }
  let attempt: { fingerprint: string; requestId: string } | null = null
  watch(role, (value) => {
    if (value === 'EMPLOYEE') {
      scope.value = 'SELF'
      custom.value = []
    }
  })
  watch(scope, (value) => {
    if (value !== 'CUSTOM') custom.value = []
  })
  async function submit() {
    if (busy.value) return
    error.value = ''
    const targets = props.selected
      .map((e) => ({ id: e.id, version: e.version }))
      .sort((a, b) => a.id - b.id)
    if (!targets.length || targets.length > 200) {
      error.value = '请选择 1–200 个账号'
      return
    }
    if (operation.value === 'TRANSFER' && !departmentId.value) {
      error.value = '请选择目标部门'
      return
    }
    if (operation.value === 'ROLES' && scope.value === 'CUSTOM' && !custom.value.length) {
      error.value = '请至少选择一个授权部门'
      return
    }
    const transferBody = {
      targets,
      departmentId: departmentId.value!,
      updateJobTitle: updateJobTitle.value,
      jobTitle: jobTitle.value.trim()
    }
    const roleBody = {
      targets,
      roleCode: role.value,
      dataScope: scope.value,
      customDepartmentIds: [...custom.value].sort((a, b) => a - b)
    }
    const destination =
      operation.value === 'TRANSFER'
        ? `调至“${activeDepartments.value.find((d) => d.id === departmentId.value)?.name}”${updateJobTitle.value ? `，岗位${jobTitle.value.trim() ? `改为“${jobTitle.value.trim()}”` : '清空'}` : '，保留各自岗位'}`
        : `设置为${role.value === 'ADMIN' ? `管理员，范围：${scopeLabels[scope.value]}` : '员工，范围：本人'}`
    if (
      !window.confirm(
        `确认将所选 ${targets.length} 人${destination}？实际发生变更的账号会退出原登录会话；调离的部门负责人任命会解除。有任何校验失败时整批不保存。`
      )
    )
      return
    const fingerprint = JSON.stringify([
      operation.value,
      operation.value === 'TRANSFER' ? transferBody : roleBody
    ])
    if (!attempt || attempt.fingerprint !== fingerprint)
      attempt = { fingerprint, requestId: crypto.randomUUID() }
    busy.value = true
    emit('busy', true)
    try {
      const result =
        operation.value === 'TRANSFER'
          ? await api.batchTransfer({ ...transferBody, requestId: attempt.requestId })
          : await api.batchRoles({ ...roleBody, requestId: attempt.requestId })
      emit('done', result)
    } catch (e) {
      error.value =
        e instanceof ApiError
          ? e.message
          : '请求结果未确认，可保留选择和配置重试；同一次请求不会重复修改账号。'
    } finally {
      busy.value = false
      emit('busy', false)
    }
  }
</script>
<template>
  <section class="batch-panel" aria-label="批量员工变更">
    <h2>批量调整 {{ selected.length }} 人</h2>
    <p>选择会跨页保留。请核对名单和目标配置；有版本冲突或越权时整批不保存。</p>
    <details
      ><summary>查看已选名单（{{ selected.length }} 人）</summary>
      <ul
        ><li v-for="e in selected" :key="e.id"
          ><span>{{ e.name }} · {{ e.employeeNo }} · {{ e.departmentName }}</span
          ><button
            type="button"
            :disabled="busy"
            :aria-label="`移除 ${e.employeeNo}`"
            @click="emit('remove', e.id)"
            >移除</button
          ></li
        ></ul
      >
    </details>
    <form @submit.prevent="submit">
      <fieldset :disabled="busy">
        <label
          >操作类型<select v-model="operation"
            ><option value="TRANSFER">批量调岗</option
            ><option v-if="superAdmin" value="ROLES">批量分配角色</option></select
          ></label
        >
        <template v-if="operation === 'TRANSFER'">
          <label
            >目标部门<select v-model="departmentId" required
              ><option :value="null" disabled>请选择部门</option
              ><option v-for="d in activeDepartments" :key="d.id" :value="d.id"
                >{{ d.name }}（{{ d.code }}）</option
              ></select
            ></label
          >
          <label class="inline"
            ><input v-model="updateJobTitle" type="checkbox" />同时修改岗位</label
          >
          <label v-if="updateJobTitle"
            >统一岗位<input
              v-model="jobTitle"
              maxlength="80"
              placeholder="留空将清空所选员工的岗位"
          /></label>
        </template>
        <template v-else>
          <label
            >目标角色<select v-model="role"
              ><option value="EMPLOYEE">员工</option
              ><option value="ADMIN">管理员</option></select
            ></label
          >
          <label v-if="role === 'ADMIN'"
            >人员数据范围<select v-model="scope"
              ><option v-for="(label, value) in scopeLabels" :key="value" :value="value">{{
                label
              }}</option></select
            ></label
          >
          <div v-if="scope === 'CUSTOM'" class="batch-scopes" role="group" aria-label="授权部门"
            ><label v-for="d in activeDepartments" :key="d.id" class="inline"
              ><input v-model="custom" type="checkbox" :value="d.id" />{{ d.name }}</label
            ></div
          >
          <p>批量操作不授予超级管理员权限；降为员工时清除原自定义部门授权。</p>
        </template>
      </fieldset>
      <p v-if="error" role="alert" class="batch-error">{{ error }}</p>
      <button class="primary-button" :disabled="busy">{{
        busy ? '正在保存…' : '核对并提交批量变更'
      }}</button>
    </form>
  </section>
</template>
<style scoped>
  .batch-panel {
    padding: 24px;
    margin: 20px 0;
    border: 1px solid #c8dfd7;
    border-radius: 12px;
    background: #f2f8f5;
  }
  .batch-panel h2 {
    font-size: 20px;
    margin: 0 0 12px;
  }
  .batch-panel p,
  .batch-panel summary {
    font-size: 14px;
    line-height: 1.7;
    color: #486962;
  }
  .batch-panel summary {
    cursor: pointer;
  }
  .batch-panel ul {
    list-style: none;
    padding: 0;
    max-height: 220px;
    overflow-y: auto;
  }
  .batch-panel li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 8px 0;
    font-size: 13px;
    overflow-wrap: anywhere;
  }
  .batch-panel li button {
    border: 0;
    color: #166e64;
    background: transparent;
    cursor: pointer;
    flex-shrink: 0;
  }
  .batch-panel fieldset {
    border: 0;
    padding: 0;
    margin: 20px 0;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }
  .batch-panel label {
    display: grid;
    gap: 9px;
    font-size: 14px;
    min-width: 0;
  }
  .batch-panel input:not([type='checkbox']),
  .batch-panel select {
    box-sizing: border-box;
    width: 100%;
    padding: 12px;
    border: 1px solid #c5d7d1;
    border-radius: 8px;
    background: white;
    min-width: 0;
  }
  .batch-panel .inline {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .batch-panel input[type='checkbox'] {
    width: 16px;
    height: 16px;
    flex: 0 0 16px;
    margin: 0;
    padding: 0;
  }
  .batch-scopes {
    display: grid;
    gap: 12px;
    grid-column: 1 / -1;
  }
  .batch-panel .batch-error {
    color: #a0362c;
  }
  .batch-panel button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
  @media (max-width: 650px) {
    .batch-panel {
      padding: 18px;
    }
    .batch-panel fieldset {
      grid-template-columns: minmax(0, 1fr);
    }
    .batch-panel .primary-button {
      width: 100%;
    }
  }
</style>
