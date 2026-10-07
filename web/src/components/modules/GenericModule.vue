<script setup lang="ts">
/**
 * 兜底渲染器：任何后端没专门支持的 type 都走这里。
 * 只要有 body / media / facts / rows 这类通用字段就能显示，
 * 因此以后新增模块类型时，前台不需要同步改代码也不会白屏。
 */
import MediaFrame from '../MediaFrame.vue'
import { mediaList } from '@/util/content'
import type { PortfolioModule } from '@/types/content'

const props = defineProps<{ module: PortfolioModule }>()
const m = () => props.module as Record<string, any>
const list = () => mediaList(m().media)
</script>

<template>
  <div>
    <div v-if="m().body" class="textblock" v-html="m().body"></div>

    <p
      v-for="(p, i) in m().paragraphs || []"
      :key="'p' + i"
      class="body"
      style="margin-bottom: 14px"
      v-html="p"
    ></p>

    <div v-if="list().length" class="grid" style="margin-top: 20px; padding: 0">
      <div
        v-for="(md, i) in list()"
        :key="i"
        class="mod"
        :style="{ '--span': list().length > 2 ? 4 : 6 }"
      >
        <MediaFrame :item="md" />
      </div>
    </div>

    <dl v-if="m().rows?.length" class="rows">
      <div v-for="(r, i) in m().rows" :key="i" class="row-i">
        <dt>{{ r.k }}</dt>
        <dd>{{ r.v }}</dd>
      </div>
    </dl>

    <div v-if="m().skills?.length" class="chip-row" style="margin-top: 18px">
      <span v-for="(s, i) in m().skills" :key="i" class="chip chip--flat">{{ s }}</span>
    </div>
  </div>
</template>
