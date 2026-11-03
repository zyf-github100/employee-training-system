import assert from 'node:assert/strict'
import {
  scorePaper,
  isCorrect,
  distributePoints,
  drawQuestions,
  questionError
} from '../src/training/services/assessment.ts'
import { parseCsv, previewRoster } from '../src/training/services/roster.ts'
import { createChapters, curriculumError } from '../src/training/services/curriculum.ts'
import { createSeed } from '../src/training/api/seed.ts'
import { seedQuestionBank } from '../src/training/api/bank-seed.ts'
import { resourceMetadata } from '../src/training/api/local-resources.ts'
const seed = createSeed(),
  bank = seedQuestionBank(seed.exams),
  results = []
function check(name, fn) {
  fn()
  results.push(name)
}
check('三题型与题库 ID 唯一', () => {
  assert.equal(new Set(bank.map((q) => q.id)).size, bank.length)
  assert.deepEqual([...new Set(bank.map((q) => q.type))].sort(), ['boolean', 'multiple', 'single'])
  bank.forEach((q) => assert.equal(questionError(q), ''))
})
const multi = bank.find((q) => q.type === 'multiple')
check('多选全对/漏选/多选/乱序', () => {
  assert(isCorrect(multi, [...multi.answer].reverse()))
  assert(!isCorrect(multi, multi.answer.slice(1)))
  assert(!isCorrect(multi, [0, 1, 2, 3]))
  assert(!isCorrect(multi, undefined))
})
check('按分值判分', () => {
  const paper = [
    { ...bank[0], points: 20 },
    { ...multi, points: 80 }
  ]
  assert.equal(scorePaper(paper, { [multi.id]: multi.answer }), 80)
  assert.equal(scorePaper(paper, {}), 0)
})
check('旧试卷等权判分', () =>
  assert.equal(
    scorePaper(
      seed.exams[0].questions,
      Object.fromEntries(seed.exams[0].questions.map((q) => [q.id, q.answer]))
    ),
    100
  )
)
check('随机抽题不重复、总分 100、不修改题库', () => {
  const before = JSON.stringify(bank)
  for (let i = 0; i < 100; i++) {
    const paper = drawQuestions(bank, { single: 3, multiple: 2, boolean: 2 })
    assert.equal(new Set(paper.map((q) => q.id)).size, 7)
    assert.equal(
      paper.reduce((n, q) => n + q.points, 0),
      100
    )
  }
  assert.equal(JSON.stringify(bank), before)
})
check('抽题不足、零题、负数、非整数拒绝', () => {
  for (const counts of [
    { single: 50, multiple: 0, boolean: 0 },
    { single: 0, multiple: 0, boolean: 0 },
    { single: -1, multiple: 1, boolean: 1 },
    { single: 1.5, multiple: 1, boolean: 1 }
  ])
    assert.throws(() => drawQuestions(bank, counts))
})
check('停用题不参与抽题', () => {
  assert.throws(() =>
    drawQuestions(
      bank.map((q) => ({ ...q, status: '停用' })),
      { single: 1, multiple: 0, boolean: 0 }
    )
  )
})
check('均分余数与题目校验', () => {
  const qs = Array.from({ length: 7 }, () => ({ ...bank[0] }))
  distributePoints(qs)
  assert.deepEqual(
    qs.map((q) => q.points),
    [15, 15, 14, 14, 14, 14, 14]
  )
  assert(questionError({ ...multi, answer: [0] }))
  assert(questionError({ ...bank[0], options: ['A', 'A'] }))
})
check('CSV 引号、逗号、换行', () => {
  assert.deepEqual(parseCsv('"工号","姓名"\r\n"E1","张,三"'), [
    ['工号', '姓名'],
    ['E1', '张,三']
  ])
  assert.throws(() => parseCsv('"未闭合'))
})
check('名单新增/重复/已选/不存在/停用/姓名不符', () => {
  const [a, b] = seed.employees
  const text =
    '工号,姓名\n' +
    a.no +
    ',' +
    a.name +
    '\n' +
    b.no +
    ',' +
    b.name +
    '\n' +
    b.no +
    ',' +
    b.name +
    '\nBAD,某人\n' +
    a.no +
    ',错误'
  assert.deepEqual(
    previewRoster(text, seed.employees, [a.id]).map((r) => r.status),
    ['已在名单', '新增', '文件重复', '错误', '错误']
  )
  assert.equal(previewRoster('工号\n' + a.no, [{ ...a, status: '已停用' }], [])[0].status, '错误')
})
check('CSV BOM/工号大小写/坏表头/空文件', () => {
  const a = seed.employees[0]
  assert.equal(
    previewRoster('\uFEFF工号\n' + a.no.toLowerCase(), seed.employees, [])[0].status,
    '新增'
  )
  assert.throws(() => previewRoster('姓名\n张三', seed.employees, []))
  assert.throws(() => previewRoster('工号', seed.employees, []))
})
check('章节草稿与发布门槛/PPT 不能单独发布', () => {
  const c = createChapters({ ...seed.courses[0], lessons: 1 })
  assert.equal(curriculumError(c), '')
  assert(curriculumError(c, true))
  c[0].resources = [{ type: 'ppt' }]
  assert(curriculumError(c, true))
  c[0].content = '正式学习内容'
  assert.equal(curriculumError(c, true), '')
  c[0].duration = 0
  assert(curriculumError(c))
})
check('旧章节生成、已完成章节索引保留', () => {
  for (const c of seed.courses) {
    const chapters = createChapters(c, true)
    assert.equal(chapters.length, c.lessons)
    assert.equal(curriculumError(chapters, true), '')
  }
})
await assert.rejects(() => resourceMetadata(new File(['bad'], 'invalid.pdf')))
await assert.rejects(() => resourceMetadata(new File(['bad'], 'payload.html')))
await assert.rejects(() => resourceMetadata(new File([], 'empty.mp4')))
assert.equal((await resourceMetadata(new File(['%PDF-1.7'], 'valid.pdf'))).type, 'pdf')
results.push('资源类型、空文件、PDF 文件头校验')

