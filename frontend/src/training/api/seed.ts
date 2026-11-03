import type { Course, Employee, TrainingData, Question } from '../types'
const courseRows = [
  [
    '高效沟通与跨部门协作',
    '打破沟通壁垒，让协作更高效',
    '通用能力',
    '陈思远',
    120,
    8,
    86,
    4.9,
    'communication',
    true,
    62
  ],
  [
    'AI 办公效率提升指南',
    '用智能工具，释放你的创造力',
    '专业技能',
    '周予安',
    95,
    6,
    112,
    4.9,
    'ai',
    false,
    28
  ],
  [
    '从业务骨干到团队管理者',
    '完成从个人贡献到团队赋能的转变',
    '管理能力',
    '许知行',
    180,
    12,
    64,
    4.8,
    'leadership',
    false,
    0
  ],
  [
    '信息安全与数据保护',
    '建立安全意识，守护企业数字资产',
    '合规安全',
    '李明哲',
    60,
    5,
    128,
    4.7,
    'security',
    true,
    100
  ],
  [
    '新员工入职第一课',
    '从认识企业开始，开启全新旅程',
    '新人必修',
    '林晓',
    80,
    6,
    48,
    4.9,
    'onboarding',
    true,
    100
  ],
  [
    'Excel 数据分析实战',
    '从数据中发现价值，用洞察驱动业务',
    '专业技能',
    '赵清禾',
    150,
    10,
    92,
    4.8,
    'data',
    false,
    0
  ],
  [
    '结构化思考与问题解决',
    '理清思路，让复杂问题变得简单',
    '通用能力',
    '陈思远',
    90,
    7,
    73,
    4.8,
    'communication',
    false,
    0
  ],
  [
    '项目管理的关键路径',
    '用目标、计划与反馈推动项目落地',
    '管理能力',
    '许知行',
    135,
    9,
    58,
    4.7,
    'leadership',
    false,
    0
  ],
  [
    '职场商务礼仪',
    '在每一次沟通中展现专业与尊重',
    '通用能力',
    '苏言',
    45,
    4,
    68,
    4.6,
    'onboarding',
    false,
    0
  ],
  [
    '客户需求洞察与服务',
    '从客户视角出发，创造持久价值',
    '专业技能',
    '周予安',
    100,
    8,
    52,
    4.8,
    'data',
    false,
    0
  ],
  [
    '企业合规与职业道德',
    '识别工作中的合规边界与责任',
    '合规安全',
    '李明哲',
    60,
    5,
    36,
    4.7,
    'security',
    true,
    0
  ],
  [
    '创新思维工作坊',
    '把好奇心变成可以落地的好想法',
    '通用能力',
    '赵清禾',
    75,
    6,
    0,
    0,
    'ai',
    false,
    0
  ]
] as const
const courses: Course[] = courseRows.map((r, i) => ({
  id: i + 1,
  title: r[0],
  subtitle: r[1],
  category: r[2],
  instructor: r[3],
  duration: r[4],
  lessons: r[5],
  learners: r[6],
  rating: r[7],
  theme: r[8],
  required: r[9],
  progress: r[10],
  status: i === 11 ? '草稿' : '已发布',
  description: `${r[1]}。本课程通过案例讲解、方法练习与情境思考，帮助你将所学应用到实际工作中。适合希望持续提升个人能力的企业员工。`
}))
export const departments = [
  '产品研发部',
  '市场营销部',
  '客户服务部',
  '人力资源部',
  '财务管理部',
  '综合运营部'
]
const names = [
  '林晓',
  '陈思远',
  '周予安',
  '许知行',
  '苏言',
  '赵清禾',
  '李明哲',
  '沈嘉宁',
  '顾南星',
  '何书瑶',
  '陆景和',
  '温以宁',
  '方亦然',
  '江若初',
  '宋知意',
  '程向北'
]
const employees: Employee[] = Array.from({ length: 128 }, (_, i) => ({
  id: i + 1,
  no: `E${String(20260001 + i)}`,
  name: names[i % names.length]! + (i >= 16 ? String(Math.floor(i / 16) + 1) : ''),
  department: departments[i % 6]!,
  job: ['产品经理', '研发工程师', '客户顾问', '培训专员', '财务专员', '运营专员'][i % 6]!,
  email: `employee${i + 1}@zhixing.example`,
  status: '在职',
  progress: 52 + ((i * 7) % 49),
  hours: 8 + ((i * 3) % 36),
  avatar: ['teal', 'blue', 'violet', 'orange'][i % 4]!
}))
export const baseQuestions: Question[] = [
  {
    id: 1,
    text: '跨部门协作开始前，最应该优先明确的是什么？',
    options: ['各部门的个人偏好', '共同目标与责任分工', '会议的次数', '使用哪种聊天工具'],
    answer: 1,
    analysis: '明确共同目标和责任分工，可以减少信息偏差与职责重叠，是高效协作的起点。'
  },
  {
    id: 2,
    text: '收到不明确的需求时，哪种做法更合适？',
    options: [
      '直接按自己的理解执行',
      '等待对方再次联系',
      '复述需求并确认交付标准',
      '转交给其他同事'
    ],
    answer: 2,
    analysis: '复述需求并确认交付标准，有助于双方形成一致理解，降低返工。'
  },
  {
    id: 3,
    text: '团队讨论出现分歧时，应当如何处理？',
    options: [
      '用事实和共同目标讨论方案',
      '坚持自己的观点直到结束',
      '避免任何讨论',
      '由声音最大的人决定'
    ],
    answer: 0,
    analysis: '基于事实与共同目标讨论，有助于把分歧转化为可比较的方案。'
  },
  {
    id: 4,
    text: '以下哪一项属于有效的工作反馈？',
    options: [
      '你一直做得不好',
      '大家都是这么说的',
      '你应该自己知道',
      '这份报告缺少数据来源，建议补上引用'
    ],
    answer: 3,
    analysis: '有效反馈应描述具体行为、影响与可执行的改进建议。'
  },
  {
    id: 5,
    text: '一次协作会议结束后，最有价值的跟进行动是？',
    options: [
      '立即再开一次会议',
      '发送包含负责人和截止时间的行动清单',
      '只保存会议录音',
      '等待下次会议再确认'
    ],
    answer: 1,
    analysis: '明确行动项、负责人和截止时间，可以将讨论转化为可追踪的执行。'
  }
]
export function createSeed(): TrainingData {
  return structuredClone({
    courses,
    employees,
    plans: [
      {
        id: 1,
        title: '2026 年第三季度全员能力提升计划',
        department: '全体部门',
        start: '2026-09-01',
        end: '2026-09-30',
        participants: 128,
        completed: 94,
        status: '进行中',
        courseIds: [1, 2, 4],
        description: '聚焦沟通协作与数字办公，帮助每位伙伴建立面向未来的工作能力。'
      },
      {
        id: 2,
        title: '新员工 30 天成长计划',
        department: '人力资源部',
        start: '2026-09-05',
        end: '2026-10-05',
        participants: 24,
        completed: 18,
        status: '进行中',
        courseIds: [5, 4, 9],
        description: '从文化融入到岗位实践，为新伙伴安排清晰的成长路径。'
      },
      {
        id: 3,
        title: '一线管理者领导力训练营',
        department: '综合运营部',
        start: '2026-09-10',
        end: '2026-10-15',
        participants: 32,
        completed: 12,
        status: '进行中',
        courseIds: [3, 8],
        description: '提升团队沟通、目标管理与人才培养能力。'
      },
      {
        id: 4,
        title: '数据驱动业务专项培训',
        department: '市场营销部',
        start: '2026-09-20',
        end: '2026-10-20',
        participants: 36,
        completed: 0,
        status: '未开始',
        courseIds: [6, 10],
        description: '以实际业务问题为起点，构建数据分析方法。'
      },
      {
        id: 5,
        title: '2026 年信息安全合规培训',
        department: '全体部门',
        start: '2026-08-01',
        end: '2026-08-31',
        participants: 128,
        completed: 128,
        status: '已完成',
        courseIds: [4, 11],
        description: '全员安全意识与信息合规培训。'
      },
      {
        id: 6,
        title: '创新思维与敏捷实践',
        department: '产品研发部',
        start: '2026-10-01',
        end: '2026-10-30',
        participants: 22,
        completed: 0,
        status: '草稿',
        courseIds: [7, 12],
        description: '以工作坊形式培养团队创新能力。'
      }
    ],
    exams: [
      {
        id: 1,
        title: '高效沟通与协作能力测评',
        category: '通用能力',
        duration: 30,
        passScore: 60,
        questions: baseQuestions,
        status: '进行中',
        deadline: '2026-09-30',
        participants: 128
      },
      {
        id: 2,
        title: '信息安全意识专项测评',
        category: '合规安全',
        duration: 20,
        passScore: 80,
        questions: [
          {
            id: 1,
            text: '收到要求输入企业密码的陌生邮件链接时，应当如何处理？',
            options: [
              '立即输入密码',
              '转发给全部同事',
              '核实来源并向安全负责人报告',
              '回复邮件提供验证码'
            ],
            answer: 2,
            analysis: '应通过可信渠道核实来源，不在陌生链接中提交账号凭证。'
          },
          {
            id: 2,
            text: '以下哪种文件共享方式更符合数据保护要求？',
            options: [
              '上传到公开网盘',
              '使用经授权的企业共享空间',
              '发送到个人社交群',
              '取消全部访问限制'
            ],
            answer: 1,
            analysis: '使用经过授权的企业共享空间，按最小权限分配访问范围。'
          },
          {
            id: 3,
            text: '离开办公电脑时应当？',
            options: ['锁定屏幕', '保持所有页面开放', '告诉同事密码', '只关闭显示器'],
            answer: 0,
            analysis: '锁定屏幕能够防止他人在离席期间访问企业信息。'
          },
          {
            id: 4,
            text: '企业账号密码应当如何管理？',
            options: [
              '所有平台使用同一密码',
              '写在显示器旁',
              '分享给同事方便协作',
              '使用独立强密码并妥善保管'
            ],
            answer: 3,
            analysis: '独立强密码和妥善的凭据管理有助于降低账号被盗风险。'
          },
          {
            id: 5,
            text: '发现误发了包含客户信息的文件后应当？',
            options: [
              '不做处理',
              '立即报告并按流程采取补救措施',
              '等客户联系',
              '删除自己的发送记录'
            ],
            answer: 1,
            analysis: '及时报告并配合补救，才能有效控制数据泄露的影响。'
          }
        ],
        status: '进行中',
        deadline: '2026-09-25',
        participants: 128
      },
      {
        id: 3,
        title: '新员工入职综合测评',
        category: '新人必修',
        duration: 30,
        passScore: 60,
        questions: baseQuestions,
        status: '未开始',
        deadline: '2026-10-05',
        participants: 24
      }
    ],
    feedback: [
      {
        id: 1,
        title: '希望增加跨部门沟通的实际案例',
        content: '沟通课程很有帮助，希望补充一些产品与研发团队协作的场景案例。',
        type: '课程建议',
        author: '林晓',
        status: '待处理',
        date: '2026-09-14',
        reply: ''
      },
      {
        id: 2,
        title: '是否可以提供课程讲义下载？',
        content: '学习 Excel 数据分析时想对照讲义练习，请问可以提供配套资料吗？',
        type: '课程提问',
        author: '周予安',
        status: '待处理',
        date: '2026-09-13',
        reply: ''
      },
      {
        id: 3,
        title: '新员工培训安排非常清晰',
        content: '入职学习路径很清楚，感谢培训团队的准备。',
        type: '学习反馈',
        author: '林晓',
        status: '已回复',
        date: '2026-09-12',
        reply: '谢谢你的反馈，祝你在知行开启愉快的成长旅程！'
      }
    ],
    notices: [
      {
        id: 1,
        title: '第三季度培训计划已发布',
        category: '培训通知',
        date: '2026-09-14',
        content:
          '第三季度全员能力提升计划已正式发布，请于 9 月 30 日前完成必修课程与测评。课程涵盖高效沟通、AI 办公与信息安全，请在「我的培训」中查看安排。',
        read: false
      },
      {
        id: 2,
        title: '新课程上线：AI 办公效率提升指南',
        category: '课程上新',
        date: '2026-09-12',
        content: '6 个章节覆盖常见办公场景，帮助你建立有效的人机协作方式。欢迎前往课程中心学习。',
        read: false
      },
      {
        id: 3,
        title: '学习平台使用指南',
        category: '平台公告',
        date: '2026-09-10',
        content:
          '你可以在页面右上角切换管理端与员工端。当前为本地演示工作区，课程、考试、计划等操作保存在本浏览器，不会发送到服务器。',
        read: true
      }
    ],
    attempts: {},
    settings: {
      company: '知行科技',
      admin: '林管理员',
      email: 'learning@zhixing.example',
      reminder: true,
      autoSave: true,
      theme: 'teal'
    }
  } as TrainingData)
}
