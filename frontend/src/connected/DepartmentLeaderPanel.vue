<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { api, ApiError, type Department, type Employee } from './api'
  const props = defineProps<{ department: Department }>()
  const emit = defineEmits<{ saved: []; close: [] }>()
  const keyword = ref(''),
    page = ref(1),
    total = ref(0),
    employees = ref<Employee[]>([])
  const selected = ref<number | null>(props.department.leaderUserId)
  const selectedName = ref(
    props.department.leaderName
      ? `${props.department.leaderName} · ${props.department.leaderEmployeeNo}`
      : ''
  )
  const busy = ref(false),
    error = ref('')
  async function search(next = 1) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      const result = await api.employees(next, keyword.value, String(props.department.id), 'ACTIVE')
      employees.value = result.list
      page.value = next
      total.value = result.total
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : '无法读取候选人，请重试'
    } finally {
      busy.value = false
    }
  }
  function choose(e: Employee) {
    selected.value = e.id
    selectedName.value = `${e.name} · ${e.employeeNo}`
  }
  function clear() {
    selected.value = null
    selectedName.value = ''
  }
  async function save() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      await api.setDepartmentLeader(props.department.id, selected.value, props.department.version)
      emit('saved')
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : '保存失败，请关闭后刷新部门数据确认结果'
    } finally {
      busy.value = false
    }
  }
  onMounted(() => search())
</script>
<template>
  <section class="leader-panel" aria-label="部门负责人设置">
    <div class="leader-heading"
      ><h2>{{ department.name }} · 负责人</h2
      ><button class="text-button" :disabled="busy" @click="emit('close')"
        >关闭负责人设置</button
      ></div
    >
    <p>负责人须为本部门启用的账号。任命只维护组织信息，权限仍由账号角色和数据范围决定。</p>
    <form class="leader-search" @submit.prevent="search()"
      ><label>负责人候选人工号或姓名<input v-model="keyword" maxlength="80" /></label
      ><button class="outline-button" :disabled="busy">查询候选人</button></form
    >
    <div class="leader-options"
      ><label v-for="e in employees" :key="e.id"
        ><input
          type="radio"
          name="department-leader"
          :checked="selected === e.id"
          :disabled="busy"
          @change="choose(e)"
        /><span
          >{{ e.name }} · {{ e.employeeNo
          }}<small
            >{{ e.roleCode === 'ADMIN' ? '管理员' : '员工'
            }}{{ e.activated ? '' : ' · 未激活' }}</small
          ></span
        ></label
      ></div
    >
    <p v-if="!employees.length && !busy">暂无匹配的本部门启用账号</p>
    <div class="leader-pagination"
      ><span>共 {{ total }} 人 · 第 {{ page }} 页</span
      ><button class="outline-button" :disabled="busy || page <= 1" @click="search(page - 1)"
        >上一页候选人</button
      ><button
        class="outline-button"
        :disabled="busy || page * 20 >= total"
        @click="search(page + 1)"
        >下一页候选人</button
      ></div
    >
    <p class="leader-choice">{{
      selected ? `当前选择：${selectedName}` : '当前选择：暂不设置负责人'
    }}</p>
    <p v-if="error" role="alert" class="leader-error">{{ error }}</p>
    <div class="leader-heading"
      ><button class="outline-button" :disabled="busy" @click="clear">清空负责人选择</button
      ><button class="primary-button" :disabled="busy" @click="save">保存负责人</button></div
    >
  </section>
</template>
<style scoped>
  .leader-panel {
    background: #fff;
    padding: 24px;
    border: 1px solid #d4e2dc;
    border-radius: 12px;
    margin: 24px 0;
  }
  .leader-panel h2 {
    font-size: 20px;
    margin: 0;
  }
  .leader-panel p {
    line-height: 1.7;
    color: #53746b;
    font-size: 14px;
  }
  .leader-heading,
  .leader-search,
  .leader-pagination {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    justify-content: space-between;
  }
  .leader-search {
    margin: 20px 0;
    align-items: end;
  }
  .leader-search label {
    display: grid;
    gap: 8px;
    flex: 1;
    min-width: 180px;
    font-size: 14px;
  }
  .leader-search input {
    box-sizing: border-box;
    width: 100%;
    padding: 12px;
    border: 1px solid #cadbd4;
    border-radius: 8px;
  }
  .leader-options {
    max-height: 310px;
    overflow-y: auto;
  }
  .leader-options label {
    display: flex;
    gap: 10px;
    padding: 12px 0;
    border-bottom: 1px solid #edf2ef;
    font-size: 14px;
  }
  .leader-options small {
    display: block;
    color: #789086;
    margin-top: 6px;
  }
  .leader-options input[type='radio'] {
    width: 16px;
    height: 16px;
    flex: 0 0 16px;
    margin: 0;
    padding: 0;
  }
  .leader-pagination {
    margin: 16px 0;
    font-size: 13px;
  }
  .leader-panel .leader-error {
    color: #a0362c;
  }
  .leader-choice {
    font-weight: 600;
  }
  @media (max-width: 650px) {
    .leader-panel {
      padding: 18px;
    }
    .leader-search {
      display: grid;
    }
    .leader-search label {
      min-width: 0;
    }
    .leader-pagination span {
      width: 100%;
    }
    .leader-heading {
      align-items: start;
    }
    .leader-heading .primary-button {
      width: 100%;
    }
  }
</style>