import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { useTraining } from '../src/training/store.ts'
const memory = new Map()
globalThis.localStorage = {
  getItem: (k) => memory.get(k) ?? null,
  setItem: (k, v) => memory.set(k, String(v)),
  removeItem: (k) => memory.delete(k)
}
globalThis.window = { addEventListener: () => {} }
setActivePinia(createPinia())
const s = useTraining()
check('升级保留旧进度并补齐章节、题库与 schema 3', () => {
  assert.equal(s.data.schemaVersion, 3)
  assert.equal(s.data.courses[0].chapters.length, s.data.courses[0].lessons)
  assert.equal(s.data.learning[1][1].progress, 62)
  assert.equal(s.data.questionBank.length, 22)
})
check('已有报名课程锁定章节顺序', () => {
  assert.throws(() => s.saveCurriculum(1, [...s.data.courses[0].chapters].reverse()))
  assert.equal(s.data.learning[1][1].progress, 62)
})
const plan = {
  ...s.data.plans[0],
  id: 90000,
  participantIds: [1, 2],
  courseIds: [12],
  status: '草稿'
}
s.data.plans.push(plan)
check('草稿名单不分配；停用成员不能新增', () => {
  s.saveRoster(plan.id, [1, 2])
  assert(!s.data.learning[2]?.[12])
  const status = s.data.employees[2].status
  s.data.employees[2].status = '已停用'
  assert.throws(() => s.saveRoster(plan.id, [1, 3]))
  s.data.employees[2].status = status
})
check('发布分配去重、移出保留记录、名单统计实时', () => {
  const p = s.data.plans.find((p) => p.id === plan.id)
  p.status = '进行中'
  p.courseIds = [3]
  s.saveRoster(p.id, [1, 2, 2])
  assert.equal(p.participantIds.length, 2)
  assert(s.data.learning[2][3])
  s.data.learning[2][3].progress = 100
  assert.equal(s.planViews.find((p) => p.id === plan.id).completed, 1)
  s.saveRoster(p.id, [1])
  assert(s.data.learning[2][3])
  assert.throws(() => s.saveRoster(p.id, []))
})
const paper = {
  id: 90001,
  title: '验证试卷',
  category: '通用能力',
  duration: 20,
  passScore: 60,
  deadline: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
  status: '草稿',
  questions: drawQuestions(bank, { single: 1, multiple: 1, boolean: 1 }),
  participantIds: [1],
  participants: 1
}
check('试卷发布校验与冻结', () => {
  assert.throws(() => s.savePaper({ ...paper, questions: [] }, true))
  assert.throws(() =>
    s.savePaper({ ...paper, questions: paper.questions.map((q) => ({ ...q, points: 1 })) }, true)
  )
  s.savePaper(paper, true)
  paper.questions[0].text = '源对象修改'
  assert.notEqual(
    s.data.exams.find((e) => e.id === paper.id).questions[0].text,
    paper.questions[0].text
  )
  assert.throws(() => s.savePaper(paper, false))
})
check('试卷人员范围和草稿对员工不可见', () => {
  s.role = 'employee'
  s.employeeId = 2
  assert(!s.canAccessExam(s.data.exams.find((e) => e.id === paper.id)))
  assert.equal(s.beginExam(paper.id), undefined)
  assert(!s.canAccessExam({ ...paper, status: '草稿' }))
  assert(!s.canAccessPlan(s.data.plans.find((p) => p.id === plan.id)))
})
check('员工独立答卷、快照、重复提交幂等', () => {
  s.employeeId = 1
  const a = s.beginExam(paper.id)
  assert(a)
  const deadline = a.deadline
  assert.equal(s.beginExam(paper.id).deadline, deadline)
  a.questionSnapshot.forEach((q) => (a.answers[q.id] = q.answer))
  const first = s.submitExam(paper.id)
  assert.equal(first.score, 100)
  const stamp = first.submittedAt
  a.answers = {}
  assert.equal(s.submitExam(paper.id).score, 100)
  assert.equal(s.submitExam(paper.id).submittedAt, stamp)
  s.employeeId = 2
  assert(!s.attempts[paper.id])
})
check('员工不能写入管理业务', () => {
  assert.throws(() => s.saveBankQuestion(bank[0]))
  assert.throws(() => s.saveRoster(plan.id, [1, 2]))
  assert.throws(() => s.savePaper({ ...paper, id: 90002 }, false))
})
await nextTick()
assert.equal(JSON.parse(memory.get('zhixing-training-v1')).schemaVersion, 3)
results.push('业务更新批量写入本地仓库')
console.log(JSON.stringify({ passed: results.length, checks: results }, null, 2))
