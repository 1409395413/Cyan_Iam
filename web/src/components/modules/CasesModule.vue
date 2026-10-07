<script setup lang="ts">
/** 案例：图文左右分栏 + 参数表 + 标签。 */
import MediaFrame from '../MediaFrame.vue'
import type { CasesModule } from '@/types/content'

const props = defineProps<{ module: CasesModule }>()
</script>

<template>
  <div>
    <div v-for="(c, i) in props.module.cases || []" :key="i" class="case reveal">
      <div>
        <MediaFrame :item="c.media" ratio="r-16-9" />
      </div>
      <div>
        <h3 class="t-md">{{ c.title }}</h3>
        <p v-if="c.desc" class="body" style="margin-top: 12px">{{ c.desc }}</p>

        <dl v-if="c.specs?.length" class="specs">
          <div v-for="s in c.specs" :key="s.k" class="spec">
            <dt>{{ s.k }}</dt>
            <dd>{{ s.v }}</dd>
          </div>
        </dl>

        <div v-if="c.tags?.length" class="chip-row" style="margin-top: 18px">
          <span v-for="t in c.tags" :key="t" class="chip chip--flat">{{ t }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
