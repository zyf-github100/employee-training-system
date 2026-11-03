<script setup lang="ts">
  import { reactive } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { removeResourceFiles } from '../api/local-resources'
  const s = useTraining(),
    form = reactive({ ...s.data.settings })
  function save() {
    if (!form.company.trim() || !form.admin.trim()) {
      ElMessage.warning('企业名称和管理员称呼不能为空')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      ElMessage.warning('请输入有效的联系邮箱')
      return
    }
    s.data.settings = { ...form }
    ElMessage.success('平台设置已保存')
  }
  async function reset() {
    try {
      await ElMessageBox.confirm(
        '将清除本地新增及修改的账号、密码、资料、课程及其资源文件、员工、培训名单、题库、试卷、学习记录、笔记、评价、反馈与考试答卷，并恢复初始数据。此操作不可撤销。',
        '恢复演示数据',
        { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '保留当前数据' }
      )
      const resourceIds = s.data.courses.flatMap(
        (c) => c.chapters?.flatMap((ch) => ch.resources.map((r) => r.id)) || []
      )
      s.reset()
      try {
        await removeResourceFiles(resourceIds)
      } catch {
        ElMessage.warning('演示数据已恢复，但旧资源文件清理失败')
      }
      Object.assign(form, s.data.settings)
    } catch {}
  }
</script>
<template>
  <div
    ><PageTitle title="平台设置" description="让学习平台更贴近你的团队。" /><div
      class="settings-layout"
      ><section class="panel settings-panel"
        ><div class="panel-heading"
          ><div><h3>企业与管理员信息</h3><p>这些信息会用于工作空间展示</p></div
          ><AppIcon name="building" :size="23" /></div
        ><el-form label-position="top" class="settings-form"
          ><el-form-item label="企业名称"
            ><el-input v-model="form.company" maxlength="30" /></el-form-item
          ><el-form-item label="管理员称呼"
            ><el-input v-model="form.admin" maxlength="20" /></el-form-item
          ><el-form-item label="培训联系邮箱"
            ><el-input v-model="form.email" maxlength="100" /></el-form-item
          ><div class="settings-divider" /><div class="form-switch"
            ><div><b>显示未读通知提醒</b><p>在导航栏显示新公告的小红点</p></div
            ><el-switch v-model="form.reminder" aria-label="显示未读通知提醒" /></div
          ><div class="settings-divider" /><el-button type="primary" @click="save"
            >保存设置</el-button
          ></el-form
        ></section
      ><aside
        ><section class="panel about-panel"
          ><img src="/brand.svg" alt="知行" /><h2>知行企业学习平台</h2
          ><p>让每一份潜力，都被看见。</p><div class="version-tag">前端演示版 v1.0</div
          ><div class="detail-pairs"
            ><span>前端框架<b>Vue 3 + TypeScript</b></span
            ><span>界面组件<b>Element Plus</b></span
            ><span>数据可视化<b>ECharts</b></span
            ><span>项目基础<b>Art Design Pro · MIT</b></span
            ><span>数据模式<b>浏览器本地存储</b></span></div
          ></section
        ><section class="panel reset-panel"
          ><h3>演示工作区</h3><p>恢复初始示例，重新体验完整的学习与管理流程。</p
          ><el-button @click="reset"
            ><AppIcon name="refresh" :size="15" />恢复演示数据</el-button
          ></section
        ></aside
      ></div
    ></div
  >
</template>
