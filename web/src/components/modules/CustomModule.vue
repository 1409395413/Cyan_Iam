<script setup lang="ts">
/**
 * 自定义模块。
 * ---------------------------------------------------------------------------
 * 后台选「自定义内容」后可以自由组合：富文本正文 + 素材网格（1/2/3 列）+ 参数表。
 * 素材按真实方向排版 —— 竖屏和横屏混着放也不会互相挤变形。
 */
import { computed } from 'vue'
import MediaFrame from '../MediaFrame.vue'
import { mediaList, rich } from '@/util/content'
import type { CustomModule } from '@/types/content'

const props = defineProps<{ module: CustomModule }>()

const media = computed(() => mediaList(props.module.media))
const cols = computed(() => {
  const c = Number(props.module.columns || 1)
  return c === 3 ? 3 : c === 2 ? 2 : 1
})
const body = computed(() => rich(props.module.body))
const facts = computed(() => props.module.facts || [])
</script>

<template>
  <div class="custom reveal">
    <div v-if="body" class="textblock custom__body" v-html="body"></div>

    <div v-if="media.length" class="custom__grid" :class="`cols-${cols}`">
      <figure v-for="(m, i) in media" :key="m.src || i" class="custom__cell">
        <MediaFrame :item="m" />
        <figcaption v-if="m.title || m.meta">
          <b>{{ m.title }}</b>
          <span v-if="m.meta">{{ m.meta }}</span>
        </figcaption>
      </figure>
    </div>

    <dl v-if="facts.length" class="specs custom__facts">
      <div v-for="f in facts" :key="f.k" class="spec">
        <dt>{{ f.k }}</dt>
        <dd>{{ f.v }}</dd>
      </div>
    </dl>
  </div>
</template>

<style scoped>
.custom {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.custom__body {
  max-width: none;
}
.custom__grid {
  display: grid;
  gap: 14px;
}
.custom__grid.cols-1 {
  grid-template-columns: 1fr;
}
.custom__grid.cols-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.custom__grid.cols-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.custom__cell {
  margin: 0;
  min-width: 0;
}
.custom__cell figcaption {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 2px 0;
}
.custom__cell figcaption b {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  letter-spacing: -0.01em;
}
.custom__cell figcaption span {
  font-size: 12px;
  color: var(--ink-3);
}
.custom__facts {
  margin: 0;
}
@media (max-width: 700px) {
  .custom__grid.cols-3 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .custom__grid.cols-2,
  .custom__grid.cols-3 {
    gap: 10px;
  }
}
@media (max-width: 460px) {
  .custom__grid.cols-3,
  .custom__grid.cols-2 {
    grid-template-columns: 1fr;
  }
}
</style>
