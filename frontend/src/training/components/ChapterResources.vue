<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue'
  import { ElMessage } from 'element-plus'
  import { getResourceFile, fileSize } from '../api/local-resources'
  import { download } from '../api/repository'
  import type { CourseResource } from '../types'
  const props = defineProps<{
    resources: CourseResource[]
    courseId: number
    chapter: number
    canRecord: boolean
  }>()
  const emit = defineEmits<{ activity: [value: boolean] }>()
  const selected = ref(''),
    file = ref<File>(),
    busy = ref(false),
    error = ref('')
  let sequence = 0
  const current = computed(() => props.resources.find((r) => r.id === selected.value))
  watch(
    () => props.resources,
    (resources) => {
      selected.value = resources[0]?.id || ''
    },
    { immediate: true }
  )
  async function load() {
    const request = ++sequence,
      meta = current.value
    emit('activity', false)
    file.value = undefined
    error.value = ''
    if (!meta) return
    busy.value = true
    try {
      const result = await getResourceFile(meta)
      if (request !== sequence) return
      file.value = result
      emit('activity', meta.type === 'pdf')
    } catch (e) {
      if (request === sequence) error.value = e instanceof Error ? e.message : '资源读取失败'
    } finally {
      if (request === sequence) busy.value = false
    }
  }
  watch(selected, load, { immediate: true })
  onBeforeUnmount(() => {
    sequence++
    emit('activity', false)
  })
  function downloadCurrent() {
    if (file.value && current.value) download(current.value.name, file.value)
    else ElMessage.warning('请先成功读取文件')
  }
</script>
<template>
  <div class="chapter-reader"
    ><div class="resource-pills"
      ><button
        v-for="r in resources"
        :key="r.id"
        :class="{ active: selected === r.id }"
        @click="selected = r.id"
        ><AppIcon
          :name="r.type === 'video' ? 'play' : r.type === 'pdf' ? 'book' : 'monitor'"
          :size="16"
        /><span>{{ r.name }}</span
        ><small>{{ r.type.toUpperCase() }}</small></button
      ></div
    >
    <div v-if="busy" class="reader-message"
      ><AppIcon name="refresh" :size="25" /><p>正在读取本机资源…</p></div
    >
    <div v-else-if="error" class="reader-message"
      ><AppIcon name="warning" :size="25" /><p>{{ error }}</p
      ><el-button @click="load">重新读取</el-button></div
    >
    <template v-else-if="file && current"
      ><div v-if="current.type === 'ppt'" class="reader-message"
        ><span class="file-icon ppt"><AppIcon name="monitor" :size="28" /></span
        ><h3>{{ current.name }}</h3
        ><p>{{ fileSize(current.size) }} · 原始 PPT 附件，浏览器端不做转换。</p
        ><el-button type="primary" @click="downloadCurrent">下载 PPT 原件</el-button
        ><p class="demo-note">如需在线阅读，请管理员添加转换后的 PDF。</p></div
      ><LessonMedia
        v-else
        :key="current.id"
        :course-id="courseId"
        :chapter="chapter"
        :type="current.type"
        :can-record="canRecord"
        :file="file"
        @playing="emit('activity', current?.type === 'pdf' || $event)"
    /></template>
  </div>
</template>
