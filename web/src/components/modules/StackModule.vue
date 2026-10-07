<script setup lang="ts">
/** 作品陈列：走统一的叠牌画廊。 */
import StackGallery from '../StackGallery.vue'
import { mediaList } from '@/util/content'
import type { StackModule } from '@/types/content'

const props = defineProps<{ module: StackModule }>()
</script>

<template>
  <StackGallery :items="mediaList(props.module.media)" />
</template>
