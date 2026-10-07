<script setup lang="ts">
/** 金句通栏。 */
import { rich } from '@/util/content'
import type { QuoteModule } from '@/types/content'

const props = defineProps<{ module: QuoteModule }>()
</script>

<template>
  <div>
    <h2 class="t-lg" v-html="rich(props.module.title)"></h2>
    <p v-if="props.module.lead" class="lead" v-html="rich(props.module.lead)"></p>
  </div>
</template>
