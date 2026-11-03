import type { Answer, BankQuestion, Question, QuestionType } from '../types'
export const questionTypeNames: Record<QuestionType, string> = {
  single: '单选题',
  multiple: '多选题',
  boolean: '判断题'
}
export const answerIndices = (answer: Answer | undefined): number[] =>
  answer === undefined ? [] : Array.isArray(answer) ? answer : [answer]
export function isCorrect(q: Question, answer: Answer | undefined): boolean {
  const actual = [...new Set(answerIndices(answer))].sort((a, b) => a - b),
    expected = [...new Set(answerIndices(q.answer))].sort((a, b) => a - b)
  return (
    actual.length > 0 &&
    actual.length === expected.length &&
    actual.every((v, i) => v === expected[i])
  )
}
export const answerText = (q: Question, answer: Answer | undefined) =>
  answerIndices(answer)
    .map((i) => `${String.fromCharCode(65 + i)} · ${q.options[i] || ''}`)
    .join('；') || '未作答'
export function scorePaper(questions: Question[], answers: Record<number, Answer>): number {
  const total = questions.reduce((sum, q) => sum + (q.points ?? 1), 0)
  return total
    ? Math.round(
        (questions.reduce(
          (sum, q) => sum + (isCorrect(q, answers[q.id]) ? (q.points ?? 1) : 0),
          0
        ) /
          total) *
          100
      )
    : 0
}
export function questionError(q: Question): string {
  if (!q.text.trim() || q.text.length > 1000) return '题干应为 1–1000 字'
  if (
    q.options.length < 2 ||
    q.options.length > 8 ||
    q.options.some((o) => !o.trim() || o.length > 300)
  )
    return '请填写 2–8 个有效选项，每项不超过 300 字'
  if (new Set(q.options.map((o) => o.trim())).size !== q.options.length) return '选项内容不能重复'
  const answer = answerIndices(q.answer)
  if (!answer.length || answer.some((i) => !Number.isInteger(i) || i < 0 || i >= q.options.length))
    return '请选择有效的正确答案'
  if (q.type === 'multiple' && new Set(answer).size < 2) return '多选题至少设置两个正确选项'
  if (q.type !== 'multiple' && (Array.isArray(q.answer) || answer.length !== 1))
    return '单选题和判断题只能有一个正确答案'
  if (q.type === 'boolean' && q.options.length !== 2) return '判断题必须有正确、错误两个选项'
  return ''
}
export function distributePoints(questions: Question[]) {
  if (!questions.length) return
  const base = Math.floor(100 / questions.length),
    remainder = 100 % questions.length
  questions.forEach((q, i) => (q.points = base + (i < remainder ? 1 : 0)))
}
export function drawQuestions(
  pool: BankQuestion[],
  counts: Record<QuestionType, number>
): Question[] {
  const result: Question[] = []
  for (const type of ['single', 'multiple', 'boolean'] as const) {
    const count = counts[type],
      available = pool.filter((q) => q.status === '启用' && q.type === type)
    if (!Number.isInteger(count) || count < 0 || count > 50)
      throw Error('各题型抽题数量应为 0–50 的整数')
    if (count > available.length)
      throw Error(`${questionTypeNames[type]}需要 ${count} 题，当前筛选仅有 ${available.length} 题`)
    for (let i = available.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[available[i], available[j]] = [available[j]!, available[i]!]
    }
    result.push(...JSON.parse(JSON.stringify(available.slice(0, count))))
  }
  if (!result.length || result.length > 50) throw Error('一份试卷应包含 1–50 道题')
  distributePoints(result)
  return result
}
