<script setup lang="ts">
/** 纯文本段落。 */
import { rich } from '@/util/content'
import type { TextModule } from '@/types/content'

const props = defineProps<{ module: TextModule }>()
</script>

<template>
  <div class="textblock" v-html="rich(props.module.body)"></div>
</template>
