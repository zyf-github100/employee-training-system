<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTraining } from '../store'
  import { curriculumError } from '../services/curriculum'
  import {
    fileSize,
    getResourceFile,
    removeResourceFiles,
    resourceMetadata,
    saveResourceFiles
  } from '../api/local-resources'
  import { download } from '../api/repository'
  import type { CourseChapter, CourseResource } from '../types'
  const s = useTraining(),
    route = useRoute(),
    router = useRouter(),
    id = Number(route.params.id)
  const course = computed(() => s.data.courses.find((c) => c.id === id))
  const chapters = ref<CourseChapter[]>(JSON.parse(JSON.stringify(course.value?.chapters || [])))
  const selected = ref(chapters.value[0]?.id || ''),
    busy = ref(false),
    adding = ref(false),
    fileInput = ref<HTMLInputElement>(),
    pending = ref(new Map<string, File>())
  const active = computed(() => chapters.value.find((c) => c.id === selected.value))
  const locked = computed(() => s.curriculumLocked(id)),
    dirty = computed(
      () => JSON.stringify(chapters.value) !== JSON.stringify(course.value?.chapters || [])
    )
  const resources = computed(() => chapters.value.flatMap((c) => c.resources)),
    duration = computed(() => chapters.value.reduce((sum, c) => sum + (c.duration || 0), 0))
  const ready = computed(
    () =>
      chapters.value.filter((c) => c.content.trim() || c.resources.some((r) => r.type !== 'ppt'))
        .length
  )
  function addChapter() {
    if (locked.value) return
    const chapter = {
      id: crypto.randomUUID(),
      title: `第 ${chapters.value.length + 1} 章`,
      duration: 15,
      content: '',
      resources: []
    }
    chapters.value.push(chapter)
    selected.value = chapter.id
  }
  function move(index: number, offset: number) {
    if (locked.value) return
    const next = index + offset
    if (next < 0 || next >= chapters.value.length) return
    ;[chapters.value[index], chapters.value[next]] = [chapters.value[next]!, chapters.value[index]!]
  }
  async function removeChapter(chapter: CourseChapter) {
    if (locked.value || chapters.value.length === 1) return
    try {
      await ElMessageBox.confirm(`删除「${chapter.title}」及其资源关联？保存后生效。`, '删除章节', {
        type: 'warning',
        confirmButtonText: '删除章节',
        cancelButtonText: '取消'
      })
      chapters.value = chapters.value.filter((c) => c.id !== chapter.id)
      chapter.resources.forEach((r) => pending.value.delete(r.id))
      if (selected.value === chapter.id) selected.value = chapters.value[0]!.id
    } catch {}
  }
  async function addFiles(event: Event) {
    const input = event.target as HTMLInputElement,
      files = Array.from(input.files || []),
      target = active.value
    input.value = ''
    if (!target || !files.length) return
    adding.value = true
    try {
      if (target.resources.length + files.length > 10) throw Error('每章最多添加 10 个资源')
      const metas = await Promise.all(files.map(resourceMetadata))
      metas.forEach((meta, i) => {
        pending.value.set(meta.id, files[i]!)
        target.resources.push(meta)
      })
      ElMessage.success(`已添加 ${files.length} 个待保存资源`)
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '资源添加失败')
    } finally {
      adding.value = false
    }
  }
  async function removeResource(meta: CourseResource) {
    try {
      await ElMessageBox.confirm(`移除「${meta.name}」？保存后将清理此本机文件。`, '移除课程资源', {
        type: 'warning',
        confirmButtonText: '移除资源',
        cancelButtonText: '取消'
      })
      if (active.value)
        active.value.resources = active.value.resources.filter((r) => r.id !== meta.id)
      pending.value.delete(meta.id)
    } catch {}
  }
  async function downloadResource(meta: CourseResource) {
    try {
      download(meta.name, pending.value.get(meta.id) || (await getResourceFile(meta)))
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '下载失败')
    }
  }
  async function save(publish = false) {
    if (busy.value || adding.value || !course.value) return
    const invalid = curriculumError(chapters.value, publish || course.value.status === '已发布')
    if (invalid) return ElMessage.warning(invalid)
    if (publish) {
      try {
        await ElMessageBox.confirm(
          '发布后员工可以报名学习，已保存的本机资源在同一浏览器内可直接打开。',
          '发布课程',
          { confirmButtonText: '确认发布', cancelButtonText: '继续编辑' }
        )
      } catch {
        return
      }
    }
    busy.value = true
    try {
      const oldIds = course.value.chapters?.flatMap((c) => c.resources.map((r) => r.id)) || [],
        retained = new Set(resources.value.map((r) => r.id))
      await saveResourceFiles(pending.value)
      s.saveCurriculum(id, chapters.value, publish)
      pending.value.clear()
      ElMessage.success(publish ? '课程已发布' : '章节与资源已保存，员工端同步生效')
      try {
        await removeResourceFiles(oldIds.filter((id) => !retained.has(id)))
      } catch {
        ElMessage.warning('内容已保存，但旧文件未能清理；不影响新内容使用')
      }
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      busy.value = false
    }
  }
  onBeforeRouteLeave(async () => {
    if (busy.value || adding.value) {
      ElMessage.info('请等待资源处理完成')
      return false
    }
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm('章节或资源尚未保存，离开将放弃这些修改。', '有未保存的课程内容', {
        confirmButtonText: '放弃并离开',
        cancelButtonText: '继续编辑',
        type: 'warning'
      })
      return true
    } catch {
      return false
    }
  })
