<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useTraining } from '../store'
  const s = useTraining(),
    route = useRoute(),
    router = useRouter(),
    chapter = ref(0),
    tab = ref('课程内容'),
    note = ref(''),
    resource = ref<'text' | 'video' | 'pdf' | 'slides' | 'managed'>('text'),
    playing = ref(false)
  const rating = ref(5),
    reviewText = ref(''),
    submitting = ref(false)
  const id = Number(route.params.id)
  const course = computed(() =>
    s.data.courses.find((c) => c.id === id && (s.isAdmin || c.status === '已发布'))
  )
  const record = computed(() => s.myLearning[id]),
    enrolled = computed(() => !!record.value),
    completed = computed(() => record.value?.completedLessons || [])
  const ownReview = computed(() =>
    s.data.reviews.find((r) => r.courseId === id && r.employeeId === s.employeeId)
  )
  const reviews = computed(() => s.data.reviews.filter((r) => r.courseId === id))
  const avgRating = computed(() =>
    reviews.value.length
      ? (reviews.value.reduce((n, r) => n + r.rating, 0) / reviews.value.length).toFixed(1)
      : course.value?.rating || '暂无'
  )
  const noteDirty = computed(() => note.value !== (record.value?.note || ''))
  const titles = [
    '学习导览与目标',
    '理解核心概念',
    '建立有效的方法',
    '实践中的常见误区',
    '积极倾听与反馈',
    '工作场景案例分析',
    '行动计划与自我练习',
    '课程回顾与总结'
  ]
  const currentChapter = computed(() => course.value?.chapters?.[chapter.value])
  const currentTitle = computed(
    () => currentChapter.value?.title || titles[chapter.value % titles.length]
  )
  watch(
    () => record.value?.courseId,
    () => {
      chapter.value = Math.min(record.value?.lastChapter || 0, (course.value?.lessons || 1) - 1)
      note.value = record.value?.note || ''
    },
    { immediate: true }
  )
  watch(
    ownReview,
    (r) => {
      rating.value = r?.rating || 5
      reviewText.value = r?.content || ''
    },
    { immediate: true }
  )
  watch(chapter, (value) => {
    playing.value = false
    if (resource.value === 'managed' && !currentChapter.value?.resources.length)
      resource.value = 'text'
    if (record.value && !s.isAdmin) record.value.lastChapter = value
  })
  watch(resource, () => (playing.value = false))
  const timer = setInterval(() => {
    if (
      !document.hidden &&
      document.hasFocus() &&
      enrolled.value &&
      !s.isAdmin &&
      tab.value === '课程内容' &&
      (!['video', 'managed'].includes(resource.value) || playing.value)
    )
      s.recordStudyTime(id, 5)
  }, 5000)
  onBeforeUnmount(() => clearInterval(timer))
  function enroll() {
    try {
      s.enrollCourse(id)
      ElMessage.success('报名成功，课程已加入学习记录')
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '报名失败')
    }
  }
  function saveNote() {
    if (!record.value || s.isAdmin) return
    record.value.note = note.value
    ElMessage.success('学习笔记已保存')
  }
  function finish() {
    try {
      s.completeLesson(id, chapter.value)
      ElMessage.success(
        record.value?.progress === 100
          ? '恭喜，你已完成这门课程！现在可以分享学习评价。'
          : '本节学习已记录'
      )
      if (course.value && chapter.value < course.value.lessons - 1) chapter.value++
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '请先报名')
    }
  }
  function review() {
    if (submitting.value) return
    submitting.value = true
    try {
      s.saveReview(id, rating.value, reviewText.value)
      ElMessage.success(ownReview.value ? '课程评价已保存' : '感谢你的分享')
    } catch (e) {
      ElMessage.warning(e instanceof Error ? e.message : '评价失败')
    } finally {
      submitting.value = false
    }
  }
