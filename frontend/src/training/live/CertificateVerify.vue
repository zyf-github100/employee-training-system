<script setup lang="ts">
  import { ref, watch } from 'vue'
  import { useRoute } from 'vue-router'
  import { api, type CertificateVerification } from '../../connected/api'
  import { errorMessage } from './session'
  const route = useRoute(),
    input = ref(''),
    loading = ref(false),
    error = ref('')
  const certificate = ref<CertificateVerification | null>(null)
  let request = 0
  function codeFrom(value: string) {
    value = value.trim()
    if (/^[a-f\d]{64}$/i.test(value)) return value
    try {
      const url = new URL(value)
      return new URLSearchParams(url.hash.split('?')[1] ?? url.search).get('code') ?? ''
    } catch {
      return ''
    }
  }
  async function verify() {
    const code = codeFrom(input.value),
      ticket = ++request
    error.value = ''
    certificate.value = null
    if (!/^[a-f\d]{64}$/i.test(code)) {
      if (input.value.trim()) error.value = '请输入原证书上的完整验真链接或 64 位验真码。'
      loading.value = false
      return
    }
    loading.value = true
    try {
      const data = await api.verifyCertificate(code)
      if (ticket === request) certificate.value = data
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
    }
  }
  const date = (value?: string) => (value ? new Date(value).toLocaleDateString('zh-CN') : '—')
  watch(
    () => route.query.code,
    (value) => {
      input.value = typeof value === 'string' ? value : ''
      void verify()
    },
    { immediate: true }
  )
</script>
<template>
  <div class="certificate-verification-page"
    ><header class="verification-header"
      ><router-link class="brand" to="/"
        ><img src="/brand.svg" alt="" /><div
          ><strong>知行 <span>ZHIXING</span></strong
          ><small>企业学习平台</small></div
        ></router-link
      ><router-link to="/">返回培训平台 →</router-link></header
    ><main class="verification-main"
      ><div class="verification-intro"
        ><span class="eyebrow">CERTIFICATE VERIFICATION</span><h1>每一份成长，都有据可查。</h1
        ><p>扫描原证书二维码，核对培训记录和当前有效状态。</p></div
      ><form class="verification-search" @submit.prevent="verify"
        ><label for="verification-code">证书验真链接或验真码</label
        ><div
          ><input
            id="verification-code"
            v-model="input"
            maxlength="2048"
            placeholder="粘贴完整验真链接或 64 位验真码"
            :disabled="loading"
            autocomplete="off"
          /><button :disabled="loading">{{ loading ? '核验中…' : '查询验真' }}</button></div
        ></form
      ><p v-if="loading" class="verification-empty" role="status">正在向签发系统核验证书…</p
      ><div v-else-if="error" class="verification-error" role="alert"
        ><AppIcon name="search" :size="28" /><h2>未能完成核验</h2><p>{{ error }}</p></div
      ><section
        v-else-if="certificate"
        class="verification-result"
        :class="{ revoked: certificate.status === 'REVOKED' }"
        ><div class="verification-status"
          ><span
            ><AppIcon :name="certificate.status === 'VALID' ? 'check' : 'close'" :size="26" /></span
          ><div
            ><h2>{{ certificate.status === 'VALID' ? '证书有效' : '证书已撤销' }}</h2
            ><p>{{
              certificate.status === 'VALID'
                ? '签发系统中存在该证书，当前状态为有效。'
                : '该证书当前无效，请勿作为有效结业证明使用。'
            }}</p></div
          ></div
        ><dl class="verification-details"
          ><div class="wide"
            ><dt>证书编号</dt><dd>{{ certificate.certificateNo }}</dd></div
          ><div
            ><dt>获得人</dt><dd>{{ certificate.recipientName }}</dd></div
          ><div
            ><dt>工号（部分隐藏）</dt><dd>{{ certificate.employeeNoMasked }}</dd></div
          ><div class="wide"
            ><dt>培训计划</dt><dd>{{ certificate.planTitle }}</dd></div
          ><div class="wide"
            ><dt>签发单位</dt><dd>{{ certificate.issuerName }}</dd></div
          ><div
            ><dt>结业日期</dt><dd>{{ date(certificate.completedAt) }}</dd></div
          ><div
            ><dt>签发日期</dt><dd>{{ date(certificate.issuedAt) }}</dd></div
          ><div v-if="certificate.revokedAt" class="wide"
            ><dt>撤销日期</dt><dd>{{ date(certificate.revokedAt) }}</dd></div
          ></dl
        ><p v-if="certificate.legacySnapshot" class="verification-note"
          >此为历史签发记录，姓名、培训名称和签发单位信息于系统升级时固化。</p
        ><footer
          >本次核验时间：{{ new Date(certificate.checkedAt).toLocaleString('zh-CN')
          }}<br />请同时核对 PDF 上的姓名、培训名称、签发单位和证书编号。</footer
        ></section
      ><p v-else class="verification-empty"
        >此页通过原证书携带的验真码读取结果，证书编号本身不能替代验真码。</p
      ></main
    ><footer class="verification-footer">知行企业学习平台 · 记录成长，信任有据</footer></div
  >
</template>