</script>
<template>
  <div v-if="course" class="management-page"
    ><button class="back-link" @click="router.push('/courses')"
      ><AppIcon name="left" :size="15" />返回课程中心</button
    >
    <PageTitle title="章节与资源" :description="course.title"
      ><StatusBadge :status="course.status" /><el-button
        :disabled="busy"
        @click="router.push('/courses?edit=' + id)"
        >课程设置</el-button
      ><el-button :disabled="busy" @click="router.push('/learn/' + id)">预览课程</el-button
      ><el-button type="primary" :loading="busy" :disabled="!dirty || adding" @click="save()"
        >保存内容</el-button
      ><el-button
        v-if="course.status !== '已发布'"
        type="primary"
        plain
        :disabled="busy || adding"
        @click="save(true)"
        >发布课程</el-button
      ></PageTitle
    >
    <section class="management-banner"
      ><span class="stat-icon teal"><AppIcon name="collection" :size="28" /></span
      ><div
        ><span class="eyebrow">BUILD A MEANINGFUL COURSE</span><h2>让好内容，有清晰的学习路径。</h2
        ><p>编排章节、写入知识、连接资源，准备一次真正有收获的学习。</p></div
      ><div class="management-metrics"
        ><span
          ><b>{{ chapters.length }}</b
          >个章节</span
        ><span
          ><b>{{ resources.length }}</b
          >个资源</span
        ><span
          ><b>{{ duration }}</b
          >分钟</span
        ></div
      ></section
    >
    <div class="curriculum-layout"
      ><aside class="panel curriculum-sidebar"
        ><div class="panel-heading"
          ><h3>课程目录</h3
          ><span class="muted small">{{ ready }} / {{ chapters.length }} 已就绪</span></div
        >
        <div class="chapter-tree"
          ><div v-for="(c, i) in chapters" :key="c.id" :class="{ active: selected === c.id }"
            ><button class="chapter-select" @click="selected = c.id"
              ><span>{{ String(i + 1).padStart(2, '0') }}</span
              ><div
                ><b>{{ c.title || '未命名章节' }}</b
                ><small>{{ c.duration || 0 }} 分钟 · {{ c.resources.length }} 个资源</small></div
              ><AppIcon
                v-if="c.content.trim() || c.resources.some((r) => r.type !== 'ppt')"
                name="check"
                :size="14" /></button
            ><div class="chapter-order"
              ><button
                :disabled="locked || i === 0 || busy"
                :aria-label="'上移章节 ' + c.title"
                @click="move(i, -1)"
                >上移</button
              ><button
                :disabled="locked || i === chapters.length - 1 || busy"
                :aria-label="'下移章节 ' + c.title"
                @click="move(i, 1)"
                >下移</button
              ><button
                :disabled="locked || chapters.length === 1 || busy"
                :aria-label="'删除章节 ' + c.title"
                @click="removeChapter(c)"
                >删除</button
              ></div
            ></div
          ></div
        >
        <div class="chapter-add"
          ><el-button
            class="full-width"
            :disabled="locked || chapters.length >= 30 || busy"
            @click="addChapter"
            ><AppIcon name="plus" :size="14" />新增章节</el-button
          ><p v-if="locked" class="demo-note"
            >此课程已有报名。为保留学习记录，章节数量和顺序已锁定；仍可编辑正文、标题及资源。</p
          ><p v-else class="muted small">最多 30 章，支持调整学习顺序</p></div
        > </aside
      ><section v-if="active" class="panel chapter-editor"
        ><div class="panel-heading"
          ><div
            ><span class="eyebrow">CHAPTER EDITOR</span><h3>把知识，整理成容易理解的内容</h3></div
          ><span class="edit-state" :class="{ dirty }">{{
            dirty ? '有未保存的修改' : '全部更改已保存'
          }}</span></div
        >
        <el-form label-position="top" class="chapter-form" :disabled="busy"
          ><div class="form-grid"
            ><el-form-item label="章节标题"
              ><el-input v-model="active.title" maxlength="80" show-word-limit /></el-form-item
            ><el-form-item label="预计学习时长（分钟）"
              ><el-input-number v-model="active.duration" :min="1" :max="240" /></el-form-item></div
          ><el-form-item label="图文正文"
            ><el-input
              v-model="active.content"
              type="textarea"
              :rows="10"
              maxlength="20000"
              show-word-limit
              placeholder="写下本章的学习目标、核心知识、案例和行动练习。支持纯文本分段，不执行 HTML。" /></el-form-item
        ></el-form>
        <div class="chapter-resource-section"
          ><div class="section-heading"
            ><div><h3>章节资源</h3><p>本机保存，员工端直接阅读</p></div
            ><el-button :loading="adding" :disabled="busy" @click="fileInput?.click()"
              ><AppIcon name="upload" :size="15" />添加本地资源</el-button
            ></div
          ><input
            ref="fileInput"
            type="file"
            multiple
            class="sr-only-input"
            aria-label="添加章节资源文件"
            accept=".mp4,.webm,.ogg,.pdf,.ppt,.pptx"
            @change="addFiles"
          />
          <div v-if="active.resources.length" class="managed-resource-list"
            ><article v-for="r in active.resources" :key="r.id"
              ><span class="file-icon" :class="r.type"
                ><AppIcon
                  :name="r.type === 'video' ? 'play' : r.type === 'pdf' ? 'book' : 'monitor'"
                  :size="22" /></span
              ><div
                ><b>{{ r.name }}</b
                ><small
                  >{{ r.type.toUpperCase() }} · {{ fileSize(r.size) }} ·
                  {{
                    pending.has(r.id)
                      ? '待保存'
                      : r.type === 'ppt'
                        ? '附件可下载，预览需转换'
                        : '已保存到本机'
                  }}</small
                ></div
              ><button
                class="icon-button"
                :aria-label="'下载资源 ' + r.name"
                @click="downloadResource(r)"
                ><AppIcon name="download" :size="17" /></button
              ><button
                class="icon-button"
                :disabled="busy"
                :aria-label="'移除资源 ' + r.name"
                @click="removeResource(r)"
                ><AppIcon name="delete" :size="17" /></button></article
          ></div>
          <div v-else class="resource-drop-hint"
            ><AppIcon name="upload" :size="32" /><h4>让知识不止于文字</h4
            ><p>添加视频、PDF 讲义或 PPT 附件，让学习材料更完整。</p></div
          >
          <p class="demo-note"
            >视频每个不超过 100 MB，PDF / PPT 不超过 30 MB，每章最多 10 个文件。文件存入此浏览器的
            IndexedDB，不会上传服务器，也不会跨设备同步。PPT 原件仅下载，转换预览待后端接入。</p
          >
        </div>
      </section></div
    > </div
  ><el-empty v-else description="课程不存在"
    ><el-button @click="router.push('/courses')">返回课程中心</el-button></el-empty
  >
</template>
