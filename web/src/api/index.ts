import type {
  AdminStats,
  AdminStatus,
  ApiError,
  InquiryStatus,
  InquiryView,
  LoginPayload,
  LoginResult,
  MediaItemView,
  RevisionItem,
  SiteContent
} from '@/types/content'

const BASE = import.meta.env.VITE_API_BASE || '/api'

/** sendBeacon 需要拼绝对路径，所以把前缀也导出去 */
export const API_BASE = BASE

export class HttpError extends Error {
  status: number
  code: string
  constructor(status: number, body: Partial<ApiError>) {
    super(body.message || `HTTP ${status}`)
    this.status = status
    this.code = body.code || String(status)
  }
}

const TOKEN_KEY = 'yc_admin_token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = tokenStore.get()
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(init.headers as Record<string, string> | undefined)
  }
  if (token) headers.Authorization = `Bearer ${token}`
  if (init.body && typeof init.body === 'string') headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path, { ...init, headers, credentials: 'same-origin' })
  if (res.status === 401) tokenStore.clear()
  if (!res.ok) {
    let body: Partial<ApiError> = {}
    try { body = await res.json() } catch { /* ignore */ }
    throw new HttpError(res.status, body)
  }
  if (res.status === 204) return undefined as unknown as T
  return res.json() as Promise<T>
}

export const http = request

/* ------------------------------ 公开接口 ------------------------------ */
export const fetchContent = () => request<SiteContent>('/content')

/** 访客在底部提交需求 */
export function submitInquiry(payload: {
  name: string
  contact: string
  type?: string
  budget?: string
  message: string
}) {
  return request<{ status: string }>('/inquiry', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

/** 匿名访问埋点：开会话 / 心跳 / 关键动作 */
export const trackApi = {
  open(path: string, referrer: string) {
    return request<{ sessionId: number; beatMs: number }>('/track/session', {
      method: 'POST',
      body: JSON.stringify({ path, referrer })
    })
  },
  beat(sessionId: number, ms: number) {
    return request<{ status: string }>('/track/beat', {
      method: 'POST',
      body: JSON.stringify({ sessionId, ms })
    })
  },
  event(sessionId: number, type: string, target: string) {
    return request<{ status: string }>('/track/event', {
      method: 'POST',
      body: JSON.stringify({ sessionId, type, target })
    })
  }
}

/* ------------------------------ 后台接口 ------------------------------ */
export const adminApi = {
  login(payload: LoginPayload) {
    return request<LoginResult>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    })
  },
  logout() {
    return request<void>('/auth/logout', { method: 'POST' })
  },
  me() {
    return request<{ username: string; role: string; checkedAt: string }>('/auth/me')
  },
  changePassword(oldPassword: string, newPassword: string) {
    return request<{ status: string }>('/auth/password', {
      method: 'POST',
      body: JSON.stringify({ oldPassword, newPassword })
    })
  },
  saveContent(content: SiteContent) {
    return request<{ version: number }>('/content', {
      method: 'PUT',
      body: JSON.stringify(content)
    })
  },
  status() {
    return request<AdminStatus>('/admin/status')
  },
  revisions() {
    return request<RevisionItem[]>('/admin/revisions')
  },
  rollback(id: number) {
    return request<{ version: number }>(`/admin/revisions/${id}/rollback`, { method: 'POST' })
  },
  upload(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request<{ url: string; mime: string; size: number }>('/media', {
      method: 'POST',
      body: form
    })
  },
  listMedia() {
    return request<MediaItemView[]>('/media')
  },
  deleteMedia(path: string) {
    return request<void>(`/media?path=${encodeURIComponent(path)}`, { method: 'DELETE' })
  },
  /* ---- 监控 ---- */
  stats(days = 7) {
    return request<AdminStats>(`/admin/stats?days=${days}`)
  },
  /* ---- 工作对接 ---- */
  inquiries() {
    return request<InquiryView[]>('/admin/inquiries')
  },
  patchInquiry(id: number, patch: { status?: InquiryStatus; note?: string }) {
    return request<InquiryView>(`/admin/inquiries/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch)
    })
  },
  deleteInquiry(id: number) {
    return request<void>(`/admin/inquiries/${id}`, { method: 'DELETE' })
  }
}
