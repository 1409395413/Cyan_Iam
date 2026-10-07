<script setup lang="ts">
/**
 * 内容后台。
 * ---------------------------------------------------------------------------
 * 左：模块列表（增删 / 排序 / 停用）；中：按模块类型自动生成的字段表单；
 * 上：保存与导入导出。保存直接 PUT /api/content，前端刷新即可看到 —— 不需要重新构建。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, fetchContent, tokenStore } from '@/api'
import FieldsEditor from '@/components/admin/FieldsEditor.vue'
import { SITE_FIELDS, TYPE_DEFS, typeDef } from '@/moduleSchema'
import type {
  AdminStats,
  AdminStatus,
  InquiryStatus,
  InquiryView,
  PortfolioModule,
  RevisionItem,
  SiteContent
} from '@/types/content'

const router = useRouter()

const content = ref<SiteContent | null>(null)
const status = ref<AdminStatus | null>(null)
const revisions = ref<RevisionItem[]>([])
const inquiries = ref<InquiryView[]>([])
const stats = ref<AdminStats | null>(null)
const statsDays = ref(7)
const inboxFilter = ref<'all' | InquiryStatus>('all')
const tab = ref<'modules' | 'site' | 'media' | 'stats' | 'inbox' | 'account'>('modules')
const selectedId = ref('')
const loading = ref(true)
const saving = ref(false)
const notice = ref<{ kind: 'ok' | 'err'; text: string } | null>(null)

let noticeTimer: ReturnType<typeof setTimeout> | null = null
function flash(kind: 'ok' | 'err', text: string) {
  notice.value = { kind, text }
  if (noticeTimer) clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => (notice.value = null), 4000)
}

const modules = computed(() => content.value?.modules || [])
const current = computed(() => modules.value.find((m) => m.id === selectedId.value) || null)
const currentDef = computed(() => (current.value ? typeDef(current.value.type) : undefined))

/* ------------------------------ 数据加载 ------------------------------ */
onMounted(async () => {
  try {
    content.value = await adminApi.me().then(() => fetchRemote())
    selectedId.value = modules.value[0]?.id || ''
    await Promise.all([loadStatus(), loadRevisions(), loadInquiries(), loadStats()])
  } catch (e: any) {
    flash('err', e?.message || '载入失败')
  } finally {
    loading.value = false
  }
})

async function fetchRemote() {
  return fetchContent()
}

async function loadStatus() {
  try { status.value = await adminApi.status() } catch { /* ignore */ }
}
async function loadRevisions() {
  try { revisions.value = await adminApi.revisions() } catch { /* ignore */ }
}
async function loadInquiries() {
  try { inquiries.value = await adminApi.inquiries() } catch { /* ignore */ }
}
async function loadStats() {
  try { stats.value = await adminApi.stats(statsDays.value) } catch { /* ignore */ }
}

/* ------------------------------ 监控面板 ------------------------------ */
const STATUS_LABEL: Record<InquiryStatus, string> = {
  new: '未读',
  contacted: '已联系',
  archived: '已归档'
}

