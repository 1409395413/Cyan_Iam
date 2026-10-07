<script setup lang="ts">
/** 首屏横幅：左侧标题 + CTA，右侧 Showreel，底部数据条。 */
import MediaFrame from '../MediaFrame.vue'
import { rich } from '@/util/content'
import type { HeroModule } from '@/types/content'

const props = defineProps<{ module: HeroModule }>()
</script>

<template>
  <div class="hero__grid reveal">
    <div>
      <p v-if="props.module.eyebrow" class="eyebrow">{{ props.module.eyebrow }}</p>
      <h1 class="t-mega hero__title" v-html="rich(props.module.title)"></h1>
      <p v-if="props.module.lead" class="lead hero__lead" v-html="rich(props.module.lead)"></p>

      <div v-if="props.module.chips?.length" class="chip-row" style="margin-top: 26px">
        <span v-for="c in props.module.chips" :key="c" class="chip">{{ c }}</span>
      </div>

      <div v-if="props.module.actions?.length" class="btn-row" style="margin-top: 24px">
        <a
          v-for="a in props.module.actions"
          :key="a.label"
          class="btn"
          :class="{ 'btn--ghost': a.kind !== 'primary' }"
          :href="a.href || '#'"
        >{{ a.label }}</a>
      </div>
    </div>

    <div class="reel__shell glass reveal">
      <div class="reel__bar">
        <span>{{ props.module.reel?.bar || 'Showreel' }}</span>
        <span class="reel__dots"><i></i><i></i><i></i></span>
      </div>
      <MediaFrame :item="props.module.reel?.media" ratio="r-16-9" autoplay big />
    </div>
  </div>

  <div v-if="props.module.stats?.length" class="hero__stats">
    <div v-for="s in props.module.stats" :key="s.label" class="stat reveal">
      <b class="mono">{{ s.value }}</b>
      <span>{{ s.label }}</span>
    </div>
  </div>
</template>
