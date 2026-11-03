import type { TrainingData, LearningRecord } from '../types'
import { createChapters } from '../services/curriculum'
import { seedQuestionBank } from './bank-seed'

export function emptyLearning(courseId: number): LearningRecord {
  return {
    courseId,
    enrolledAt: new Date().toISOString(),
    lastStudiedAt: null,
    completedAt: null,
    completedLessons: [],
    progress: 0,
    seconds: 0,
    lastChapter: 0,
    note: '',
    videoPositions: {}
  }
}

/** Extend v1 in place, without clearing existing courses, staff or user edits. */
export function upgradeTrainingData(data: TrainingData, includeLegacyNotes = true): TrainingData {
  if (!data.accounts)
    data.accounts = [
      { no: 'ADMIN', role: 'admin', employeeId: null, active: true, passwordHash: '', salt: '' },
      {
        no: data.employees.find((e) => e.id === 1)?.no || 'E20260001',
        role: 'employee',
        employeeId: 1,
        active: true,
        passwordHash: '',
        salt: ''
      }
    ]
  if (!data.learning) {
    const initial: Record<number, LearningRecord> = {}
    for (const c of data.courses) {
      let legacyNote = ''
      if (includeLegacyNotes) {
        try {
          legacyNote = localStorage.getItem(`zhixing-note-${c.id}`) || ''
        } catch {}
      }
      if (c.progress <= 0 && !legacyNote) continue
      initial[c.id] = {
        ...emptyLearning(c.id),
        enrolledAt: '2026-09-01T01:00:00.000Z',
        progress: c.progress,
        lastStudiedAt: '2026-09-14T00:00:00.000Z',
        completedAt: c.progress === 100 ? '2026-09-10T02:00:00.000Z' : null,
        completedLessons:
          c.completedLessons ??
          Array.from({ length: Math.floor((c.progress * c.lessons) / 100) }, (_, i) => i),
        lastChapter: Math.min(c.lessons - 1, Math.floor((c.progress * c.lessons) / 100)),
        seconds: Math.round((c.duration * 60 * c.progress) / 100)
      }
      // v1 notes remain on disk; copy only during migration.
      initial[c.id]!.note = legacyNote
    }
    data.learning = { 1: initial }
  }
  if (!data.employeeAttempts) data.employeeAttempts = { 1: data.attempts || {} }
  for (const [employeeId, attempts] of Object.entries(data.employeeAttempts)) {
    for (const attempt of Object.values(attempts)) attempt.employeeId = Number(employeeId)
  }
  data.reviews ??= []
  for (const f of data.feedback) {
    if (f.employeeId === undefined)
      f.employeeId = data.employees.find((e) => e.name === f.author)?.id
  }
  for (const course of data.courses) course.chapters ??= createChapters(course, true)
  data.questionBank ??= seedQuestionBank(data.exams)
  for (const attempts of Object.values(data.employeeAttempts))
    for (const attempt of Object.values(attempts)) {
      const exam = data.exams.find((e) => e.id === attempt.examId)
      if (exam) {
        attempt.questionSnapshot ??= JSON.parse(JSON.stringify(exam.questions))
        attempt.passScore ??= exam.passScore
        attempt.examTitle ??= exam.title
      }
    }
  data.schemaVersion = 3
  return data
}
