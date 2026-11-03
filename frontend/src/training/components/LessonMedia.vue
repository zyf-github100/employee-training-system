<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useTraining } from '../store'
  const props = defineProps<{
    courseId: number
    chapter: number
    type: 'video' | 'pdf' | 'slides'
    canRecord: boolean
    file?: File
  }>()
  const emit = defineEmits<{ playing: [value: boolean] }>()
  const s = useTraining(),
    input = ref<HTMLInputElement>(),
    player = ref<HTMLVideoElement>(),
    url = ref(''),
    filename = ref(''),
    mediaKey = ref(''),
    failed = ref(''),
    page = ref(0),
    current = ref(0),
    duration = ref(0)
  let lastPersist = 0
  const slides = [
    {
      tag: '01 / LEARNING GOALS',
      title: '从共同目标出发',
      subtitle: '让每一次协作，都有清晰的方向。',
      points: ['确认我们共同解决的问题', '定义可以验证的交付成果', '记录分工与时间节点']
    },
    {
      tag: '02 / ACTIVE LISTENING',
      title: '先理解，再回应',
      subtitle: '倾听不是等待发言，而是理解对方的真实意图。',
      points: ['用开放式问题了解上下文', '复述关键信息，校准理解', '区分事实、推测与感受']
    },
    {
      tag: '03 / CLEAR EXPRESSION',
      title: '让表达更加清晰',
      subtitle: '用事实、影响和建议，组织你的表达。',
      points: ['事实：我观察到了什么', '影响：这对目标意味着什么', '行动：我们下一步可以怎么做']
    },
    {
      tag: '04 / MAKE IT HAPPEN',
      title: '把共识，变成行动',
      subtitle: '一次好的会议，应该有一个明确的下一步。',
      points: ['形成负责人明确的行动清单', '设置可执行的截止时间', '约定反馈方式与检查节点']
    },
    {
      tag: '05 / REFLECTION',
      title: '带走一个小改变',
      subtitle: '选择一个真实场景，在今天试着用起来。',
      points: [
        '写下你遇到的一次协作挑战',
        '挑选一个今天就能实践的方法',
        '在学习笔记中记录结果与反思'
      ]
    }
  ]
  const slide = computed(() => slides[page.value]!)
  const time = (v: number) => Math.floor(v / 60) + ':' + String(Math.floor(v % 60)).padStart(2, '0')
  function persist() {
    const r = s.myLearning[props.courseId],
      video = player.value
    if (props.canRecord && r && video && mediaKey.value && Number.isFinite(video.currentTime))
      r.videoPositions[mediaKey.value] = video.currentTime
  }
  function release() {
    persist()
    emit('playing', false)
    player.value?.pause()
    if (url.value) URL.revokeObjectURL(url.value)
    url.value = ''
    filename.value = ''
    failed.value = ''
    duration.value = 0
    current.value = 0
    page.value = 0
  }
  watch(() => [props.type, props.chapter, props.courseId], release)
  onBeforeUnmount(release)
  async function selectFile(event: Event) {
    const el = event.target as HTMLInputElement,
      file = el.files?.[0]
    el.value = ''
    if (!file) return
    await openFile(file)
  }
  watch(
    () => props.file,
    (file) => {
      if (file) void openFile(file)
    },
    { immediate: true }
  )
  async function openFile(file: File) {
    try {
      if (
        props.type === 'video' &&
        (!/\.(mp4|webm|ogg)$/i.test(file.name) || file.size > 500 * 1024 * 1024)
      )
        throw Error('请选择小于 500 MB 的 MP4 / WebM / Ogg 视频')
      if (props.type === 'pdf') {
        if (!/\.pdf$/i.test(file.name) || file.size > 30 * 1024 * 1024)
          throw Error('请选择小于 30 MB 的 PDF 文件；PPT 请先转换为 PDF')
        if (!(await file.slice(0, 5).text()).startsWith('%PDF-'))
          throw Error('文件内容不是有效的 PDF')
      }
      release()
      filename.value = file.name
      mediaKey.value = props.chapter + ':' + file.name + ':' + file.size + ':' + file.lastModified
      url.value = URL.createObjectURL(file)
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '文件无法打开')
    }
  }
  function loaded() {
    const v = player.value
    if (!v) return
    duration.value = Number.isFinite(v.duration) ? v.duration : 0
    const position = s.myLearning[props.courseId]?.videoPositions[mediaKey.value] || 0
    if (props.canRecord && position > 0 && duration.value > 0) {
      v.currentTime = Math.min(position, Math.max(0, duration.value - 1))
      ElMessage.info('已恢复此文件的上次播放位置')
    }
  }
  function tick() {
    current.value = player.value?.currentTime || 0
    if (Date.now() - lastPersist > 5000) {
      persist()
      lastPersist = Date.now()
    }
  }
