import type { BankQuestion, Exam } from '../types'
export function seedQuestionBank(exams: Exam[]): BankQuestion[] {
  const seen = new Set<string>(),
    bank: BankQuestion[] = []
  for (const e of exams)
    for (const q of e.questions) {
      if (seen.has(q.text)) continue
      seen.add(q.text)
      bank.push({
        ...JSON.parse(JSON.stringify(q)),
        id: 100001 + bank.length,
        type: q.type || 'single',
        category: e.category,
        difficulty: '基础',
        status: '启用'
      })
    }
  const extras: Array<[string, string[], number[] | number, string]> = [
    [
      '制定团队行动计划时，哪些信息有助于执行？',
      ['负责人', '截止时间', '验收标准', '仅写一句“尽快完成”'],
      [0, 1, 2],
      '行动清单需要明确责任、期限与验收标准。'
    ],
    [
      '积极倾听包括哪些行为？',
      ['复述关键信息', '提出澄清问题', '频繁打断对方', '确认理解一致'],
      [0, 1, 3],
      '倾听需要确认理解，避免随意打断。'
    ],
    [
      '跨部门协作前，应优先对齐哪些内容？',
      ['共同目标', '职责边界', '交付标准', '各自的个人偏好必须相同'],
      [0, 1, 2],
      '围绕目标、职责和交付建立共识。'
    ],
    [
      '复盘时适合记录哪些内容？',
      ['实际结果', '遇到的阻碍', '下一步改进', '只追究个人责任'],
      [0, 1, 2],
      '复盘应聚焦结果、原因和可执行的改进。'
    ],
    [
      '清晰的工作反馈可以包含哪些信息？',
      ['观察到的事实', '对目标的影响', '具体改进建议', '未经验证的人身判断'],
      [0, 1, 2],
      '事实、影响、建议有助于形成建设性反馈。'
    ],
    [
      '确认需求时，哪些做法有帮助？',
      ['复述预期成果', '确认约束条件', '记录变更', '默认所有人理解一致'],
      [0, 1, 2],
      '显式确认和记录可以减少理解偏差。'
    ],
    ['会议结束后，应明确下一步行动的负责人。', ['正确', '错误'], 0, '明确负责人有助于行动落地。'],
    ['复述对方观点可以用于校准双方理解。', ['正确', '错误'], 0, '复述是确认理解的常用方法。'],
    ['任务只写“尽快完成”，就等于设定了明确截止时间。', ['正确', '错误'], 1, '应明确具体时间节点。'],
    ['反馈中应区分可观察的事实和个人推测。', ['正确', '错误'], 0, '区分事实与推测可减少误解。'],
    ['只要自己完成工作，就不需要告知协作方。', ['正确', '错误'], 1, '及时同步有助于后续工作衔接。'],
    ['复盘的目的之一是形成可复用的改进方法。', ['正确', '错误'], 0, '复盘帮助团队积累可复用经验。']
  ]
  extras.forEach(([text, options, answer, analysis], i) =>
    bank.push({
      id: 200001 + i,
      text,
      options,
      answer,
      analysis,
      type: Array.isArray(answer) ? 'multiple' : 'boolean',
      category: '通用能力',
      difficulty: i % 3 === 0 ? '进阶' : '基础',
      status: '启用'
    })
  )
  return bank
}
