<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { api } from '../../../connected/api'
  import { errorMessage } from '../session'
  import { platformSettings } from '../platform'
  const form = reactive({
    companyName: '',
    contactName: '',
    contactEmail: '',
    unreadReminders: true,
    version: 0
  })
  const loading = ref(true),
    saving = ref(false),
    allowed = ref(false),
    ready = ref(false),
    error = ref(''),
    notice = ref('')
  async function load() {
    loading.value = true
    error.value = ''
    notice.value = ''
    try {
      const [settings, access] = await Promise.all([api.settings(), api.access()])
      Object.assign(form, settings)
      platformSettings.value = settings
      allowed.value = access.superAdmin
      ready.value = true
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function save() {
    if (saving.value || !allowed.value) return
    saving.value = true
    error.value = ''
    notice.value = ''
    try {
      const settings = await api.saveSettings(form)
      Object.assign(form, settings)
      platformSettings.value = settings
      notice.value = '设置已保存，其他已登录用户在重新进入页面或刷新后可看到更新。'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      saving.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">PLATFORM SETTINGS</span><h1>平台设置</h1
        ><p>维护企业展示信息与培训联系方式。</p></div
      ><button class="outline-button" :disabled="loading || saving" @click="load"
        >重新读取设置</button
      ></div
    ><div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button :disabled="saving" @click="load">重新读取</button></div
    ><p v-if="notice" class="toast" role="status">{{ notice }}</p
    ><div v-if="loading" class="empty-state">正在读取平台设置…</div
    ><div v-else-if="ready" class="admin-overview-grid"
      ><form class="panel" @submit.prevent="save"
        ><div class="panel-heading"><h2>企业与培训联系人</h2><AppIcon name="building" /></div
        ><p v-if="!allowed" class="admin-info-note">当前账号可查看设置，修改由超级管理员维护。</p
        ><fieldset class="service-fieldset" :disabled="!allowed || saving"
          ><label>企业名称<input v-model.trim="form.companyName" required maxlength="80" /></label
          ><label>培训联系人<input v-model.trim="form.contactName" required maxlength="40" /></label
          ><label
            >培训联系邮箱<input
              v-model.trim="form.contactEmail"
              type="email"
              maxlength="100"
              placeholder="可选，例如 training@example.com" /></label
          ><label class="option-line"
            ><input v-model="form.unreadReminders" type="checkbox" />显示公告未读提醒</label
          ><p class="muted">关闭提醒后，员工仍可在“通知公告”查看收到的公告。</p></fieldset
        ><button v-if="allowed" class="primary-button" :disabled="saving">{{
          saving ? '保存中…' : '保存设置'
        }}</button></form
      ><aside class="panel service-settings-note"
        ><AppIcon name="settings" :size="32" /><h2>团队共同的学习空间</h2
        ><p
          >企业名称显示在侧栏工作区，培训联系人和邮箱显示在页面底部。修改联系人不会改变登录账号姓名或权限。</p
        ><p>公告按发布时选择的员工名单投递，未读状态由每位员工独立保存。</p></aside
      ></div
    ></section
  >
</template>
