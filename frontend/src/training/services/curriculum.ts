import type { Course, CourseChapter } from '../types'
export const chapterTitles = [
  '学习导览与目标',
  '理解核心概念',
  '建立有效的方法',
  '实践中的常见误区',
  '积极倾听与反馈',
  '工作场景案例分析',
  '行动计划与自我练习',
  '课程回顾与总结'
]
export function createChapters(course: Course, legacy = false): CourseChapter[] {
  return Array.from({ length: course.lessons }, (_, i) => ({
    id: `chapter-${course.id}-${i}`,
    title: chapterTitles[i % chapterTitles.length]!,
    duration: Math.min(
      240,
      Math.max(
        1,
        Math.floor(course.duration / course.lessons) +
          (i < course.duration % course.lessons ? 1 : 0)
      )
    ),
    content: legacy
      ? `${course.description}\n\n本节主题：${chapterTitles[i % chapterTitles.length]}\n\n1. 明确工作场景中的学习目标，记录希望解决的问题。\n2. 结合课程主题，尝试一种可以执行的方法。\n3. 在学习笔记中记录行动、结果与复盘。\n\n以上为迁移后的通用示例内容，管理员可在章节与资源中替换为正式课程材料。`
      : '',
    resources: []
  }))
}
export function curriculumError(chapters: CourseChapter[], publishing = false): string {
  if (!chapters.length || chapters.length > 30) return '课程需包含 1–30 个章节'
  if (new Set(chapters.map((c) => c.id)).size !== chapters.length) return '章节标识重复'
  for (const [i, c] of chapters.entries()) {
    if (!c.title.trim() || c.title.length > 80) return `第 ${i + 1} 章需要 1–80 字标题`
    if (!Number.isInteger(c.duration) || c.duration < 1 || c.duration > 240)
      return `第 ${i + 1} 章时长须为 1–240 分钟`
    if (c.content.length > 20000) return `第 ${i + 1} 章正文超过 20000 字`
    if (publishing && !c.content.trim() && !c.resources.some((r) => r.type !== 'ppt'))
      return `第 ${i + 1} 章尚无可学习的正文、视频或 PDF；PPT 附件不能单独作为学习内容`
  }
  return ''
}
