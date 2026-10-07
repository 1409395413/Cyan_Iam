<script setup lang="ts">
/**
 * 首页：内容全部来自 GET /api/content。
 * 模块顺序、显示与否、每个模块的文案与素材都由后台决定，改完即时生效 —— 不需要重新构建或部署。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchContent } from '@/api'
import SiteNav from '@/components/SiteNav.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import ModuleSection from '@/components/ModuleSection.vue'
import { initTracking, stopTracking } from '@/util/tracking'
import type { SiteContent } from '@/types/content'

const content = ref<SiteContent | null>(null)
const loading = ref(true)
const error = ref('')
const activeId = ref('')

const visibleModules = computed(
  () => (content.value?.modules || []).filter((m) => m.visible !== false)
)

/* ---------- 滚动高亮当前板块 + 导航收展 ---------- */
// Apple 的做法：向下滚动时导航栏让位给内容（收窄），向上滚一点就立刻回来。
let lastY = 0
function onScroll() {
  const y = window.scrollY

  const mid = y + window.innerHeight / 3
  let current = visibleModules.value[0]?.id || ''
  for (const m of visibleModules.value) {
    const el = document.getElementById(m.id)
    if (el && el.offsetTop <= mid) current = m.id
  }
  activeId.value = current

  const root = document.documentElement
  if (y > 160 && y > lastY + 6) root.classList.add('nav-compact')
  else if (y < lastY - 6 || y < 80) root.classList.remove('nav-compact')
  lastY = y
}

/* ---------- 入场动画 ---------- */
let io: IntersectionObserver | null = null
function setupReveal() {
  io?.disconnect()
  io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          e.target.classList.add('is-in')
          io?.unobserve(e.target)
        }
      }
    },
    { threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
  )
  document.querySelectorAll('.reveal').forEach((el) => io?.observe(el))
}

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })
  initTracking()
  try {
    content.value = await fetchContent()
    await nextTick()
    setupReveal()
    onScroll()
  } catch (e: any) {
    error.value = e?.message || '内容加载失败'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  io?.disconnect()
  document.documentElement.classList.remove('nav-compact')
  stopTracking()
})
</script>

<template>
  <template v-if="content">
    <SiteNav :site="content.site" :modules="visibleModules" :active-id="activeId" />
  </template>

  <main v-if="content" class="page">
    <div class="grid">
      <ModuleSection v-for="m in visibleModules" :key="m.id" :module="m" />
    </div>
    <SiteFooter v-if="content" :site="content.site" />
  </main>

  <div v-else-if="loading" class="state-box">正在载入内容…</div>
  <div v-else class="state-box error">
    <p>{{ error }}</p>
    <p class="hint">确认后端已启动，并且 <code>VITE_API_BASE</code> 指向正确。</p>
  </div>
</template>

<style scoped>
.state-box {
  padding: 25vh 24px;
  text-align: center;
  color: var(--ink-2);
  font-size: 17px;
}
.state-box.error { color: #d70015; }
.state-box .hint { margin-top: 10px; font-size: 13px; color: var(--ink-3); }
.state-box code {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, .06);
  font-size: 12px;
}
</style>
