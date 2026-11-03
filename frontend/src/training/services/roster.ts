import type { Employee } from '../types'
export interface RosterImportRow {
  line: number
  no: string
  name: string
  employeeId?: number
  status: '新增' | '已在名单' | '文件重复' | '错误'
  error?: string
}
export function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = [],
    cell = '',
    quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (c === '"') {
      if (quoted && text[i + 1] === '"') {
        cell += '"'
        i++
      } else quoted = !quoted
    } else if (c === ',' && !quoted) {
      row.push(cell)
      cell = ''
    } else if ((c === '\n' || c === '\r') && !quoted) {
      if (c === '\r' && text[i + 1] === '\n') i++
      row.push(cell)
      if (row.some((v) => v.trim())) rows.push(row)
      row = []
      cell = ''
    } else cell += c
  }
  if (quoted) throw Error('CSV 引号未闭合')
  row.push(cell)
  if (row.some((v) => v.trim())) rows.push(row)
  return rows
}
export function previewRoster(
  text: string,
  employees: Employee[],
  selected: number[]
): RosterImportRow[] {
  const rows = parseCsv(text.replace(/^\uFEFF/, '')),
    header = rows.shift()?.map((s) => s.trim()) || [],
    noIndex = header.indexOf('工号'),
    nameIndex = header.indexOf('姓名')
  if (noIndex < 0) throw Error('CSV 表头必须包含“工号”；可下载参训名单模板')
  if (!rows.length || rows.length > 10000) throw Error('文件须包含 1–10000 条员工记录')
  const seen = new Set<string>()
  return rows.map((row, index) => {
    const no = (row[noIndex] || '').trim().toUpperCase(),
      name = nameIndex < 0 ? '' : (row[nameIndex] || '').trim(),
      e = employees.find((e) => e.no.toUpperCase() === no)
    let error = !no
      ? '工号为空'
      : !e
        ? '工号不存在'
        : e.status !== '在职'
          ? '员工已停用'
          : name && name !== e.name
            ? '姓名与员工名单不匹配'
            : ''
    if (row.length !== header.length) error = '列数与表头不一致'
    const status = error
      ? '错误'
      : seen.has(no)
        ? '文件重复'
        : selected.includes(e!.id)
          ? '已在名单'
          : '新增'
    seen.add(no)
    return { line: index + 2, no, name: e?.name || name, employeeId: e?.id, status, error }
  })
}
