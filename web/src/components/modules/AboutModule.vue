<script setup lang="ts">
/** 个人简介：竖版形象照 + 履历段落 + 履历表 + 能力标签。 */
import MediaFrame from '../MediaFrame.vue'
import { mediaList } from '@/util/content'
import type { AboutModule } from '@/types/content'

const props = defineProps<{ module: AboutModule }>()
const first = () => mediaList(props.module.media)[0] || null
</script>

<template>
  <div class="about reveal">
    <div class="about__frame glass">
      <MediaFrame :item="first()" ratio="r-3-4" />
      <div class="about__cap">
        <span>{{ first()?.cap || '' }}</span>
        <span>{{ first()?.cap2 || '' }}</span>
      </div>
    </div>

    <div>
      <p v-for="(p, i) in props.module.paragraphs || []" :key="i" class="body about__p" v-html="p"></p>

      <div v-if="props.module.facts?.length" class="about__facts">
        <div v-for="f in props.module.facts" :key="f.k" class="fact">
          <b>{{ f.k }}</b>
          <span>{{ f.v }}</span>
        </div>
      </div>

      <template v-if="props.module.skills?.length">
        <p class="eyebrow" style="margin: 22px 0 10px">能力关键词</p>
        <div class="chip-row">
          <span v-for="s in props.module.skills" :key="s" class="chip chip--flat">{{ s }}</span>
        </div>
      </template>
    </div>
  </div>
</template>