</script>
<template>
  <div v-if="course" class="learning-detail-v2">
    <button class="back-link" @click="router.push('/courses')"
      ><AppIcon name="left" :size="15" />返回课程中心</button
    >
    <PageTitle :title="course.title" :description="course.subtitle"
      ><el-button v-if="s.isAdmin" @click="router.push('/courses/' + id + '/manage')"
        >管理章节与资源</el-button
      ><span class="category-label">{{ course.category }}</span
      ><span class="muted">{{ course.instructor }} · {{ course.lessons }} 章节</span></PageTitle
    >
    <div v-if="s.isAdmin" class="analytics-note"
      ><AppIcon
        name="info"
        :size="16"
      />管理员预览模式，不写入员工学习记录。请切换员工端体验报名和学习。</div
    >
    <div class="learning-layout">
      <section class="panel learning-content">
        <CourseCover :theme="course.theme" />
        <div class="lesson-top"
          ><div
            ><span class="eyebrow">CHAPTER {{ String(chapter + 1).padStart(2, '0') }}</span
            ><h2>{{ currentTitle }}</h2></div
          ><span class="lesson-type">{{
            enrolled && !s.isAdmin ? '已加入学习清单' : '课程预览'
          }}</span></div
        >
        <div class="content-tabs"
          ><button
            v-for="v in ['课程内容', '我的笔记', '课程评价']"
            :key="v"
            :class="{ active: tab === v }"
            @click="tab = v"
            >{{ v
            }}<span v-if="v === '课程评价' && reviews.length" class="inline-count">{{
              reviews.length
            }}</span></button
          ></div
        >
        <div v-if="tab === '课程内容'">
          <div class="resource-tabs" aria-label="资源体验类型"
            ><button
              v-if="currentChapter?.resources.length"
              :class="{ active: resource === 'managed' }"
              @click="resource = 'managed'"
              ><AppIcon name="collection" :size="15" />本章资源
              {{ currentChapter.resources.length }}</button
            ><button
              v-for="r in [
                { id: 'text', name: '图文内容', icon: 'book' },
                { id: 'video', name: '视频学习', icon: 'play' },
                { id: 'pdf', name: 'PDF 讲义', icon: 'ticket' },
                { id: 'slides', name: '翻页课件', icon: 'monitor' }
              ] as const"
              :key="r.id"
              :class="{ active: resource === r.id }"
              :aria-pressed="resource === r.id"
              @click="resource = r.id"
              ><AppIcon :name="r.icon" :size="15" />{{ r.name }}</button
            ></div
          >
          <article v-if="resource === 'text'" class="lesson-article"
            ><h3>{{ currentTitle }}</h3
            ><div v-if="currentChapter?.content" class="managed-prose">{{
              currentChapter.content
            }}</div
            ><div v-else class="reader-message"
              ><p>本章内容通过课程资源提供，请打开下方资源。</p
              ><el-button
                v-if="currentChapter?.resources.length"
                type="primary"
                @click="resource = 'managed'"
                >打开本章资源</el-button
              ></div
            ><div v-if="currentChapter?.resources.length" class="chapter-material-link"
              ><span>{{ currentChapter.resources.length }} 个学习资源已就绪</span
              ><el-button @click="resource = 'managed'"
                >查看本章资源<AppIcon name="right" :size="14" /></el-button></div
            ><p class="demo-note"
              >章节完成与学时为本地演示记录，正式完成条件和有效学时待服务端校验。</p
            ></article
          >
          <ChapterResources
            v-else-if="resource === 'managed'"
            :key="currentChapter?.id"
            :resources="currentChapter?.resources || []"
            :course-id="id"
            :chapter="chapter"
            :can-record="enrolled && !s.isAdmin"
            @activity="playing = $event"
          />
          <LessonMedia
            v-else
            :course-id="id"
            :chapter="chapter"
            :type="resource"
            :can-record="enrolled && !s.isAdmin"
            @playing="playing = $event"
          />
        </div>
        <div v-else-if="tab === '我的笔记'" class="note-editor"
          ><div class="note-heading"
            ><div
              ><h3>把灵感留下，让知识生根</h3
              ><p class="muted small">同一门课的笔记独立保存在当前员工名下。</p></div
            ><span class="saved-status">{{
              noteDirty ? '有未保存的内容' : '已保存到本地'
            }}</span></div
          ><template v-if="enrolled && !s.isAdmin"
            ><el-input
              v-model="note"
              type="textarea"
              :rows="12"
              placeholder="写下你的收获、问题或下一步行动…"
              maxlength="10000"
              show-word-limit
            /><el-button type="primary" :disabled="!noteDirty" @click="saveNote"
              >保存学习笔记</el-button
            ></template
          ><el-empty
            v-else
            :description="s.isAdmin ? '预览模式不编辑学习笔记' : '报名课程后，就可以记录学习笔记'"
            :image-size="90"
            ><el-button v-if="!s.isAdmin" type="primary" @click="enroll"
              >报名并开始学习</el-button
            ></el-empty
          ></div
        >
        <div v-else class="course-reviews"
          ><div class="reviews-summary"
            ><strong>{{ avgRating }}</strong
            ><div
              ><h3>每一种真实收获，都值得分享</h3
              ><p>{{
                reviews.length
                  ? '来自 ' + reviews.length + ' 条本地学习评价'
                  : '还没有本地评价。课程卡片评分为初始示例评分。'
              }}</p></div
            ><AppIcon name="star" :size="27"
          /></div>
          <div v-if="!s.isAdmin && record?.progress === 100" class="review-compose"
            ><h3>{{ ownReview ? '编辑我的评价' : '分享你的课程收获' }}</h3
            ><el-rate
              v-model="rating"
              :max="5"
              :clearable="false"
              aria-label="课程评分"
              show-text
              :texts="['还需改进', '一般', '不错', '很有帮助', '非常推荐']"
            /><el-input
              v-model="reviewText"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="这门课程对你有什么帮助？分享一个具体的收获吧。"
            /><div class="review-submit"
              ><span>每位员工每门课程保留一条评价，可修改。</span
              ><el-button type="primary" :loading="submitting" @click="review">{{
                ownReview ? '保存评价' : '发布评价'
              }}</el-button></div
            ></div
          >
          <div v-else-if="!s.isAdmin" class="review-locked"
            ><AppIcon name="lock" :size="20" /><span>完成全部课程学习后，即可提交评价。</span></div
          >
          <article v-for="r in reviews" :key="r.id" class="review-row"
            ><span
              class="avatar"
              :class="s.data.employees.find((e) => e.id === r.employeeId)?.avatar || 'teal'"
              >{{
                s.data.employees.find((e) => e.id === r.employeeId)?.name.slice(0, 1) || '员'
              }}</span
            ><div
              ><header
                ><b>{{ s.data.employees.find((e) => e.id === r.employeeId)?.name || '员工' }}</b
                ><span>{{ new Date(r.updatedAt).toLocaleDateString('zh-CN') }}</span></header
              ><el-rate :model-value="r.rating" disabled /><p>{{ r.content }}</p></div
            ></article
          ><el-empty v-if="!reviews.length" description="期待第一份学习心得" :image-size="80" />
        </div>
        <div v-if="tab === '课程内容'" class="lesson-footer"
          ><el-button :disabled="chapter === 0" @click="chapter--"
            ><AppIcon name="left" :size="14" />上一节</el-button
          ><el-button v-if="!s.isAdmin && !enrolled" type="primary" @click="enroll"
            >报名并开始学习</el-button
          ><el-button v-else-if="!s.isAdmin" type="primary" @click="finish"
            ><AppIcon name="check" :size="15" />{{
              completed.includes(chapter) ? '已完成 · 继续学习' : '完成本节学习'
            }}</el-button
          ><el-button v-else :disabled="chapter === course.lessons - 1" @click="chapter++"
            >下一节预览</el-button
          ></div
        >
      </section>
      <aside class="panel lesson-outline">
        <div class="enrollment-status" v-if="!enrolled && !s.isAdmin"
          ><span class="stat-icon teal"><AppIcon name="book" :size="24" /></span
          ><h3>下一步成长，从这里开始</h3><p>报名后解锁进度记录、学习笔记与课程评价。</p
          ><el-button type="primary" class="full-width" @click="enroll">报名课程</el-button
          ><small>免费演示课程 · 无需审批</small></div
        >
        <template v-else
          ><h3>{{ s.isAdmin ? '课程信息' : '你的学习进度' }}</h3
          ><div v-if="!s.isAdmin" class="outline-progress"
            ><span
              >已完成 {{ completed.length }} / {{ course.lessons }} 章节<strong
                >{{ record?.progress || 0 }}%</strong
              ></span
            ><el-progress
              :percentage="record?.progress || 0"
              :show-text="false"
              :stroke-width="6" /></div
          ><router-link v-if="!s.isAdmin" to="/records" class="outline-record-link"
            >查看学习记录<AppIcon name="right" :size="13" /></router-link
        ></template>
        <h3 class="outline-section-title">课程目录</h3>
        <button
          v-for="(_, i) in Array.from({ length: course.lessons })"
          :key="i"
          :class="{ active: chapter === i, complete: !s.isAdmin && completed.includes(i) }"
          :aria-current="chapter === i ? 'step' : undefined"
          @click="
            () => {
              chapter = i
              tab = '课程内容'
            }
          "
          ><span class="chapter-number"
            ><AppIcon v-if="!s.isAdmin && completed.includes(i)" name="check" :size="14" /><template
              v-else
              >{{ String(i + 1).padStart(2, '0') }}</template
            ></span
          ><span
            ><b>{{ course.chapters?.[i]?.title || titles[i % titles.length] }}</b
            ><small
              >{{
                course.chapters?.[i]?.duration || Math.round(course.duration / course.lessons)
              }}
              分钟</small
            ></span
          ><AppIcon v-if="chapter === i" name="play" :size="16"
        /></button>
        <div class="outline-tip"
          ><AppIcon name="info" :size="16" /><p
            >本地记录按员工隔离。「本章资源」自动读取管理员保存的文件；临时选择的视频需重选同一文件。</p
          ></div
        >
      </aside>
    </div> </div
  ><el-empty v-else description="课程不存在或暂不可学习"
    ><el-button @click="router.push('/courses')">返回课程中心</el-button></el-empty
  >
</template>
