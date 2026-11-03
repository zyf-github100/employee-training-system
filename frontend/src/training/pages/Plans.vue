<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useTraining } from '../store'
  import { departments } from '../api/seed'
  import { exportCsv } from '../api/repository'
  import type { Plan } from '../types'
  const s = useTraining(),
    route = useRoute(),
    router = useRouter(),
    keyword = ref(''),
    status = ref('全部计划'),
    editor = ref(false),
    detail = ref<Plan | null>(null),
    editId = ref<number | null>(null)
  const form = reactive({
    title: '',
    department: '全体部门',
    start: '2026-09-14',
    end: '2026-09-30',
    courseIds: [] as number[],
    description: ''
  })
  const accessible = computed(() => s.accessiblePlans)
  const filtered = computed(() =>
    accessible.value.filter(
      (p) =>
        (status.value === '全部计划' || p.status === status.value) &&
        p.title.includes(keyword.value.trim())
    )
  )
  const metrics = computed(() => [
    { name: '全部培训计划', value: accessible.value.length, icon: 'calendar', tone: 'blue' },
    {
      name: '正在进行',
      value: accessible.value.filter((p) => p.status === '进行中').length,
      icon: 'play',
      tone: 'teal'
    },
    {
      name: '已完成计划',
      value: accessible.value.filter((p) => p.status === '已完成').length,
      icon: 'finished',
      tone: 'violet'
    },
    {
      name: '即将开始',
      value: accessible.value.filter((p) => p.status === '未开始').length,
      icon: 'clock',
      tone: 'orange'
    }
  ])
  function progress(p: Plan) {
    return s.isAdmin
      ? Math.round((p.completed / Math.max(1, p.participants)) * 100)
      : s.planProgress(p)
  }
  function edit(p?: Plan) {
    editId.value = p?.id ?? null
    Object.assign(
      form,
      p
        ? { ...p, courseIds: [...p.courseIds] }
        : {
            title: '',
            department: '全体部门',
            start: '2026-09-14',
            end: '2026-09-30',
            courseIds: [],
            description: ''
          }
    )
    editor.value = true
  }
  function save(draft: boolean) {
    if (!form.title.trim() || !form.start || !form.end) {
      ElMessage.warning('请填写计划名称和起止时间')
      return
    }
    if (form.end < form.start) {
      ElMessage.warning('截止日期不能早于开始日期')
      return
    }
    if (!form.courseIds.length) {
      ElMessage.warning('请至少选择一门课程')
      return
    }
    const participants =
      form.department === '全体部门'
        ? s.data.employees.filter((e) => e.status === '在职').length
        : s.data.employees.filter((e) => e.department === form.department && e.status === '在职')
            .length
    const old = s.data.plans.find((p) => p.id === editId.value)
    const payload = {
      ...form,
      title: form.title.trim(),
      courseIds: [...form.courseIds],
      participants,
      participantIds: old?.participantIds
        ? [...old.participantIds]
        : s.data.employees
            .filter(
              (e) =>
                e.status === '在职' &&
                (form.department === '全体部门' || e.department === form.department)
            )
            .map((e) => e.id),
      status: (draft ? '草稿' : form.start > '2026-09-14' ? '未开始' : '进行中') as Plan['status']
    }
    if (!draft && !payload.participantIds.length) {
      ElMessage.warning('发布计划前请至少添加一名参训员工')
      return
    }
    payload.participants = payload.participantIds.length
    const saved = old || { ...payload, id: Date.now(), completed: 0 }
    if (old) Object.assign(old, payload)
    else s.data.plans.unshift(saved)
    s.assignPlan(saved)
    editor.value = false
    status.value = '全部计划'
    keyword.value = ''
    ElMessage.success(draft ? '计划草稿已保存' : '培训计划已发布')
  }
  function exportPlans() {
    exportCsv(
      '培训计划.csv',
      ['计划名称', '部门', '开始日期', '截止日期', '状态', '参与人数', '完成率'],
      filtered.value.map((p) => [
        p.title,
        p.department,
        p.start,
        p.end,
        p.status,
        p.participants,
        progress(p) + '%'
      ])
    )
  }
  watch(
    () => route.query.create,
    (v) => {
      if (v && s.isAdmin) {
        edit()
        router.replace('/plans')
      }
    },
    { immediate: true }
  )
