<script setup lang="ts">
  import { reactive, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { login } from '../../connected/api'
  import { errorMessage, sessionError } from './session'
  const router = useRouter(),
    route = useRoute()
  const form = reactive({ employeeNo: '', password: '' }),
    busy = ref(false),
    error = ref('')
  async function submit() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    sessionError.value = ''
    try {
      await login(form.employeeNo.trim(), form.password)
      form.password = ''
      const next =
        typeof route.query.next === 'string' &&
        route.query.next.startsWith('/') &&
        !route.query.next.startsWith('//') &&
        !route.query.next.startsWith('/login')
          ? route.query.next
          : '/dashboard'
      await router.replace(next)
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
</script>
<template>
  <div class="login-page account-login"
    ><section class="login-story"
      ><div class="brand"
        ><img src="/brand.svg" alt="知行" /><div
          ><strong>知行 <span>ZHIXING</span></strong
          ><small>企业学习平台</small></div
        ></div
      ><div class="login-story-copy"
        ><span class="eyebrow">YOUR NEXT CHAPTER</span><h1>每一步学习，<br />都通向更好的你。</h1
        ><p>连接知识，记录成长。<br />在这里开启你的下一段学习旅程。</p
        ><div class="account-benefits"
          ><span><AppIcon name="book" />发现适合你的课程</span
          ><span><AppIcon name="chart" />记录每一次进步</span
          ><span><AppIcon name="medal" />收获成长的证明</span></div
        ><div class="login-decor"
          ><div /><div /><div /><span>LEARN<br />GROW<br />TOGETHER</span></div
        ></div
      ><small>© 2026 知行企业学习平台</small></section
    ><section class="login-form"
      ><div class="account-form-wrap"
        ><span class="eyebrow">WELCOME TO ZHIXING</span><h2>欢迎回来</h2
        ><p class="muted">使用企业分配的工号与密码登录。</p
        ><form class="live-login-form" @submit.prevent="submit"
          ><label
            >员工工号<input
              v-model="form.employeeNo"
              autocomplete="username"
              placeholder="请输入工号"
              required
              :disabled="busy" /></label
          ><label
            >登录密码<input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
              :disabled="busy" /></label
          ><p v-if="error || sessionError" class="form-error" role="alert">{{
            error || sessionError
          }}</p
          ><el-button
            native-type="submit"
            type="primary"
            size="large"
            class="full-width"
            :loading="busy"
            >登录并开始学习</el-button
          ></form
        ><p class="muted">账号或密码问题请联系企业培训管理员。</p
        ><p
          ><router-link class="text-link" to="/account-setup"
            >使用激活链接或设置码设置密码</router-link
          ></p
        ><a class="text-link" href="/demo.html">查看本地演示（与正式记录隔离）</a></div
      ></section
    ></div
  >
</template>
