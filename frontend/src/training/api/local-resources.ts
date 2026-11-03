import type { CourseResource } from '../types'
const DB_NAME = 'zhixing-training-resources',
  STORE = 'files'
let opening: Promise<IDBDatabase> | undefined
function database(): Promise<IDBDatabase> {
  if (!opening)
    opening = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 1)
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(STORE))
          request.result.createObjectStore(STORE)
      }
      request.onsuccess = () => {
        request.result.onversionchange = () => {
          request.result.close()
          opening = undefined
        }
        resolve(request.result)
      }
      request.onerror = () => {
        opening = undefined
        reject(Error('本机资源存储不可用，请检查浏览器存储权限'))
      }
      request.onblocked = () => {
        opening = undefined
        reject(Error('资源库升级被其他标签页阻止，请关闭其他培训平台页面'))
      }
    })
  return opening
}
export async function saveResourceFiles(files: Map<string, File>) {
  if (!files.size) return
  const db = await database()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(Error('资源保存失败，可能已超出浏览器存储配额；请减少文件大小后重试'))
    tx.onabort = () => reject(Error('资源保存已中止，本次文件未提交'))
    files.forEach((file, id) => tx.objectStore(STORE).put(file, id))
  })
}
export async function getResourceFile(meta: CourseResource): Promise<File> {
  const db = await database()
  const blob = await new Promise<Blob | undefined>((resolve, reject) => {
    const r = db.transaction(STORE, 'readonly').objectStore(STORE).get(meta.id)
    r.onsuccess = () => resolve(r.result)
    r.onerror = () => reject(Error('资源读取失败，请重试'))
  })
  if (!blob)
    throw Error('本浏览器未找到资源文件。请回到管理端重新添加；其他浏览器或端口不共享本机资源。')
  return new File([blob], meta.name, { type: meta.mime, lastModified: meta.lastModified })
}
export async function removeResourceFiles(ids: string[]) {
  if (!ids.length) return
  const db = await database()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(Error('旧资源文件清理失败'))
    tx.onabort = () => reject(Error('旧资源文件清理被中止'))
    ids.forEach((id) => tx.objectStore(STORE).delete(id))
  })
}
export async function resourceMetadata(file: File): Promise<CourseResource> {
  const ext = file.name.split('.').pop()?.toLowerCase()
  const type = ['mp4', 'webm', 'ogg'].includes(ext || '')
    ? 'video'
    : ext === 'pdf'
      ? 'pdf'
      : ['ppt', 'pptx'].includes(ext || '')
        ? 'ppt'
        : null
  if (!type) throw Error('仅支持 MP4、WebM、Ogg、PDF、PPT 和 PPTX 文件')
  const max = type === 'video' ? 100 : 30
  if (!file.size || file.size > max * 1024 * 1024)
    throw Error(`${file.name} 应为非空文件，且不超过 ${max} MB`)
  if (type === 'pdf' && !(await file.slice(0, 5).text()).startsWith('%PDF-'))
    throw Error(`${file.name} 不是有效的 PDF 文件头`)
  const mime =
    type === 'pdf'
      ? 'application/pdf'
      : type === 'video'
        ? ext === 'mp4'
          ? 'video/mp4'
          : ext === 'webm'
            ? 'video/webm'
            : 'video/ogg'
        : file.type
  return {
    id: crypto.randomUUID(),
    name: file.name,
    type,
    size: file.size,
    mime,
    lastModified: file.lastModified,
    createdAt: new Date().toISOString()
  }
}
export const fileSize = (bytes: number) =>
  bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
