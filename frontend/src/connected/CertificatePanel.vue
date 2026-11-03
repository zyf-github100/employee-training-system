<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import QrcodeVue from 'qrcode.vue'
  import { api, type Certificate } from './api'
  import { errorMessage } from '../training/live/session'

  const props = defineProps<{ admin: boolean }>()
  const certificates = ref<Certificate[]>([])
  const loading = ref(false)
  const error = ref('')
  const keyword = ref(''),
    status = ref(''),
    page = ref(1),
    total = ref(0),
    working = ref(false)
  const verifyTarget = ref<Certificate | null>(null),
    revokeTarget = ref<Certificate | null>(null),
    reason = ref('')
  let request = 0
  const verifyUrl = (certificate: Certificate) =>
    certificate.verificationUrl ||
    `${window.location.origin}/#/verify-certificate?code=${certificate.verificationCode}`
  const date = (value?: string) => (value ? new Date(value).toLocaleDateString('zh-CN') : '—')
  async function load() {
    const ticket = ++request
    loading.value = true
    error.value = ''
    try {
      const result = await api.certificatePage(
        props.admin,
        page.value,
        keyword.value.trim(),
        status.value
      )
      if (ticket === request) {
        const lastPage = Math.max(1, Math.ceil(result.total / 20))
        if (page.value > lastPage) {
          page.value = lastPage
          await load()
          return
        }
        certificates.value = result.list
        total.value = result.total
      }
    } catch (e) {
      if (ticket === request) error.value = errorMessage(e)
    } finally {
      if (ticket === request) loading.value = false
    }
  }
  function search() {
    page.value = 1
    void load()
  }
  function changePage(delta: number) {
    page.value += delta
    void load()
  }
  async function download(certificate: Certificate) {
    if (working.value) return
    working.value = true
    try {
      const url = URL.createObjectURL(await api.certificatePdf(props.admin, certificate.id))
      const link = document.createElement('a')
      link.href = url
      link.download = `certificate-${certificate.certificateNo}.pdf`
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 1500)
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      working.value = false
    }
  }
  async function copyLink(certificate: Certificate) {
    try {
      await navigator.clipboard.writeText(verifyUrl(certificate))
      ElMessage.success('验真链接已复制，持有此链接的人可核验证书的基本信息与状态')
    } catch {
      verifyTarget.value = certificate
      ElMessage.info('可在弹窗中手动复制验真链接')
    }
  }
  function openRevoke(certificate: Certificate) {
    revokeTarget.value = certificate
    reason.value = ''
  }
  async function revoke() {
    if (!revokeTarget.value || working.value) return
    working.value = true
    try {
      await api.revokeCertificate(
        revokeTarget.value.id,
        reason.value.trim(),
        revokeTarget.value.version
      )
      revokeTarget.value = null
      await load()
      ElMessage.success('证书已撤销，验真状态已更新')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      working.value = false
    }
  }
  onMounted(load)
</script>