</script>
<template>
  <div
    ><PageTitle
      :title="s.isAdmin ? '培训计划' : '我的培训'"
      :description="
        s.isAdmin
          ? '从目标到行动，为团队设计清晰的成长路径。'
          : '按培训计划完成课程和测评，进度会持续更新。'
      "
      ><el-button v-if="s.isAdmin" @click="exportPlans"><AppIcon name="download" :size="15" />导出计划</el-button
      ><el-button v-if="s.isAdmin" type="primary" @click="edit()"
        ><AppIcon name="plus" :size="15" />创建培训计划</el-button
      ></PageTitle
    ><div class="summary-grid"
      ><div v-for="m in metrics" :key="m.name" class="summary-card"
        ><div class="stat-icon" :class="m.tone"><AppIcon :name="m.icon" :size="22" /></div
        ><div
          ><span>{{ m.name }}</span
          ><strong>{{ m.value }}<small>个</small></strong></div
        ></div
      ></div
    ><section class="panel"
      ><div class="list-toolbar"
        ><div class="content-tabs"
          ><button
            v-for="v in ['全部计划', '进行中', '未开始', '已完成', '草稿'].filter(
              (v) => s.isAdmin || v !== '草稿'
            )"
            :key="v"
            :class="{ active: status === v }"
            @click="status = v"
            >{{ v }}</button
          ></div
        ><el-input v-model="keyword" placeholder="搜索培训计划" clearable style="width: 240px"
          ><template #prefix><AppIcon name="search" :size="17" /></template></el-input></div
      ><div class="table-scroll"
        ><table class="data-table"
          ><thead
            ><tr
              ><th>培训计划名称</th><th>培训对象</th><th>学习进度</th><th>时间安排</th><th>状态</th
              ><th>操作</th></tr
            ></thead
          ><tbody
            ><tr v-for="(p, i) in filtered" :key="p.id"
              ><td
                ><div class="table-title"
                  ><span class="table-icon" :class="['teal', 'blue', 'violet', 'orange'][i % 4]"
                    ><AppIcon name="calendar" :size="20" /></span
                  ><div
                    ><button class="table-name" @click="detail = p">{{ p.title }}</button
                    ><small
                      >{{ p.courseIds.length }} 门课程 · {{ p.participants }} 名学员</small
                    ></div
                  ></div
                ></td
              ><td>{{ p.department }}</td
              ><td
                ><div class="table-progress"
                  ><el-progress :percentage="progress(p)" :stroke-width="5" /></div
                ><small class="muted">{{
                  s.isAdmin ? `${p.completed} / ${p.participants} 人完成` : '个人课程完成度'
                }}</small></td
              ><td
                ><div>{{ p.start }}</div
                ><small class="muted">至 {{ p.end }}</small></td
              ><td><StatusBadge :status="p.status" /></td
              ><td
                ><div class="inline-actions"
                  ><button class="text-link" @click="detail = p">详情</button
                  ><button
                    v-if="s.isAdmin"
                    class="text-link"
                    @click="router.push('/plans/' + p.id + '/participants')"
                    >参训名单</button
                  ><button v-if="s.isAdmin" class="text-link muted-link" @click="edit(p)"
                    >编辑</button
                  ></div
                ></td
              ></tr
            ></tbody
          ></table
        ></div
      ><el-empty v-if="!filtered.length" description="没有找到匹配的培训计划" /><div
        class="table-footer"
        >共 {{ filtered.length }} 个培训计划<span>计划有节奏，成长有方向</span></div
      ></section
    >
    <el-dialog
      v-model="editor"
      :title="editId ? '编辑培训计划' : '创建培训计划'"
      width="620px"
      class="responsive-dialog"
      ><el-form label-position="top"
        ><el-form-item label="计划名称 *"
          ><el-input
            v-model="form.title"
            placeholder="例如：2026 年第四季度能力提升计划"
            maxlength="80" /></el-form-item
        ><div class="form-grid"
          ><el-form-item label="开始日期 *"
            ><el-date-picker
              v-model="form.start"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期" /></el-form-item
          ><el-form-item label="截止日期 *"
            ><el-date-picker
              v-model="form.end"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期" /></el-form-item></div
        ><el-form-item label="培训对象"
          ><el-select v-model="form.department"
            ><el-option
              v-for="d in ['全体部门', ...departments]"
              :key="d"
              :value="d"
              :label="d" /></el-select></el-form-item
        ><p class="demo-note"
          >新计划按部门生成在职员工名单。已有精确名单不会因更改部门而覆盖，请在「参训名单」中调整。发布时自动分配关联课程。</p
        ><el-form-item label="关联课程 *"
          ><el-select v-model="form.courseIds" multiple filterable placeholder="选择已发布的课程"
            ><el-option
              v-for="c in s.published"
              :key="c.id"
              :value="c.id"
              :label="c.title" /></el-select></el-form-item
        ><el-form-item label="计划介绍"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="说明学习目标与安排"
            maxlength="1000" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editor = false">取消</el-button
        ><el-button @click="save(true)">保存草稿</el-button
        ><el-button type="primary" @click="save(false)">发布计划</el-button></template
      ></el-dialog
    >
    <el-drawer :model-value="!!detail" title="培训计划详情" size="480px" @close="detail = null"
      ><template v-if="detail"
        ><StatusBadge :status="detail.status" /><h2 class="drawer-title">{{ detail.title }}</h2
        ><p class="reading-copy muted">{{ detail.description }}</p
        ><div class="detail-pairs"
          ><span
            >培训对象<b>{{ detail.department }}</b></span
          ><span
            >参与学员<b>{{ detail.participants }} 人</b></span
          ><span
            >起止日期<b>{{ detail.start }} 至 {{ detail.end }}</b></span
          ><span
            >完成进度<b>{{ progress(detail) }}%</b></span
          ></div
        ><h3>学习路径</h3
        ><div class="path-list"
          ><router-link v-for="(id, i) in detail.courseIds" :key="id" :to="'/learn/' + id"
            ><span>{{ String(i + 1).padStart(2, '0') }}</span
            ><div
              ><b>{{ s.data.courses.find((c) => c.id === id)?.title || '课程不可用' }}</b
              ><small>{{ s.data.courses.find((c) => c.id === id)?.duration }} 分钟</small></div
            ><AppIcon name="right" :size="15" /></router-link></div></template
    ></el-drawer>
  </div>
</template>