function humanDuration(ms?: number): string {
  const s = Math.round((ms || 0) / 1000)
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} 分 ${s % 60} 秒`
  return `${Math.floor(m / 60)} 小时 ${m % 60} 分`
}

const maxSeries = computed(() => {
  const arr = stats.value?.series || []
  return Math.max(1, ...arr.map((d) => d.sessions))
})

const filteredInquiries = computed(() => {
  const all = inquiries.value
  return inboxFilter.value === 'all' ? all : all.filter((q) => q.status === inboxFilter.value)
})

const inboxCounts = computed(() => ({
  all: inquiries.value.length,
  new: inquiries.value.filter((q) => q.status === 'new').length,
  contacted: inquiries.value.filter((q) => q.status === 'contacted').length,
  archived: inquiries.value.filter((q) => q.status === 'archived').length
}))

async function setStatus(q: InquiryView, s: InquiryStatus) {
  try {
    const updated = await adminApi.patchInquiry(q.id, { status: s })
    const i = inquiries.value.findIndex((x) => x.id === q.id)
    if (i >= 0) inquiries.value[i] = updated
    flash('ok', `已标记为「${STATUS_LABEL[s]}」`)
  } catch (e: any) {
    flash('err', e?.message || '更新失败')
  }
}

async function saveNote(q: InquiryView) {
  try {
    const updated = await adminApi.patchInquiry(q.id, { note: q.note || '' })
    const i = inquiries.value.findIndex((x) => x.id === q.id)
    if (i >= 0) inquiries.value[i] = updated
    flash('ok', '备注已保存')
  } catch (e: any) {
    flash('err', e?.message || '保存失败')
  }
}

async function removeInquiry(q: InquiryView) {
  if (!confirm(`删除来自「${q.name || '匿名'}」的这条需求？`)) return
  try {
    await adminApi.deleteInquiry(q.id)
    inquiries.value = inquiries.value.filter((x) => x.id !== q.id)
    flash('ok', '已删除')
  } catch (e: any) {
    flash('err', e?.message || '删除失败')
  }
}

/* ------------------------------ 模块操作 ------------------------------ */
function newId(type: string) {
  let n = 1
  let id = `${type}${n}`
  const exists = (x: string) => modules.value.some((m) => m.id === x)
  while (exists(id)) id = `${type}${++n}`
  return id
}

function addModule(type: string) {
  if (!content.value) return
  const def = typeDef(type)
  const mod: PortfolioModule = {
    id: newId(type),
    type: type as any,
    visible: true,
    flat: false,
    size: 'auto',
    showNav: true,
    navLabel: def?.label || type,
    eyebrow: '',
    title: def?.label || type,
    lead: ''
  } as PortfolioModule
  if (type === 'stack' || type === 'custom') (mod as any).media = []
  if (type === 'cases') (mod as any).cases = []
  if (type === 'contact') (mod as any).rows = []
  if (type === 'custom') (mod as any).columns = '2'
  content.value.modules.push(mod)
  selectedId.value = mod.id
  flash('ok', `已添加「${def?.label || type}」模块，完善内容后记得保存`)
}

function moveModule(i: number, dir: -1 | 1) {
  const j = i + dir
  if (j < 0 || j >= modules.value.length) return
  const arr = modules.value
  const t = arr[i]
  arr[i] = arr[j]
  arr[j] = t
}

function removeModule(id: string) {
  if (!content.value) return
  if (!confirm(`确定删除模块「${id}」？删除后需要点保存才会生效。`)) return
  content.value.modules = content.value.modules.filter((m) => m.id !== id)
  if (selectedId.value === id) selectedId.value = content.value.modules[0]?.id || ''
}

function changeId(oldId: string, newId: string) {
  if (!content.value) return
  const trimmed = newId.trim()
  if (!trimmed || modules.value.some((m) => m.id === trimmed && m.id !== oldId)) return
  modules.value.forEach((m) => { if (m.id === oldId) m.id = trimmed })
  selectedId.value = trimmed
}

/* ------------------------------ 保存 / 导入导出 ------------------------------ */
async function save() {
  if (!content.value || saving.value) return
  saving.value = true
  try {
    const res = await adminApi.saveContent(content.value)
    flash('ok', `已保存，内容版本 v${res.version}`)
    await Promise.all([loadStatus(), loadRevisions()])
  } catch (e: any) {
    flash('err', e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function exportJson() {
  const blob = new Blob([JSON.stringify(content.value, null, 2)], {
    type: 'application/json'
  })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `content-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(a.href)
}

function importJson(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result))
      if (!parsed.site || !Array.isArray(parsed.modules)) throw new Error('结构不正确')
      content.value = parsed
      selectedId.value = parsed.modules[0]?.id || ''
      flash('ok', '已载入到本地，检查无误后点「保存」')
    } catch (err: any) {
      flash('err', '导入失败：' + (err?.message || '不是合法的内容 JSON'))
    }
  }
  reader.readAsText(f)
  input.value = ''
}

async function rollback(id: number) {
  if (!confirm('回滚到这份快照？当前未保存的改动会被覆盖。')) return
  try {
    const res = await adminApi.rollback(id)
    content.value = await fetchRemote()
    flash('ok', `已回滚，当前版本 v${res.version}`)
    await Promise.all([loadStatus(), loadRevisions()])
  } catch (e: any) {
    flash('err', e?.message || '回滚失败')
  }
}

/* ------------------------------ 素材库 / 账号 ------------------------------ */
const mediaList = ref<any[]>([])
async function loadMedia() {
  try { mediaList.value = await adminApi.listMedia() } catch { /* ignore */ }
}
async function removeMedia(path: string) {
  if (!confirm('删除素材？已发布的页面里引用它的位置会变成占位块。')) return
  try {
    await adminApi.deleteMedia(path)
    await loadMedia()
    flash('ok', '已删除')
  } catch (e: any) {
    flash('err', e?.message || '删除失败')
  }
}