<template>
  <section class="page-section certificate-panel"
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">CERTIFICATE / ACHIEVEMENT</span
        ><h1>{{ admin ? '结业证书' : '我的证书' }}</h1
        ><p>{{
          admin
            ? '按授权范围查询签发记录，下载正式 PDF、验真和撤销证书。'
            : '培训结业后自动签发，下载带验真二维码的正式证书。'
        }}</p></div
      ><button class="outline-button" :disabled="loading || working" @click="load"
        >刷新证书</button
      ></div
    >
    <form class="toolbar admin-toolbar certificate-toolbar" @submit.prevent="search"
      ><input
        v-model="keyword"
        aria-label="搜索证书"
        maxlength="80"
        placeholder="搜索证书编号、姓名、工号或培训名称"
      /><select v-model="status" aria-label="证书状态" @change="search"
        ><option value="">全部状态</option
        ><option value="VALID">有效</option
        ><option value="REVOKED">已撤销</option></select
      ><button class="outline-button" :disabled="loading || working">查询</button
      ><span>共 {{ total }} 张</span></form
    >
    <p class="certificate-guidance"
      >证书信息在签发时固化。已下载的文件不会自动更新，请通过二维码或验真链接核对最新状态。</p
    >
    <div v-if="error" class="api-alert"
      ><b>证书接口</b><span>{{ error }}</span
      ><button class="text-button" @click="error = ''">关闭</button></div
    ><div v-if="loading" class="empty-state">正在读取证书…</div
    ><div v-else-if="!certificates.length" class="panel empty-state">暂无符合条件的证书</div
    ><div v-else class="certificate-list"
      ><article v-for="certificate in certificates" :key="certificate.id" class="certificate-card"
        ><div class="certificate-seal">知</div
        ><div class="certificate-content"
          ><span class="eyebrow">{{ certificate.certificateNo }}</span
          ><h3>{{ certificate.recipientName }}的结业证书</h3><p>{{ certificate.planTitle }}</p
          ><small
            >工号 {{ certificate.employeeNo }} · {{ certificate.issuerName }} · 签发于
            {{ date(certificate.issuedAt) }}</small
          ><div v-if="certificate.revokedAt" class="certificate-revocation"
            ><b>已于 {{ date(certificate.revokedAt) }} 撤销</b
            ><p>{{ certificate.revocationReason || '历史记录未填写撤销原因' }}</p
            ><small v-if="admin && certificate.revokedByName"
              >操作人：{{ certificate.revokedByName }}</small
            ></div
          ></div
        ><span
          class="status-pill"
          :class="{ 'revoked-status': certificate.status === 'REVOKED' }"
          >{{ certificate.status === 'VALID' ? '有效' : '已撤销' }}</span
        ><div class="certificate-actions"
          ><button class="text-button" :disabled="working" @click="download(certificate)">{{
            certificate.status === 'REVOKED' ? '下载撤销版 PDF' : '下载 PDF'
          }}</button
          ><button class="text-button" @click="verifyTarget = certificate">验真</button
          ><button
            v-if="admin && certificate.status === 'VALID'"
            class="text-button danger"
            :disabled="working"
            @click="openRevoke(certificate)"
            >撤销证书</button
          ></div
        ></article
      ></div
    >
    <div v-if="total > 20" class="live-pagination"
      ><button class="outline-button" :disabled="page <= 1 || loading" @click="changePage(-1)"
        >上一页</button
      ><span>{{ page }} / {{ Math.ceil(total / 20) }}</span
      ><button
        class="outline-button"
        :disabled="page * 20 >= total || loading"
        @click="changePage(1)"
        >下一页</button
      ></div
    >
    <el-dialog
      :model-value="!!verifyTarget"
      title="证书验真"
      width="580px"
      class="responsive-dialog"
      @update:model-value="!$event && (verifyTarget = null)"
      ><div v-if="verifyTarget" class="certificate-qr-panel"
        ><QrcodeVue :value="verifyUrl(verifyTarget)" :size="180" level="M" render-as="svg" /><h3>{{
          verifyTarget.certificateNo
        }}</h3
        ><p>验真链接可查看获得人、培训计划、签发单位和最新状态，请按需分享。</p
        ><label
          >验真链接<input
            :value="verifyUrl(verifyTarget)"
            readonly
            aria-label="证书验真链接"
            @focus="($event.target as HTMLInputElement).select()" /></label
        ><div class="admin-dialog-form"
          ><div class="admin-form-actions"
            ><button class="outline-button" @click="copyLink(verifyTarget)">复制链接</button
            ><a
              class="primary-button"
              :href="verifyUrl(verifyTarget)"
              target="_blank"
              rel="noopener noreferrer"
              >打开验真页</a
            ></div
          ></div
        ></div
      ></el-dialog
    >
    <el-dialog
      :model-value="!!revokeTarget"
      title="撤销证书"
      width="620px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!working"
      :show-close="!working"
      @update:model-value="!$event && (revokeTarget = null)"
      ><form v-if="revokeTarget" class="admin-dialog-form" @submit.prevent="revoke"
        ><h3>{{ revokeTarget.recipientName }} · {{ revokeTarget.certificateNo }}</h3
        ><p class="muted">{{ revokeTarget.planTitle }}</p
        ><p class="admin-detail-error"
          >确认后证书立即失效，验真页将显示已撤销。撤销记录不能覆盖，自动结业也不会重新签发有效证书。</p
        ><label
          >撤销原因<textarea
            v-model.trim="reason"
            rows="4"
            maxlength="500"
            required
            :disabled="working"
            placeholder="填写撤销原因，员工可在本人证书列表查看"
          /></label
        ><div class="admin-form-actions"
          ><button
            type="button"
            class="outline-button"
            :disabled="working"
            @click="revokeTarget = null"
            >取消</button
          ><button
            class="primary-button certificate-revoke-button"
            :disabled="working || !reason.trim()"
            >{{ working ? '正在撤销…' : '确认撤销证书' }}</button
          ></div
        ></form
      ></el-dialog
    >
  </section>
</template>
