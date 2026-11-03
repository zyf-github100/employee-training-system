<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ApiError, api, type Certificate } from './api'

  const props = defineProps<{ admin: boolean }>()
  const certificates = ref<Certificate[]>([])
  const loading = ref(false)
  const error = ref('')
  const printTarget = ref<Certificate | null>(null)
  async function load() {
    loading.value = true
    error.value = ''
    try {
      certificates.value = await api.certificates(props.admin)
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : '证书接口暂时不可用，请确认后端已启动'
    } finally {
      loading.value = false
    }
  }
  function printCertificate(certificate: Certificate) {
    printTarget.value = certificate
    window.setTimeout(() => window.print(), 50)
  }
  function downloadCertificate(certificate: Certificate) {
    const text = `知行企业培训系统\n${certificate.title}\n培训计划：${certificate.planTitle}\n证书编号：${certificate.certificateNo}\n获得人：${certificate.recipientName}（${certificate.employeeNo}）\n签发日期：${new Date(certificate.issuedAt).toLocaleDateString('zh-CN')}\n状态：${certificate.status === 'VALID' ? '有效' : '已撤销'}\n`
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${certificate.certificateNo}.txt`
    link.click()
    URL.revokeObjectURL(url)
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
            ? '查看当前数据范围内员工由培训计划自动签发的结业证书。'
            : '培训计划的必修项目全部完成后，系统会自动生成结业证书。'
        }}</p></div
      ><button class="outline-button" :disabled="loading" @click="load">刷新数据</button></div
    >
    <div v-if="error" class="api-alert"
      ><b>证书接口</b><span>{{ error }}</span
      ><button class="text-button" @click="error = ''">关闭</button></div
    ><div v-if="loading" class="empty-state">正在读取证书…</div
    ><div v-else-if="!certificates.length" class="empty-state">暂无已签发证书</div
    ><div v-else class="certificate-list"
      ><article v-for="certificate in certificates" :key="certificate.id" class="certificate-card"
        ><div class="certificate-seal">知</div
        ><div class="certificate-content"
          ><span class="eyebrow">{{ certificate.certificateNo }}</span
          ><h3>{{ certificate.title }}</h3
          ><p>{{ certificate.planTitle }}</p
          ><small
            >{{ admin ? `${certificate.recipientName} · ${certificate.employeeNo} · ` : '' }}签发于
            {{ new Date(certificate.issuedAt).toLocaleDateString('zh-CN') }}</small
          ></div
        ><span class="status-pill">{{ certificate.status === 'VALID' ? '有效' : '已撤销' }}</span
        ><div class="certificate-actions"
          ><button class="text-button" @click="printCertificate(certificate)">打印</button
          ><button class="text-button" @click="downloadCertificate(certificate)"
            >下载文本记录</button
          ></div
        ></article
      ></div
    >
    <div v-if="printTarget" class="print-certificate"
      ><div class="certificate-seal">知</div
      ><span class="eyebrow">ZHIXING TRAINING CERTIFICATE</span><h2>{{ printTarget.title }}</h2
      ><p
        >兹证明 <strong>{{ printTarget.recipientName }}</strong
        >（{{ printTarget.employeeNo }}）已完成培训计划“{{ printTarget.planTitle }}”。</p
      ><p>证书编号：{{ printTarget.certificateNo }}</p
      ><p>签发日期：{{ new Date(printTarget.issuedAt).toLocaleDateString('zh-CN') }}</p></div
    >
  </section>
</template>
