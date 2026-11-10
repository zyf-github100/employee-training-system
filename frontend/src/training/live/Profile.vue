<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { api, clearSession, currentUser } from '../../connected/api'
  import { errorMessage } from './session'
  const form = reactive({ name: '', email: '', phone: '', jobTitle: '', version: 0 }),
    password = reactive({ current: '', next: '', confirm: '' })
  const loading = ref(true),
    error = ref(''),
    notice = ref('')
  async function load() {
    loading.value = true
    error.value = ''
    try {
      const user = await api.me()
      currentUser.value = user
      Object.assign(form, {
        name: user.name,
        email: user.email ?? '',
        phone: user.phone ?? '',
        jobTitle: user.jobTitle ?? '',
        version: user.version
      })
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function save() {
    loading.value = true
    error.value = ''
    notice.value = ''
    try {
      const user = await api.updateProfile(form)
      currentUser.value = user
      form.version = user.version
      notice.value = '个人资料已保存'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function changePassword() {
    if (password.next !== password.confirm) {
      error.value = '两次输入的新密码不一致'
      return
    }
    loading.value = true
    error.value = ''
    try {
      await api.changePassword(password.current, password.next)
      clearSession()
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  onMounted(load)
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">ACCOUNT / PROFILE</span><h1>账号与资料</h1
        ><p
          >{{ currentUser?.employeeNo }} · {{ currentUser?.departmentName || '未分配部门' }}</p
        ></div
      ><button class="outline-button" :disabled="loading" @click="load">重新读取资料</button></div
    ><div v-if="currentUser?.mustChangePassword" class="toast" role="alert"
      >首次登录必须先修改密码，修改成功后才能继续使用培训功能。</div
    ><div v-if="error" class="api-alert" role="alert">{{ error }}</div
    ><div v-if="notice" class="toast" role="status">{{ notice }}</div
    ><div class="profile-grid"
      ><form class="panel" @submit.prevent="save"
        ><h2>基本资料</h2><label>姓名<input v-model="form.name" required maxlength="40" /></label
        ><label>岗位<input v-model="form.jobTitle" maxlength="80" /></label
        ><label>工作邮箱<input v-model="form.email" type="email" /></label
        ><label>手机号<input v-model="form.phone" maxlength="30" /></label
        ><button class="primary-button" :disabled="loading">保存资料</button></form
      ><form class="panel" @submit.prevent="changePassword"
        ><h2>修改密码</h2><p class="muted">修改后需要重新登录。</p
        ><label
          >当前密码<input
            v-model="password.current"
            type="password"
            autocomplete="current-password"
            required /></label
        ><label
          >新密码<input
            v-model="password.next"
            type="password"
            autocomplete="new-password"
            minlength="8"
            required
            placeholder="至少 8 位，含字母和数字" /></label
        ><label
          >确认新密码<input
            v-model="password.confirm"
            type="password"
            autocomplete="new-password"
            required /></label
        ><button class="outline-button" :disabled="loading">修改密码</button></form
      ></div
    ></section
  >
</template>
