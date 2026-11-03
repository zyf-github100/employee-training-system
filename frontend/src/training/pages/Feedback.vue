<script setup lang="ts">
  import { computed, reactive, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useTraining } from '../store'
  import type { Feedback } from '../types'
  const s = useTraining(),
    tab = ref('全部反馈'),
    editor = ref(false),
    noticeEditor = ref(false),
    selected = ref<Feedback | null>(null),
    reply = ref('')
  const form = reactive({ title: '', type: '课程建议', content: '' }),
    notice = reactive({ title: '', content: '', category: '培训通知' })
  const filtered = computed(() =>
    s.data.feedback.filter(
      (f) =>
        (s.isAdmin || f.employeeId === s.employeeId) &&
        (tab.value === '全部反馈' || tab.value === f.status)
    )
  )
  function submit() {
    if (!form.title.trim() || !form.content.trim()) {
      ElMessage.warning('请填写标题和反馈内容')
      return
    }
    s.data.feedback.unshift({
      ...form,
      id: Date.now(),
      author: s.displayName,
      employeeId: s.isAdmin ? undefined : s.employeeId,
      status: '待处理',
      date: new Date().toISOString().slice(0, 10),
      reply: ''
    })
    editor.value = false
    Object.assign(form, { title: '', type: '课程建议', content: '' })
    tab.value = '全部反馈'
    ElMessage.success('反馈已提交，感谢你的建议')
  }
  function respond() {
    if (!selected.value || !reply.value.trim()) {
      ElMessage.warning('请输入回复内容')
      return
    }
    selected.value.reply = reply.value.trim()
    selected.value.status = '已回复'
    selected.value = null
    reply.value = ''
    ElMessage.success('回复已发送')
  }
  function publish() {
    if (!notice.title.trim() || !notice.content.trim()) {
      ElMessage.warning('请填写公告标题和正文')
      return
    }
    s.data.notices.unshift({
      ...notice,
      id: Date.now(),
      date: new Date().toISOString().slice(0, 10),
      read: false
    })
    noticeEditor.value = false
    notice.title = ''
    notice.content = ''
    ElMessage.success('公告已发布')
  }
</script>
<template>
  <div
    ><PageTitle
      title="交流与反馈"
      :description="
        s.isAdmin
          ? '倾听每一个声音，让学习体验持续变好。'
          : '分享你的想法，我们一起让学习变得更好。'
      "
      ><el-button v-if="s.isAdmin" @click="noticeEditor = true"
        ><AppIcon name="bell" :size="15" />发布公告</el-button
      ><el-button type="primary" @click="editor = true"
        ><AppIcon name="plus" :size="15" />提交反馈</el-button
      ></PageTitle
    ><div class="feedback-hero"
      ><span class="feedback-hero-icon"><AppIcon name="chat" :size="37" /></span
      ><div
        ><h2>好的学习体验，从倾听开始</h2
        ><p>无论是一个问题，还是一个新想法，我们都认真对待。</p></div
      ><span class="tiny-label">YOUR VOICE MATTERS</span></div
    ><section class="panel"
      ><div class="list-toolbar"
        ><div class="content-tabs"
          ><button
            v-for="v in ['全部反馈', '待处理', '已回复']"
            :key="v"
            :class="{ active: tab === v }"
            @click="tab = v"
            >{{ v }}</button
          ></div
        ><span class="muted small">{{ filtered.length }} 条反馈</span></div
      ><div class="feedback-list"
        ><article v-for="f in filtered" :key="f.id"
          ><span class="avatar" :class="f.status === '已回复' ? 'blue' : 'teal'">{{
            f.author.slice(0, 1)
          }}</span
          ><div class="feedback-main"
            ><div class="feedback-title"
              ><h3>{{ f.title }}</h3
              ><StatusBadge :status="f.status" /></div
            ><p>{{ f.content }}</p
            ><div class="feedback-meta"
              ><span>{{ f.author }}</span
              ><span>{{ f.type }}</span
              ><span>{{ f.date }}</span
              ><button
                v-if="s.isAdmin"
                class="text-link"
                @click="
                  () => {
                    selected = f
                    reply = f.reply
                  }
                "
                >{{ f.reply ? '编辑回复' : '回复反馈' }}</button
              ></div
            ><div v-if="f.reply" class="feedback-reply"
              ><span class="category-label">培训团队回复</span><p>{{ f.reply }}</p></div
            ></div
          ></article
        ><el-empty
          v-if="!filtered.length"
          description="这里还没有反馈，期待听到你的声音" /></div></section
    ><el-dialog v-model="editor" title="让我们听见你的想法" width="560px" class="responsive-dialog"
      ><el-form label-position="top"
        ><el-form-item label="反馈类型"
          ><el-select v-model="form.type"
            ><el-option
              v-for="v in ['课程建议', '课程提问', '学习反馈', '系统问题']"
              :key="v"
              :value="v" /></el-select></el-form-item
        ><el-form-item label="标题 *"
          ><el-input
            v-model="form.title"
            maxlength="80"
            placeholder="简要描述你的问题或建议" /></el-form-item
        ><el-form-item label="详细内容 *"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="可以告诉我们具体的课程、遇到的问题或你的改进想法…" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editor = false">取消</el-button
        ><el-button type="primary" @click="submit">提交反馈</el-button></template
      ></el-dialog
    ><el-dialog
      :model-value="!!selected"
      title="回复员工反馈"
      width="540px"
      class="responsive-dialog"
      @close="selected = null"
      ><h3>{{ selected?.title }}</h3
      ><p class="reading-copy muted">{{ selected?.content }}</p
      ><el-input
        v-model="reply"
        type="textarea"
        :rows="5"
        placeholder="写下你的回复"
        maxlength="2000"
      /><template #footer
        ><el-button @click="selected = null">取消</el-button
        ><el-button type="primary" @click="respond">发送回复</el-button></template
      ></el-dialog
    ><el-dialog v-model="noticeEditor" title="发布通知公告" width="560px" class="responsive-dialog"
      ><el-form label-position="top"
        ><el-form-item label="公告标题 *"
          ><el-input v-model="notice.title" maxlength="80" /></el-form-item
        ><el-form-item label="公告类型"
          ><el-select v-model="notice.category"
            ><el-option
              v-for="v in ['培训通知', '课程上新', '平台公告']"
              :key="v"
              :value="v" /></el-select></el-form-item
        ><el-form-item label="公告正文 *"
          ><el-input
            v-model="notice.content"
            type="textarea"
            :rows="6"
            maxlength="3000" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="noticeEditor = false">取消</el-button
        ><el-button type="primary" @click="publish">发布给全体员工</el-button></template
      ></el-dialog
    ></div
  >
</template>
