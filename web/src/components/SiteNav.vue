<script setup lang="ts">
/**
 * 悬浮玻璃导航：锚点跳转 + 滚动高亮，移动端折叠。
 *
 * 品牌标（Cc）同时是一个"隐藏入口"：看起来就是个 logo，
 * 鼠标靠近才浮出一圈微光，点一下进后台登录。
 * 不写字、不提示，只有自己知道。
 */
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { PortfolioModule, SiteConfig } from '@/types/content'

const props = defineProps<{
  site: SiteConfig
  modules: PortfolioModule[]
  activeId: string
}>()

const open = ref(false)
const navModules = computed(() => props.modules.filter((m) => m.showNav))

function go(id: string) {
  open.value = false
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="nav">
    <div class="nav__pill" :class="{ 'is-open': open }">
      <div class="nav__brand">
        <RouterLink
          class="nav__mark nav__mark--secret"
          to="/login"
          aria-label="管理入口"
        >{{ site.brandMark }}</RouterLink>
        <a class="nav__brandtext" href="#" @click.prevent="go(modules[0]?.id || '')">
          {{ site.brandName }}
        </a>
      </div>

      <a
        v-for="m in navModules"
        :key="m.id"
        class="nav__link"
        :class="{ 'is-active': activeId === m.id }"
        :href="'#' + m.id"
        @click.prevent="go(m.id)"
      >{{ m.navLabel || m.title }}</a>

      <button class="nav__burger" aria-label="菜单" @click="open = !open">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path v-if="!open" d="M3 6h18M3 12h18M3 18h18" />
          <path v-else d="M5 5l14 14M19 5L5 19" />
        </svg>
      </button>
    </div>
  </div>
</template>
