<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { api, clearSession, logout } from '../../connected/api'
  import { errorMessage } from './session'
  const route = useRoute(),
    router = useRouter()
  const form = reactive({
    employeeNo: typeof route.query.employeeNo === 'string' ? route.query.employeeNo : '',
    token: typeof route.query.token === 'string' ? route.query.token : '',
    password: '',
    confirmation: ''
  })
  const busy = ref(false),
    error = ref(''),
    done = ref(false)
  onMounted(() => {
    if (route.query.token) void router.replace('/account-setup')
  })
  async function submit() {
    if (busy.value) return
    error.value = ''
    if (form.password !== form.confirmation) {
      error.value = '两次输入的密码不一致'
      return
    }
    busy.value = true
    try {
      await api.completeAccountSetup(form.employeeNo.trim(), form.token.trim(), form.password)
      form.password = ''
      form.confirmation = ''
      form.token = ''
      done.value = true
      clearSession()
      await logout().catch(() => {})
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
</script>
<template>
  <main class="setup-page"
    ><section class="setup-card"
      ><a href="/" class="setup-brand"
        ><img src="/brand.svg" alt="知行" /><span>知行 · 企业学习平台</span></a
      ><span class="eyebrow">ACCOUNT SETUP</span
      ><h1>{{ done ? '密码设置成功' : '激活账号 / 设置新密码' }}</h1
      ><template v-if="done"
        ><p role="status">账号已激活，请使用工号 {{ form.employeeNo }} 和新密码登录。</p
        ><router-link class="setup-submit" to="/login">前往登录</router-link></template
      ><template v-else
        ><p>使用管理员交付的一次性链接或设置码，自行设置登录密码。链接有效期为 24 小时。</p
        ><form @submit.prevent="submit"
          ><label
            >员工工号<input
              v-model="form.employeeNo"
              autocomplete="username"
              required
              maxlength="32"
              :disabled="busy" /></label
          ><label
            >一次性设置码<input
              v-model="form.token"
              type="password"
              autocomplete="off"
              required
              minlength="43"
              maxlength="43"
              :disabled="busy"
              placeholder="链接已自动填入，或粘贴管理员提供的设置码" /></label
          ><label
            >新密码<input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              required
              minlength="8"
              maxlength="72"
              :disabled="busy"
              placeholder="至少 8 位，包含字母和数字" /></label
          ><label
            >再次输入新密码<input
              v-model="form.confirmation"
              type="password"
              autocomplete="new-password"
              required
              minlength="8"
              maxlength="72"
              :disabled="busy" /></label
          ><p v-if="error" class="setup-error" role="alert">{{ error }}</p
          ><button class="setup-submit" :disabled="busy">{{
            busy ? '正在设置…' : '设置密码并激活账号'
          }}</button></form
        ><router-link class="setup-back" to="/login">返回登录</router-link></template
      ></section
    ></main
  >
</template>
<style scoped>
  .setup-page {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 32px 20px;
    background: radial-gradient(ellipse at top right, #d8ece4, transparent 60%), #f4f8f6;
    box-sizing: border-box;
  }
  .setup-card {
    width: 100%;
    max-width: 520px;
    padding: 40px;
    background: #fff;
    border: 1px solid #dce7e1;
    border-radius: 20px;
    box-shadow: 0 24px 70px #174b3c0c;
    box-sizing: border-box;
  }
  .setup-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    text-decoration: none;
    color: #155c56;
    font-weight: 700;
    margin-bottom: 40px;
  }
  .setup-brand img {
    width: 38px;
    height: 38px;
  }
  .setup-card h1 {
    font-size: 26px;
    line-height: 1.4;
    color: #183f3b;
  }
  .setup-card > p {
    color: #657a75;
    font-size: 14px;
    line-height: 1.8;
  }
  .setup-card form {
    display: grid;
    gap: 20px;
    margin: 26px 0;
  }
  .setup-card label {
    display: grid;
    gap: 9px;
    color: #335f57;
    font-size: 14px;
  }
  .setup-card input {
    box-sizing: border-box;
    min-width: 0;
    width: 100%;
    padding: 13px;
    border: 1px solid #c8dad2;
    border-radius: 8px;
    font-size: 14px;
  }
  .setup-card input:focus-visible {
    outline: 3px solid #90c4b4;
    outline-offset: 2px;
  }
  .setup-submit {
    display: block;
    text-align: center;
    background: #0c6863;
    color: white;
    border: 0;
    border-radius: 9px;
    padding: 14px;
    font-weight: 700;
    text-decoration: none;
    cursor: pointer;
    font-size: 15px;
  }
  .setup-submit:disabled {
    opacity: 0.5;
  }
  .setup-error {
    color: #a4322f;
    font-size: 14px;
    line-height: 1.6;
  }
  .setup-back {
    color: #29766a;
    font-size: 14px;
  }
  .eyebrow {
    color: #388b7b;
    font-size: 11px;
    letter-spacing: 2px;
  }
  @media (max-width: 550px) {
    .setup-card {
      padding: 26px 20px;
    }
    .setup-brand {
      margin-bottom: 28px;
    }
    .setup-card h1 {
      font-size: 24px;
    }
  }
</style>
