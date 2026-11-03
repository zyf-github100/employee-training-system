<script setup lang="ts">
  import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
  import { useRouter } from 'vue-router'
  import { useTraining } from '../store'
  import {
    DEMO_PASSWORD,
    emailValid,
    passwordError,
    updateDemoPassword
  } from '../services/demo-auth'
  const s = useTraining(),
    router = useRouter()
  const mode = ref<'login' | 'activate' | 'reset'>('login')
  const busy = ref(false),
    error = ref(''),
    success = ref(''),
    now = ref(Date.now())
  const form = reactive({ no: '', password: '', email: '', code: '', confirm: '', agreed: false })
  const challenge = ref<{
    no: string
    email: string
    mode: string
    code: string
    expires: number
    sent: number
  } | null>(null)
  const title = computed(
    () => ({ login: '欢迎回来', activate: '开启你的成长旅程', reset: '找回学习账号' })[mode.value]
  )
  const cooldown = computed(() =>
    Math.max(0, Math.ceil(((challenge.value?.sent || 0) + 60000 - now.value) / 1000))
  )
  const timer = setInterval(() => (now.value = Date.now()), 1000)
  onBeforeUnmount(() => clearInterval(timer))
  watch(mode, () => {
    error.value = ''
    success.value = ''
    challenge.value = null
    form.password = ''
    form.confirm = ''
    form.code = ''
  })
  function identity() {
    const account = s.accountCandidate(form.no)
    if (!account) throw Error('没有找到该工号，请联系培训管理员')
    const employee = s.data.employees.find((e) => e.id === account.employeeId)
    if (account.employeeId && employee?.status !== '在职') throw Error('账号已停用，请联系管理员')
    const email = account.role === 'admin' ? s.data.settings.email : employee?.email
    if (!emailValid(form.email.trim()) || email?.toLowerCase() !== form.email.trim().toLowerCase())
      throw Error('请输入该工号登记的工作邮箱')
    if (mode.value === 'activate' && account.active)
      throw Error('此工号已激活，请直接登录或找回密码')
    if (mode.value === 'reset' && !account.active) throw Error('此工号尚未激活，请先激活账号')
    return account
  }
  function requestCode() {
    error.value = ''
    try {
      identity()
      if (cooldown.value) return
      const random = crypto.getRandomValues(new Uint32Array(1))[0]!
      challenge.value = {
        no: form.no.trim().toUpperCase(),
        email: form.email.trim().toLowerCase(),
        mode: mode.value,
        code: String(random % 1000000).padStart(6, '0'),
        sent: Date.now(),
        expires: Date.now() + 300000
      }
      now.value = Date.now()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '无法生成演示验证码'
    }
  }
  async function submit() {
    if (busy.value) return
    error.value = ''
    success.value = ''
    if (!form.agreed) {
      error.value = '请确认仅使用演示信息，不填写真实密码'
      return
    }
    busy.value = true
    try {
      if (!form.no.trim() || !form.password) throw Error('请填写工号与密码')
      if (mode.value === 'login') {
        await s.loginWithPassword(form.no, form.password)
        form.password = ''
        await router.push('/dashboard')
      } else {
        const account = identity(),
          c = challenge.value
        if (
          !c ||
          c.no !== form.no.trim().toUpperCase() ||
          c.email !== form.email.trim().toLowerCase() ||
          c.mode !== mode.value ||
          c.expires < Date.now() ||
          c.code !== form.code.trim()
        )
          throw Error('验证码不匹配或已失效，请重新生成演示码')
        const invalid = passwordError(form.password)
        if (invalid) throw Error(invalid)
        if (form.password !== form.confirm) throw Error('两次输入的密码不一致')
        const updated = { ...account }
        await updateDemoPassword(updated, form.password)
        updated.active = true
        if (mode.value === 'activate' && updated.employeeId) {
          s.data.learning[updated.employeeId] ??= {}
          const employee = s.data.employees.find((e) => e.id === updated.employeeId)
          if (employee && !Object.keys(s.data.learning[updated.employeeId]!).length)
            Object.assign(employee, { hours: 0, progress: 0 })
        }
        const i = s.data.accounts.findIndex((a) => a.no === updated.no)
        if (i < 0) s.data.accounts.push(updated)
        else s.data.accounts[i] = updated
        success.value =
          mode.value === 'activate'
            ? '账号已在本浏览器激活，可以用新密码登录。'
            : '本地演示密码已更新，请使用新密码登录。'
        challenge.value = null
        form.password = ''
        form.confirm = ''
        form.code = ''
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '操作失败，请重试'
    } finally {
      busy.value = false
    }
  }
  function quick(role: 'admin' | 'employee') {
    s.login(role)
    router.push('/dashboard')
  }
  function useExample() {
    Object.assign(
      form,
      mode.value === 'login'
        ? { no: 'E20260001', password: DEMO_PASSWORD }
        : {
            no: 'E20260002',
            email: s.data.employees.find((e) => e.id === 2)?.email || 'employee2@zhixing.example'
          }
    )
  }
</script>
<template>
  <div class="login-page account-login">
    <section class="login-story">
      <div class="brand"
        ><img src="/brand.svg" alt="知行" /><div
          ><strong>知行 <span>ZHIXING</span></strong
          ><small>企业学习平台</small></div
        ></div
      >
      <div class="login-story-copy"
        ><span class="eyebrow">YOUR NEXT CHAPTER</span><h1>每一步学习，<br />都通向更好的你。</h1
        ><p>在这里连接知识、记录成长。<br />让每一份努力，成为明天的底气。</p>
        <div class="account-benefits"
          ><span><AppIcon name="book" /> 发现适合你的课程</span
          ><span><AppIcon name="chart" /> 记录每一次进步</span
          ><span><AppIcon name="medal" /> 收获成长的证明</span></div
        >
        <div class="login-decor"
          ><div /><div /><div /><span>LEARN<br />GROW<br />TOGETHER</span></div
        > </div
      ><small>© 2026 知行企业学习平台 · 本地前端演示</small>
    </section>
    <section class="login-form"
      ><div class="account-form-wrap">
        <span class="eyebrow">WELCOME TO ZHIXING</span><h2>{{ title }}</h2
        ><p class="muted">从一个小小的开始，积累持续成长的力量。</p>
        <div class="account-tabs" role="tablist" aria-label="账号操作">
          <button
            v-for="t in [
              { key: 'login', name: '工号登录' },
              { key: 'activate', name: '账号激活' },
              { key: 'reset', name: '找回密码' }
            ] as const"
            :key="t.key"
            role="tab"
            :aria-selected="mode === t.key"
            :disabled="busy"
            :class="{ active: mode === t.key }"
            @click="mode = t.key"
            >{{ t.name }}</button
          >
        </div>
        <div v-if="success" class="account-success" role="status"
          ><AppIcon name="success" :size="34" /><h3>操作成功</h3><p>{{ success }}</p
          ><el-button type="primary" @click="mode = 'login'">返回工号登录</el-button></div
        >
        <el-form v-else label-position="top" @submit.prevent="submit">
          <el-form-item label="员工工号"
            ><el-input
              v-model="form.no"
              placeholder="请输入工号，例如 E20260001"
              autocomplete="username"
              maxlength="30"
              :disabled="busy"
          /></el-form-item>
          <template v-if="mode !== 'login'">
            <el-form-item label="登记邮箱"
              ><el-input
                v-model="form.email"
                type="email"
                placeholder="请输入员工名单中登记的邮箱"
                autocomplete="email"
                :disabled="busy"
            /></el-form-item>
            <el-form-item label="演示验证码"
              ><div class="verification-row"
                ><el-input
                  v-model="form.code"
                  placeholder="6 位演示码"
                  maxlength="6"
                  inputmode="numeric"
                  :disabled="busy"
                /><el-button :disabled="busy || cooldown > 0" @click="requestCode">{{
                  cooldown ? cooldown + ' 秒后重取' : '生成演示码'
                }}</el-button></div
              ></el-form-item
            >
            <div v-if="challenge" class="challenge-note" role="status"
              >本地演示码 <strong>{{ challenge.code }}</strong
              ><span>5 分钟内有效。未发送短信或邮件。</span></div
            >
          </template>
          <el-form-item :label="mode === 'login' ? '登录密码' : '设置新密码'"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              :placeholder="mode === 'login' ? '请输入演示密码' : '8–32 位，包含字母和数字'"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              maxlength="32"
              :disabled="busy"
          /></el-form-item>
          <el-form-item v-if="mode !== 'login'" label="确认新密码"
            ><el-input
              v-model="form.confirm"
              type="password"
              show-password
              placeholder="再次输入新密码"
              autocomplete="new-password"
              maxlength="32"
              :disabled="busy"
          /></el-form-item>
          <el-checkbox v-model="form.agreed" class="demo-agreement"
            >我知晓这是本地演示，不填写真实密码</el-checkbox
          >
          <p v-if="error" class="form-error" role="alert"
            ><AppIcon name="warning" :size="15" />{{ error }}</p
          >
          <el-button
            native-type="submit"
            type="primary"
            size="large"
            class="full-width account-submit"
            :loading="busy"
            >{{
              mode === 'login' ? '登录学习平台' : mode === 'activate' ? '激活账号' : '重置演示密码'
            }}<AppIcon name="right" :size="16"
          /></el-button>
        </el-form>
        <div class="demo-account-hint"
          ><div
            ><AppIcon name="info" :size="15" /><b>演示账号说明</b
            ><button class="text-link" :disabled="busy" @click="useExample">填入示例</button></div
          ><p
            >登录：ADMIN / E20260001，初始密码 {{ DEMO_PASSWORD }}。<br />激活：E20260002，邮箱
            employee2@zhixing.example。</p
          ></div
        >
        <div class="quick-demo"
          ><span>只想先看看？</span
          ><button :disabled="busy" @click="quick('admin')">管理端免登录体验</button
          ><button :disabled="busy" @click="quick('employee')">员工端免登录体验</button></div
        >
        <p class="login-demo"
          ><AppIcon name="lock" :size="15" />本地账号与验证码仅演示交互，不是真实身份认证。</p
        >
      </div></section
    >
  </div>
</template>
