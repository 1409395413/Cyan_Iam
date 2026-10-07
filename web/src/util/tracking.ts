/**
 * 匿名访问埋点。
 * ---------------------------------------------------------------------------
 * 只做三件事：开一次会话、定时报"又停留了多久"、记录作品播放。
 * 不采集姓名/账号/精确定位，IP 在服务端只存哈希。
 *
 * 用法：在 HomeView 里 initTracking()，在 StackGallery 播放时 trackPlay(标题)。
 */
import { API_BASE, trackApi } from '@/api'

let sid: number | null = null
let lastBeat = 0
let timer: ReturnType<typeof setInterval> | null = null
let inited = false

export function initTracking() {
  if (inited) return
  inited = true

  void open()

  // 切到后台/前台都要补一拍，否则"挂着标签页不关"会被算成超长停留
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
    else lastBeat = Date.now()
  })
  window.addEventListener('pagehide', flush)
  window.addEventListener('beforeunload', flush)
}

async function open() {
  try {
    const r = await trackApi.open(location.pathname, document.referrer)
    sid = r.sessionId
    lastBeat = Date.now()
    const gap = Math.max(10_000, r.beatMs || 20_000)
    if (timer) clearInterval(timer)
    timer = setInterval(() => sendBeat(false), gap)
  } catch {
    /* 埋点失败不该影响看站 */
  }
}

function sendBeat(beacon: boolean) {
  if (!sid) return
  const now = Date.now()
  const ms = now - lastBeat
  if (ms < 1000) return
  lastBeat = now

  const body = JSON.stringify({ sessionId: sid, ms })
  if (beacon && navigator.sendBeacon) {
    navigator.sendBeacon(`${API_BASE}/track/beat`, new Blob([body], { type: 'application/json' }))
    return
  }
  trackApi.beat(sid, ms).catch(() => {})
}

function flush() {
  sendBeat(true)
}

/** 点开作品播放时调用 */
export function trackPlay(target: string) {
  if (!sid) return
  trackApi.event(sid, 'play', target).catch(() => {})
}

/** 提交需求 / 点联系方式时调用 */
export function trackEvent(type: string, target = '') {
  if (!sid) return
  trackApi.event(sid, type, target).catch(() => {})
}

export function stopTracking() {
  flush()
  if (timer) clearInterval(timer)
  timer = null
  sid = null
  inited = false
}