const pw = ref({ old: '', next: '' })
async function changePassword() {
  try {
    await adminApi.changePassword(pw.value.old, pw.value.next)
    pw.value = { old: '', next: '' }
    flash('ok', '密码已更新，下次登录请使用新密码')
  } catch (e: any) {
    flash('err', e?.message || '修改失败')
  }
}

function logout() {
  adminApi.logout().catch(() => {})
  tokenStore.clear()
  router.replace('/login')
}

onMounted(() => { loadMedia() })
</script>

<template>
  <div class="admin">
    <!-- 顶栏 -->
    <header class="admin-bar glass">
      <RouterLink to="/" class="admin-brand">
        <span class="nav__mark">{{ content?.site.brandMark || 'C' }}</span>
        内容后台
      </RouterLink>

      <nav class="admin-tabs">
        <button :class="{ on: tab === 'modules' }" @click="tab = 'modules'">模块</button>
        <button :class="{ on: tab === 'site' }" @click="tab = 'site'">站点信息</button>
        <button :class="{ on: tab === 'media' }" @click="tab = 'media'; loadMedia()">素材库</button>
        <button :class="{ on: tab === 'stats' }" @click="tab = 'stats'; loadStats()">监控</button>
        <button :class="{ on: tab === 'inbox' }" @click="tab = 'inbox'; loadInquiries()">
          工作对接<template v-if="inboxCounts.new"> · {{ inboxCounts.new }} 新</template>
        </button>
        <button :class="{ on: tab === 'account' }" @click="tab = 'account'">账号</button>
      </nav>

      <div class="admin-bar__right">
        <span v-if="status" class="badge" :class="status.redis">
          {{ status.redis === 'up' ? 'Redis 正常' : status.redis === 'down' ? 'Redis 已降级' : 'Redis 关闭' }}
        </span>
        <span v-if="status" class="badge">v{{ status.contentVersion }}</span>
        <button class="mini" @click="exportJson">导出 JSON</button>
        <label class="mini file-btn">
          导入
          <input type="file" accept="application/json" hidden @change="importJson" />
        </label>
        <a class="mini" href="/" target="_blank" rel="noreferrer">查看前台 ↗</a>
        <button class="btn btn-sm" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button class="mini danger" @click="logout">登出</button>
      </div>
    </header>

    <p v-if="notice" class="notice" :class="notice.kind">{{ notice.text }}</p>
    <div v-if="loading" class="admin-empty">载入中…</div>

    <div v-else-if="content" class="admin-body">
      <!-- 模块 tab -->
      <template v-if="tab === 'modules'">
        <aside class="mod-list glass">
          <div class="mod-list__head">
            <b>模块 ({{ modules.length }})</b>
            <select @change="addModule(($event.target as HTMLSelectElement).value); ($event.target as HTMLSelectElement).value = ''">
              <option value="">+ 新增模块</option>
              <option v-for="t in TYPE_DEFS" :key="t.type" :value="t.type">{{ t.label }}</option>
            </select>
          </div>

          <div class="mod-list__items">
            <div
              v-for="(m, i) in modules"
              :key="m.id"
              class="mod-item"
              :class="{ on: selectedId === m.id, off: m.visible === false }"
              @click="selectedId = m.id"
            >
              <span class="dot" :class="{ hidden: m.visible === false }"></span>
              <span class="mod-item__text">
                <b>{{ m.navLabel || m.title || m.id }}</b>
                <i>{{ typeDef(m.type)?.label || m.type }} · {{ m.id }}</i>
              </span>
              <span class="row-tools">
                <button class="mini icon" @click.stop="moveModule(i, -1)" :disabled="i === 0">↑</button>
                <button class="mini icon" @click.stop="moveModule(i, 1)" :disabled="i === modules.length - 1">↓</button>
                <button class="mini danger" @click.stop="removeModule(m.id)">✕</button>
              </span>
            </div>
          </div>

          <p class="small mod-list__tip">
            用 ↑↓ 调整顺序；左侧小圆点变空心表示已从前台隐藏。
          </p>
        </aside>

        <section class="editor glass">
          <div v-if="current" class="editor-inner">
            <div class="editor-head">
              <label class="f inline">
                <span>模块 ID（导航锚点）</span>
                <input :value="current.id" @change="changeId(current.id, ($event.target as HTMLInputElement).value)" />
              </label>
              <label class="f inline">
                <span>类型</span>
                <b class="readonly">{{ currentDef?.label || current.type }}</b>
              </label>
            </div>
            <p v-if="currentDef?.hint" class="small">{{ currentDef.hint }}</p>

            <FieldsEditor :fields="currentDef?.fields || []" :model="current as any" />

            <div v-if="!currentDef" class="raw-json">
              <p class="small">
                这个类型还没写表单描述，下面直接编辑原始数据（前台会用通用方式渲染）。
              </p>
              <textarea
                :value="JSON.stringify(current, null, 2)"
                rows="14"
                @change="
                  Object.assign(
                    current as any,
                    JSON.parse(($event.target as HTMLTextAreaElement).value)
                  )
                "
              ></textarea>
            </div>
          </div>
          <div v-else class="admin-empty">左侧选择一个模块，或新增一个。</div>
        </section>
      </template>

      <!-- 站点 tab -->
      <section v-else-if="tab === 'site'" class="editor glass wide">
        <h2 class="t-md">站点信息</h2>
        <FieldsEditor :fields="SITE_FIELDS" :model="content.site as any" />

        <h2 class="t-md" style="margin-top: 32px">历史快照</h2>
        <p class="small">每次保存都会留一份快照，改错了可以回滚。</p>
        <div v-for="r in revisions" :key="r.id" class="rev-row">
          <span>{{ new Date(r.createdAt).toLocaleString('zh-CN') }}</span>
          <i>{{ r.moduleCount }} 个模块 · {{ Math.round(r.bytes / 1024) }} KB</i>
          <button class="mini" @click="rollback(r.id)">回滚</button>
        </div>
      </section>

      <!-- 素材 tab -->
      <section v-else-if="tab === 'media'" class="editor glass wide">
        <h2 class="t-md">素材库</h2>
        <p class="small">
          素材存在服务器的持久卷里，<b>不在 Git 仓库中</b>，重新部署不会丢。
        </p>
        <div class="media-grid-lib">
          <div v-for="m in mediaList" :key="m.path" class="lib-item">
            <video v-if="m.mime.startsWith('video/')" :src="m.url" muted />
            <img v-else :src="m.url" alt="" />
            <div class="lib-item__bar">
              <i>{{ m.url }}</i>
              <span class="row-tools">
                <a class="mini" :href="m.url" target="_blank" rel="noreferrer">打开</a>
                <button class="mini danger" @click="removeMedia(m.path)">删除</button>
              </span>
            </div>
          </div>
        </div>
        <p v-if="!mediaList.length" class="small">还没有素材，去「模块」里上传。</p>
      </section>

      <!-- 监控 tab -->
      <section v-else-if="tab === 'stats'" class="editor glass wide">
        <div class="sec-head">
          <h2 class="t-md">监控</h2>
          <div class="row-tools">
            <select v-model.number="statsDays" @change="loadStats">
              <option :value="7">近 7 天</option>
              <option :value="14">近 14 天</option>
              <option :value="30">近 30 天</option>
            </select>
            <button class="mini" @click="loadStats">刷新</button>
          </div>
        </div>
        <p class="small">
          只统计匿名会话：IP 只存哈希，不采集身份信息。停留时长由前端心跳累加，关页面时会补最后一拍。
        </p>

        <template v-if="stats">
          <div class="stat-cards">
            <div class="stat-card">
              <b>{{ stats.today.visitors }}</b>
              <span>今日访客</span>
              <i>{{ stats.today.sessions }} 次访问</i>
            </div>
            <div class="stat-card">
              <b>{{ humanDuration(stats.today.avgDurationMs) }}</b>
              <span>平均停留</span>
              <i>单次访问</i>
            </div>
            <div class="stat-card">
              <b>{{ stats.today.plays }}</b>
              <span>作品播放</span>
              <i>{{ stats.today.playedSessions }} 人点开过</i>
            </div>
            <div class="stat-card">
              <b>{{ stats.today.playRate }}%</b>
              <span>播放率</span>
              <i>点开作品 / 总访问</i>
            </div>
            <div class="stat-card">
              <b>{{ stats.inquiry.new }}</b>
              <span>待处理需求</span>
              <i>共 {{ stats.inquiry.total }} 条</i>
            </div>
          </div>

          <h3 class="sub">每日访问</h3>
          <div v-if="stats.series.length" class="bars">
            <div v-for="d in stats.series" :key="d.day" class="bars__col">
              <div class="bars__track">
                <span
                  class="bars__bar"
                  :style="{ height: Math.max(4, Math.round((d.sessions / maxSeries) * 100)) + '%' }"
                  :title="`${d.sessions} 次访问 · ${d.plays} 次播放`"
                ></span>
              </div>
              <i>{{ d.day.slice(5) }}</i>
            </div>
          </div>
          <p v-else class="small">还没有数据，去前台刷几下页面就有了。</p>

          <div class="two-col">
            <div>
              <h3 class="sub">播放最多的作品</h3>
              <div v-for="p in stats.topPlays" :key="p.target" class="play-row">
                <span class="play-row__k">{{ p.target }}</span>
                <b>{{ p.count }}</b>
              </div>
              <p v-if="!stats.topPlays.length" class="small">还没有播放记录。</p>
            </div>

            <div>
              <h3 class="sub">最近访问</h3>
              <div v-for="v in stats.recent" :key="v.id" class="visit-row">
                <span class="mono small">{{ v.startedAt?.slice(11, 19) || '—' }}</span>
                <b>{{ humanDuration(v.durationMs) }}</b>
                <i>{{ v.playCount }} 次播放</i>
                <i>{{ v.ua }}</i>
              </div>
              <p v-if="!stats.recent.length" class="small">暂无记录。</p>
            </div>
          </div>
        </template>
        <p v-else class="small">载入中…</p>
      </section>

      <!-- 工作对接 tab -->
      <section v-else-if="tab === 'inbox'" class="editor glass wide">
        <div class="sec-head">
          <h2 class="t-md">工作对接</h2>
          <div class="row-tools">
            <button class="mini" @click="loadInquiries">刷新</button>
          </div>
        </div>
        <p class="small">网站底部提交的项目需求都进这里，标记状态、写内部备注、处理完归档。</p>

        <div class="filter-row">
          <button
            v-for="f in (['all', 'new', 'contacted', 'archived'] as const)"
            :key="f"
            class="mini"
            :class="{ on: inboxFilter === f }"
            @click="inboxFilter = f"
          >
            {{ f === 'all' ? '全部' : STATUS_LABEL[f] }} ({{ inboxCounts[f] }})
          </button>
        </div>

        <div v-for="q in filteredInquiries" :key="q.id" class="inbox-item" :class="`is-${q.status}`">
          <div class="inbox-item__head">
            <b>{{ q.name || '匿名' }}</b>
            <i>{{ q.contact }}</i>
            <span v-if="q.type" class="tag">{{ q.type }}</span>
            <span v-if="q.budget" class="tag tag--ghost">{{ q.budget }}</span>
            <span class="tag tag--status">{{ STATUS_LABEL[q.status] }}</span>
            <span class="small push">{{ q.createdAt ? new Date(q.createdAt).toLocaleString('zh-CN') : '' }}</span>
          </div>

          <p class="body">{{ q.message }}</p>

          <div class="inbox-item__foot">
            <input
              v-model="q.note"
              class="note-input"
              placeholder="内部备注（只有你能看到），写完点「存备注」"
              @keyup.enter="saveNote(q)"
            />
            <span class="row-tools">
              <button class="mini" @click="saveNote(q)">存备注</button>
              <button v-if="q.status !== 'contacted'" class="mini" @click="setStatus(q, 'contacted')">标为已联系</button>
              <button v-if="q.status !== 'archived'" class="mini" @click="setStatus(q, 'archived')">归档</button>
              <button v-if="q.status !== 'new'" class="mini" @click="setStatus(q, 'new')">退回未读</button>
              <button class="mini danger" @click="removeInquiry(q)">删除</button>
            </span>
          </div>
          <p class="small inbox-item__ip">来源 IP {{ q.ip }} · ID {{ q.id }}</p>
        </div>

        <p v-if="!filteredInquiries.length" class="small">
          {{ inboxFilter === 'all' ? '还没有收到需求。' : '这个状态下没有内容。' }}
        </p>
      </section>

      <!-- 账号 tab -->
      <section v-else class="editor glass narrow">
        <h2 class="t-md">修改密码</h2>
        <label class="f">
          <span>当前密码</span>
          <input v-model="pw.old" type="password" />
        </label>
        <label class="f">
          <span>新密码（至少 8 位）</span>
          <input v-model="pw.next" type="password" />
        </label>
        <button class="btn" @click="changePassword" :disabled="!pw.old || !pw.next">更新密码</button>

        <div class="status-box">
          <div v-if="status">
            <span>内容版本</span><b>v{{ status.contentVersion }}</b>
          </div>
          <div v-if="status">
            <span>素材数量</span><b>{{ status.mediaCount }}</b>
          </div>
          <div v-if="status">
            <span>单文件上限</span><b>{{ status.maxUploadMB }} MB</b>
          </div>
          <div v-if="status">
            <span>上传目录</span><b class="mono small">{{ status.uploadDir }}</b>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
