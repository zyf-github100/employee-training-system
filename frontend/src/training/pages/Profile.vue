<script setup lang="ts">
  import { computed, reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import {
    checkDemoPassword,
    emailValid,
    passwordError,
    updateDemoPassword
  } from '../services/demo-auth'
  const s = useTraining(),
    router = useRouter(),
    active = ref('基本资料'),
    busy = ref(false)
  const snapshot = () => ({
    name: s.displayName,
    email: s.isAdmin ? s.data.settings.email : s.profile.email,
    phone: s.isAdmin ? '' : s.profile.phone || '',
    bio: s.isAdmin ? '' : s.profile.bio || '',
    avatar: s.profile?.avatar || 'teal'
  })
  const form = reactive(snapshot()),
    passwords = reactive({ old: '', next: '', confirm: '' })
  const dirty = computed(() => JSON.stringify(form) !== JSON.stringify(snapshot()))
  const records = computed(() => Object.values(s.myLearning))
  const completion = computed(() =>
    Math.round(
      ([form.name, form.email, form.phone, form.bio].filter((v) => v.trim()).length / 4) * 100
    )
  )
  function save() {
    if (!form.name.trim() || form.name.trim().length > 20)
      return ElMessage.warning('姓名应为 1–20 个字符')
    if (!emailValid(form.email.trim())) return ElMessage.warning('请填写有效的工作邮箱')
    if (form.phone && !/^\+?[\d ()-]{7,20}$/.test(form.phone))
      return ElMessage.warning('联系电话格式不正确')
    if (s.isAdmin)
      Object.assign(s.data.settings, { admin: form.name.trim(), email: form.email.trim() })
    else
      Object.assign(s.profile, {
        name: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        bio: form.bio.trim(),
        avatar: form.avatar
      })
    Object.assign(form, snapshot())
    ElMessage.success('个人资料已保存，当前浏览器内同步生效')
  }
  async function changePassword() {
    if (busy.value) return
    busy.value = true
    try {
      const account = s.currentAccount
      if (!account) throw Error('请先激活当前演示工号')
      if (!(await checkDemoPassword(account, passwords.old))) throw Error('当前密码不正确')
      const invalid = passwordError(passwords.next)
      if (invalid) throw Error(invalid)
      if (passwords.next !== passwords.confirm) throw Error('两次输入的新密码不一致')
      if (passwords.old === passwords.next) throw Error('新密码不能与当前密码相同')
      await updateDemoPassword(account, passwords.next)
      Object.assign(passwords, { old: '', next: '', confirm: '' })
      s.signedIn = false
      await router.push('/login')
      ElMessage.success('演示密码已更新，请重新登录')
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '更新失败')
    } finally {
      busy.value = false
    }
  }
  async function logout() {
    try {
      await ElMessageBox.confirm(
        dirty.value
          ? '尚有未保存资料，退出将放弃这些修改。'
          : '退出当前账号？已保存的学习记录会保留。',
        '退出登录',
        { confirmButtonText: '退出', cancelButtonText: '取消' }
      )
      s.signedIn = false
      router.push('/login')
    } catch {}
  }
