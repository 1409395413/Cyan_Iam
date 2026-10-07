// 站点内容模型 —— 与后端 Java DTO / 数据库中的 JSON 结构严格一致
// 后端不解析模块语义（只做安全校验），因此这里新增字段不会破坏后端接口。

export type ModuleType =
  | 'hero' | 'about' | 'stack' | 'cases' | 'quote' | 'contact' | 'text' | 'custom'
export type MediaKind = 'image' | 'video'
export type SizeToken = 'auto' | 'sm' | 'md' | 'lg' | 'xl' | 'full'

/** 上传素材的真实像素；留空时前端会在加载后自动探测，用于判断横竖屏 */
export interface MediaItem {
  kind: MediaKind
  src?: string
  poster?: string
  title?: string
  /** 卡片副标题 */
  meta?: string
  /** 左上角小标签 */
  badge?: string
  /** 右下角时长 */
  dur?: string
  /** 占位态的尺寸提示 */
  hint?: string
  label?: string
  cap?: string
  cap2?: string
  /** 原始宽高（上传时自动写入，也可手工填） */
  w?: number
  h?: number
  /**
   * 画框策略：
   *  auto    —— 按素材真实方向：竖屏 4:5、横屏 16:9、方图 1:1
   *  uniform —— 强制统一画框（叠牌画廊里的默认表现）
   *  native  —— 完全按原始比例，不裁切
   */
  fit?: 'auto' | 'uniform' | 'native'
}

export interface Action {
  label: string
  href: string
  kind?: 'primary' | 'ghost'
}

export interface KV {
  k: string
  v: string
}

export interface Stat {
  value: string
  label: string
}

export interface HeroReel {
  bar?: string
  media?: MediaItem
}

export interface FooterCol {
  title: string
  links: { label: string; href: string }[]
}

export interface SiteConfig {
  brandName: string
  brandMark: string
  footerNote?: string
  copyright?: string
  footerCols: FooterCol[]
}

export interface CaseItem {
  title: string
  desc?: string
  media?: MediaItem
  specs?: KV[]
  tags?: string[]
}

/** 字段缺失时的兜底字段，未知类型模块也能渲染 */
export interface GenericFields {
  body?: string
  paragraphs?: string[]
  facts?: KV[]
  skills?: string[]
  rows?: KV[]
  projectTypes?: string[]
  [key: string]: unknown
}

export interface MediaSlots {
  /** 部分模块把 media 写成对象而不是数组 */
  media?: MediaItem | MediaItem[]
}

export interface ModuleBase {
  id: string
  type: ModuleType
  visible?: boolean
  flat?: boolean
  size?: SizeToken
  showNav?: boolean
  navLabel?: string
  eyebrow?: string
  title?: string
  lead?: string
}

export interface HeroModule extends ModuleBase {
  type: 'hero'
  actions?: Action[]
  chips?: string[]
  stats?: Stat[]
  reel?: HeroReel
}

export interface AboutModule extends ModuleBase {
  type: 'about'
  paragraphs?: string[]
  media?: MediaItem | MediaItem[]
  facts?: KV[]
  skills?: string[]
}

export interface StackModule extends ModuleBase {
  type: 'stack'
  actions?: Action[]
  media?: MediaItem[]
}

export interface CasesModule extends ModuleBase {
  type: 'cases'
  actions?: Action[]
  cases?: CaseItem[]
}

export interface QuoteModule extends ModuleBase {
  type: 'quote'
}

export interface ContactModule extends ModuleBase {
  type: 'contact'
  rows?: KV[]
  formNote?: string
  submitLabel?: string
  projectTypes?: string[]
}

export interface TextModule extends ModuleBase {
  type: 'text'
  body?: string
}

/** 自定义模块：正文 + 可选素材网格 + 可选参数表，排版交给作者 */
export interface CustomModule extends ModuleBase {
  type: 'custom'
  body?: string
  media?: MediaItem[]
  /** 素材网格列数 */
  columns?: 1 | 2 | 3
  facts?: KV[]
  actions?: Action[]
}

export type PortfolioModule =
  | HeroModule | AboutModule | StackModule | CasesModule
  | QuoteModule | ContactModule | TextModule | CustomModule

export interface SiteContent {
  version: number
  site: SiteConfig
  modules: PortfolioModule[]
}

/* ---------------- 后台鉴权相关 ---------------- */
export interface LoginPayload {
  username: string
  password: string
  remember?: boolean
}

export interface LoginResult {
  token: string
  expiresIn: number
  username: string
  role: string
}

export interface ApiError {
  code: string
  message: string
}

/* ---------------- 后台运行状态 ---------------- */
export interface AdminStatus {
  contentVersion: number
  mediaCount: number
  redis: 'up' | 'down' | 'disabled'
  uploadDir: string
  maxUploadMB: number
}

export interface RevisionItem {
  id: number
  createdAt: string
  bytes: number
  moduleCount: number
}

export interface MediaItemView {
  path: string
  url: string
  mime: string
  size: number
  width?: number
  height?: number
  createdAt?: string
}

/* ---------------- 后台监控 ---------------- */
export interface StatsToday {
  day: string
  /** 今天来访的去重人数 */
  visitors: number
  /** 今天共开了几次会话 */
  sessions: number
  /** 作品播放总次数 */
  plays: number
  /** 点开过作品的人数 */
  playedSessions: number
  /** 平均停留毫秒 */
  avgDurationMs: number
  /** 播放率（百分比，0–100，保留一位小数） */
  playRate: number
}

export interface StatsSeriesItem {
  day: string
  sessions: number
  plays: number
  avgDurationMs: number
}

export interface StatsRecent {
  id: number
  startedAt: string
  durationMs: number
  playCount: number
  entryPath?: string
  referrer?: string | null
  ua?: string
}

export interface AdminStats {
  today: StatsToday
  series: StatsSeriesItem[]
  topPlays: { target: string; count: number }[]
  recent: StatsRecent[]
  inquiry: { total: number; new: number; contacted: number; archived: number }
}

/* ---------------- 工作对接 ---------------- */
export type InquiryStatus = 'new' | 'contacted' | 'archived'

export interface InquiryView {
  id: number
  name?: string
  contact?: string
  type?: string
  budget?: string
  message?: string
  status: InquiryStatus
  note?: string
  ip?: string
  createdAt?: string
}