</script>
<template>
  <section class="lesson-media">
    <template v-if="type === 'slides'"
      ><div class="media-heading"
        ><span><AppIcon name="monitor" :size="17" />翻页课件</span
        ><span class="muted small">HTML 演示课件 · 非原始 PPT 解析</span></div
      ><article class="slide-stage" :class="'slide-tone-' + page"
        ><span class="eyebrow">{{ slide.tag }}</span
        ><h2>{{ slide.title }}</h2
        ><p>{{ slide.subtitle }}</p
        ><ol
          ><li v-for="(text, i) in slide.points" :key="text"
            ><span>{{ String(i + 1).padStart(2, '0') }}</span
            >{{ text }}</li
          ></ol
        ><small>知行 ZHIXING · 高效协作方法示例</small
        ><span class="slide-watermark" aria-hidden="true">{{
          String(page + 1).padStart(2, '0')
        }}</span></article
      ><div class="slide-controls"
        ><el-button :disabled="page === 0" @click="page--"
          ><AppIcon name="left" :size="14" />上一页课件</el-button
        ><div class="slide-dots"
          ><button
            v-for="(_, i) in slides"
            :key="i"
            :class="{ active: page === i }"
            :aria-label="'课件第 ' + (i + 1) + ' 页'"
            :aria-current="page === i ? 'page' : undefined"
            @click="page = i"
            >{{ i + 1 }}</button
          ></div
        ><el-button :disabled="page === slides.length - 1" @click="page++"
          >下一页课件<AppIcon name="right" :size="14" /></el-button></div
      ><p class="demo-note"
        >这组课件用于演示 PPT 转换后的翻页体验。原始 .ppt/.pptx 上传转换将在后端接入，当前可在「PDF
        讲义」中打开转换后的文件。</p
      ></template
    >
    <template v-else
      ><div class="media-heading"
        ><span
          ><AppIcon :name="type === 'video' ? 'play' : 'book'" :size="17" />{{
            type === 'video' ? '视频学习' : 'PDF 讲义'
          }}</span
        ><el-button v-if="!file" size="small" @click="input?.click()"
          ><AppIcon name="upload" :size="14" />{{
            url ? '更换本地文件' : '选择本地文件'
          }}</el-button
        ></div
      >
      <input
        v-if="!file"
        ref="input"
        type="file"
        class="sr-only-input"
        :aria-label="type === 'video' ? '选择本地视频' : '选择本地 PDF'"
        :accept="type === 'video' ? '.mp4,.webm,.ogg' : '.pdf'"
        @change="selectFile"
      />
      <div v-if="!url" class="resource-empty"
        ><span class="resource-illustration"
          ><AppIcon :name="type === 'video' ? 'play' : 'book'" :size="44" /></span
        ><h3>{{ type === 'video' ? '把学习带入真实场景' : '打开讲义，细读每一个知识点' }}</h3
        ><p>{{
          file
            ? '此资源由管理员保存在当前浏览器。刷新后可自动读取，其他浏览器或端口不共享文件。'
            : type === 'video'
              ? '选择一段本地视频，体验播放、倍速、全屏和断点续播。'
              : '选择本地 PDF，使用浏览器阅读、缩放和下载讲义。'
        }}</p
        ><el-button type="primary" @click="input?.click()">{{
          type === 'video' ? '选择视频开始体验' : '选择 PDF 开始阅读'
        }}</el-button
        ><small>文件仅在本机打开，不会上传服务器</small></div
      >
      <template v-else
        ><video
          v-if="type === 'video'"
          ref="player"
          :src="url"
          controls
          playsinline
          preload="metadata"
          class="course-video"
          @loadedmetadata="loaded"
          @timeupdate="tick"
          @play="emit('playing', true)"
          @pause="
            () => {
              persist()
              emit('playing', false)
            }
          "
          @ended="
            () => {
              persist()
              emit('playing', false)
            }
          "
          @error="
            () => {
              failed = '视频无法播放，请尝试浏览器支持的编码（如 H.264 MP4 / WebM）'
              emit('playing', false)
            }
          "
          ><track kind="captions" />当前浏览器不支持视频播放。</video
        ><iframe v-else :src="url" :title="'PDF 讲义：' + filename" class="pdf-viewer" /><p
          v-if="failed"
          class="form-error"
          role="alert"
          >{{ failed }}</p
        ><div class="media-file-info"
          ><span>{{ filename }}</span
          ><b v-if="type === 'video'">{{ time(current) }} / {{ time(duration) }}</b
          ><a v-else :href="url" :download="filename" class="text-link">下载当前 PDF</a></div
        ></template
      >
      <p class="demo-note"
        >{{
          file
            ? '此资源已保存在当前浏览器，刷新后自动读取。其他浏览器或端口不共享文件。'
            : type === 'video'
              ? '支持记录同一文件的播放位置。刷新后需重新选择同一文件；位置按员工、课程、章节及文件信息隔离。'
              : '若浏览器未显示 PDF，请下载后阅读。浏览器内预览能力以当前浏览器为准。'
        }}
        本地计时与完成标记均为演示，不代表服务端有效学时。</p
      >
    </template>
  </section>
</template>