</script>
<template>
  <div
    ><PageTitle title="个人中心" description="认识自己，管理你的每一步成长。"
      ><el-button @click="logout"
        ><AppIcon name="switch" :size="16" />退出登录</el-button
      ></PageTitle
    >
    <div class="personal-layout">
      <aside class="panel personal-card"
        ><div class="personal-cover"><span class="eyebrow">THIS IS YOUR SPACE</span></div>
        <span class="avatar personal-avatar" :class="form.avatar">{{
          s.displayName.slice(0, 1)
        }}</span
        ><h2>{{ s.displayName }}</h2
        ><p>{{ s.isAdmin ? '培训管理员' : s.profile.job }}</p
        ><span class="category-label">{{ s.isAdmin ? 'ADMIN' : s.profile.no }}</span>
        <div class="personal-facts"
          ><span
            ><AppIcon name="building" :size="16" />{{
              s.isAdmin ? s.data.settings.company : s.profile.department
            }}</span
          ><span
            ><AppIcon name="user" :size="16" />{{
              s.isAdmin ? '管理员演示账号' : '员工演示账号'
            }}</span
          ></div
        >
        <div v-if="!s.isAdmin" class="profile-completion"
          ><div
            ><b>资料完整度</b><span>{{ completion }}%</span></div
          ><el-progress :percentage="completion" :show-text="false" :stroke-width="5" /><p
            >让团队更好地认识你</p
          ></div
        >
        <div v-if="!s.isAdmin" class="personal-numbers"
          ><span
            ><b>{{ records.length }}</b
            >已报名</span
          ><span
            ><b>{{ records.filter((r) => r.progress === 100).length }}</b
            >已完成</span
          ><span
            ><b>{{ (records.reduce((n, r) => n + r.seconds, 0) / 3600).toFixed(1) }}</b
            >演示学时</span
          ></div
        >
        <router-link v-if="!s.isAdmin" to="/records" class="text-link"
          >查看我的学习记录<AppIcon name="right" :size="14"
        /></router-link>
      </aside>
      <section class="panel personal-editor"
        ><div class="content-tabs"
          ><button
            v-for="v in ['基本资料', '账号安全']"
            :key="v"
            :class="{ active: active === v }"
            @click="active = v"
            >{{ v }}</button
          ></div
        >
        <div v-if="active === '基本资料'" class="personal-form"
          ><div class="editor-intro"
            ><h3>把你的故事补充完整</h3><p>工号、部门与岗位由培训管理员维护。</p></div
          >
          <el-form label-position="top" @submit.prevent="save"
            ><div class="form-two-columns"
              ><el-form-item label="姓名"
                ><el-input v-model="form.name" maxlength="20" show-word-limit /></el-form-item
              ><el-form-item label="工号"
                ><el-input
                  :model-value="s.isAdmin ? 'ADMIN' : s.profile.no"
                  disabled /></el-form-item
              ><el-form-item label="工作邮箱"
                ><el-input v-model="form.email" type="email" maxlength="100" /></el-form-item
              ><el-form-item v-if="!s.isAdmin" label="联系电话"
                ><el-input
                  v-model="form.phone"
                  type="tel"
                  maxlength="20"
                  placeholder="选填，用于工作联系" /></el-form-item
              ><el-form-item v-if="!s.isAdmin" label="所属部门"
                ><el-input :model-value="s.profile.department" disabled /></el-form-item
              ><el-form-item v-if="!s.isAdmin" label="当前岗位"
                ><el-input :model-value="s.profile.job" disabled /></el-form-item
            ></div>
            <el-form-item v-if="!s.isAdmin" label="头像颜色"
              ><div class="avatar-colors"
                ><button
                  v-for="c in ['teal', 'blue', 'violet', 'orange']"
                  :key="c"
                  type="button"
                  class="avatar"
                  :class="[c, { chosen: form.avatar === c }]"
                  :aria-label="'头像颜色 ' + c"
                  :aria-pressed="form.avatar === c"
                  @click="form.avatar = c"
                  >{{ form.name.slice(0, 1) || '我' }}</button
                ></div
              ></el-form-item
            >
            <el-form-item v-if="!s.isAdmin" label="关于我"
              ><el-input
                v-model="form.bio"
                type="textarea"
                :rows="4"
                maxlength="200"
                show-word-limit
                placeholder="分享你的学习兴趣，或最近想完成的小目标…"
            /></el-form-item>
            <div class="profile-save-row"
              ><span :class="{ unsaved: dirty }">{{
                dirty ? '有未保存的修改' : '资料已同步到本地'
              }}</span
              ><el-button :disabled="!dirty" @click="Object.assign(form, snapshot())"
                >取消修改</el-button
              ><el-button native-type="submit" type="primary" :disabled="!dirty"
                >保存资料</el-button
              ></div
            > </el-form
          ><p class="demo-note"
            >仅更新当前浏览器的演示资料。真实邮箱验证、手机号验证及审批将在后端接入。</p
          >
        </div>
        <div v-else class="personal-form"
          ><div class="security-callout"
            ><span class="stat-icon teal"><AppIcon name="lock" :size="25" /></span
            ><div
              ><h3>账号安全，从良好习惯开始</h3
              ><p>这里只演示密码修改流程，请勿使用真实密码。</p></div
            ></div
          ><el-form label-position="top" class="password-form" @submit.prevent="changePassword"
            ><el-form-item label="当前工号"
              ><el-input
                :model-value="s.isAdmin ? 'ADMIN' : s.profile.no"
                autocomplete="username"
                readonly /></el-form-item
            ><el-form-item label="当前密码"
              ><el-input
                v-model="passwords.old"
                type="password"
                show-password
                autocomplete="current-password"
                :disabled="busy" /></el-form-item
            ><el-form-item label="新密码"
              ><el-input
                v-model="passwords.next"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="8–32 位，包含字母和数字"
                maxlength="32"
                :disabled="busy" /></el-form-item
            ><el-form-item label="确认新密码"
              ><el-input
                v-model="passwords.confirm"
                type="password"
                show-password
                autocomplete="new-password"
                maxlength="32"
                :disabled="busy" /></el-form-item
            ><el-button native-type="submit" type="primary" :loading="busy"
              >更新密码并重新登录</el-button
            ></el-form
          ><div class="session-info"
            ><AppIcon name="monitor" :size="22" /><div
              ><b>当前演示会话</b><p>仅在此浏览器生效 · 尚未接入服务端会话管理</p></div
            ><StatusBadge status="使用中" /></div
        ></div>
      </section>
    </div>
  </div>
</template>
