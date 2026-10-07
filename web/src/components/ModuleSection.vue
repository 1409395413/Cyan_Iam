<script setup lang="ts">
/**
 * 模块外壳：12 栅格 + grid-auto-flow: dense。
 *
 * 模块自己不知道占几列 —— size 为空时按内容量推断（auto）。
 * 所以后台新增任何模块，版面都会自动给它安排位置，不会留空洞。
 */
import { computed } from 'vue'
import HeroModule from './modules/HeroModule.vue'
import AboutModule from './modules/AboutModule.vue'
import StackModule from './modules/StackModule.vue'
import CasesModule from './modules/CasesModule.vue'
import QuoteModule from './modules/QuoteModule.vue'
import ContactModule from './modules/ContactModule.vue'
import TextModule from './modules/TextModule.vue'
import CustomModule from './modules/CustomModule.vue'
import GenericModule from './modules/GenericModule.vue'
import { mediaList } from '@/util/content'
import type { PortfolioModule, SizeToken } from '@/types/content'

const props = defineProps<{ module: PortfolioModule }>()

const SPAN: Record<SizeToken, number> = { auto: 0, sm: 3, md: 4, lg: 6, xl: 8, full: 12 }

function autoSpan(m: PortfolioModule): number {
  const anyM = m as any
  const c = mediaList(anyM.media).length
  switch (m.type) {
    case 'hero':
    case 'quote':
    case 'about':
    case 'contact':
      return 12
    case 'stack':
      return c >= 5 ? 12 : c >= 3 ? 8 : 6
    case 'cases':
      return (anyM.cases?.length || 0) > 1 ? 12 : 8
    case 'text':
      return String(anyM.body || '').length > 320 ? 8 : 6
    case 'custom': {
      // 素材越多越宽；纯文字就给窄栏，避免一行字拉满整屏
      const cols = Number(anyM.columns || 1)
      if (c === 0) return String(anyM.body || '').length > 320 ? 8 : 6
      if (c >= 3 && cols >= 3) return 12
      if (c >= 3 || cols >= 2) return 8
      return 6
    }
    default:
      return c > 3 ? 12 : 6
  }
}

const span = computed(() => {
  const s = (props.module.size || 'auto') as SizeToken
  return s === 'auto' ? autoSpan(props.module) : SPAN[s] || 6
})

const bodyComponent = computed(() => {
  switch (props.module.type) {
    case 'hero': return HeroModule
    case 'about': return AboutModule
    case 'stack': return StackModule
    case 'cases': return CasesModule
    case 'quote': return QuoteModule
    case 'contact': return ContactModule
    case 'text': return TextModule
    case 'custom': return CustomModule
    default: return GenericModule
  }
})

const isQuote = computed(() => props.module.type === 'quote')
const isHero = computed(() => props.module.type === 'hero')
const showHead = computed(() => !isHero.value && !isQuote.value)
const actions = computed(() => (props.module as any).actions || [])
</script>

<template>
  <section
    :id="props.module.id"
    class="mod"
    :class="[
      props.module.flat ? 'mod--flat' : 'glass reveal',
      'mod--' + props.module.type,
      { hero: isHero }
    ]"
    :style="{ '--span': span }"
  >
    <div class="mod__inner" :class="{ quote: isQuote }">
      <div v-if="showHead && (props.module.eyebrow || props.module.title || props.module.lead)" class="mod__head">
        <p v-if="props.module.eyebrow" class="eyebrow">{{ props.module.eyebrow }}</p>
        <template v-if="props.module.title">
          <div class="rule"></div>
          <h2 class="t-lg" v-html="props.module.title"></h2>
        </template>
        <p v-if="props.module.lead" class="lead" v-html="props.module.lead"></p>
      </div>

      <component :is="bodyComponent" :module="props.module" />

      <div v-if="actions.length && props.module.type !== 'hero'" class="btn-row" style="margin-top: 24px">
        <a
          v-for="a in actions"
          :key="a.label"
          class="btn"
          :class="{ 'btn--ghost': a.kind !== 'primary' }"
          :href="a.href || '#'"
        >{{ a.label }}</a>
      </div>
    </div>
  </section>
</template>
