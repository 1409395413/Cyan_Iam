import type { MediaItem } from '@/types/content'

/** 防御式取值：某些模块的 media 是对象、某些是数组，这里统一成数组。 */
export function mediaList(v: unknown): MediaItem[] {
  if (!v) return []
  if (Array.isArray(v)) return v.filter(Boolean)
  if (typeof v === 'object') return [v as MediaItem]
  return []
}

/** 后台保存过的富文本字段已由后端 jsoup 白名单净化，此处可直接渲染。 */
export function rich(v?: string): string {
  return v ?? ''
}

/* ---------------------------------------------------------------------------
 * 横竖屏方向
 * -------------------------------------------------------------------------
 * 摄影、剪辑、直播这些活儿必然是横屏 16:9 与竖屏 9:16 混着来。
 * 这里的规则只回答一个问题：这个素材"更像"竖的还是横的。
 * ------------------------------------------------------------------------- */

export type Orientation = 'portrait' | 'landscape' | 'square' | 'unknown'

/** 0.75~1.33 之间都算方图，避免 4:3 被判成横屏、3:4 被判成竖屏 */
export function orientationOf(w?: number, h?: number): Orientation {
  if (!w || !h || w <= 0 || h <= 0) return 'unknown'
  const r = w / h
  if (r > 1.33) return 'landscape'
  if (r < 0.75) return 'portrait'
  return 'square'
}

/**
 * 叠牌画廊里所有卡牌共用的画框（这样一张竖屏和一张横屏并排时视觉重量一致）。
 * 返回 CSS aspect-ratio 用的 "w / h"。
 */
export function uniformFrame(item?: MediaItem | null): string {
  const o = orientationOf(item?.w, item?.h)
  return o === 'portrait' ? '4 / 5' : o === 'landscape' ? '4 / 3' : '1 / 1'
}

/**
 * 展开成主播放位时用的比例 —— 按素材真实方向给，竖屏就给高的，横屏就给宽的。
 * 这就是"点击播放时改变尺寸"的那一次形变。
 */
export function nativeFrame(item?: MediaItem | null): string {
  const w = item?.w
  const h = item?.h
  if (w && h && w > 0 && h > 0) {
    // 限制极端比例，免得一张长图把版面撑爆
    const r = Math.min(Math.max(w / h, 0.5), 2.2)
    return `${r.toFixed(3)} / 1`
  }
  const o = orientationOf(w, h)
  return o === 'portrait' ? '9 / 16' : o === 'square' ? '1 / 1' : '16 / 9'
}

/** 画框策略 → 具体的 aspect-ratio 字符串 */
export function frameOf(item?: MediaItem | null, uniform = false): string {
  const fit = item?.fit || 'auto'
  if (fit === 'native') return nativeFrame(item)
  if (fit === 'uniform') return uniformFrame(item)
  return uniform ? uniformFrame(item) : nativeFrame(item)
}

/** 给后台看的中文描述，例如 "1080×1920 · 竖屏" */
export function describeMedia(item?: MediaItem | null): string {
  if (!item?.w || !item?.h) return '尺寸未知'
  const o = orientationOf(item.w, item.h)
  const label = o === 'portrait' ? '竖屏' : o === 'landscape' ? '横屏' : '方图'
  return `${item.w}×${item.h} · ${label}`
}
