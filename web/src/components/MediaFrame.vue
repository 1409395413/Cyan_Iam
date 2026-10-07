<script setup lang="ts">
/**
 * 素材帧：图片 / 视频 / 缺失占位，三种形态用同一个盒子。
 * ---------------------------------------------------------------------------
 * 横竖屏兼容的核心在这里：
 *   · 卡牌态（uniform）—— 不管素材是 9:16 还是 16:9，都套同一个画框，
 *                          一排牌看下来视觉重量一致，不会因为一张竖图把行高撑乱；
 *   · 播放态（native）—— 点开后按素材真实比例展开，竖的变高、横的变宽；
 *   · 尺寸未知时，图片 onload / 视频 loadedmetadata 会当场探测并回填 w/h，
 *     所以"传上来什么尺寸"都能自动选对画框，不需要手工填。
 */
import { computed, ref } from 'vue'
import type { MediaItem } from '@/types/content'
import { frameOf, orientationOf } from '@/util/content'

const props = withDefaults(
  defineProps<{
    item?: MediaItem | null
    /** 兜底比例类：r-16-9 / r-4-3 / r-3-4 / r-1-1 / r-21-9 */
    ratio?: string
    /** true = 用统一画框（叠牌画廊的牌面） */
    uniform?: boolean
    /** 大号播放键 */
    big?: boolean
    /** 静音循环自动播放（首屏 Showreel 用） */
    autoplay?: boolean
    controls?: boolean
  }>(),
  { ratio: '', uniform: false, big: false, autoplay: false, controls: false }
)

const item = computed(() => props.item || null)
const hasSrc = computed(() => !!item.value?.src)
const isVideo = computed(() => item.value?.kind === 'video')

/** 已探测到的真实宽高（优先用内容里存的值，其次用运行时探测） */
const probed = ref<{ w: number; h: number } | null>(null)

const effW = computed(() => item.value?.w || probed.value?.w || 0)
const effH = computed(() => item.value?.h || probed.value?.h || 0)
const orientation = computed(() => orientationOf(effW.value, effH.value))

/** 探测到尺寸后回填到内容对象上，下次保存就会固化进数据库 */
function remember(w: number, h: number) {
  if (!w || !h) return
  probed.value = { w, h }
  const it = item.value
  if (it && (!it.w || !it.h)) {
    it.w = w
    it.h = h
  }
}

function onImgLoad(e: Event) {
  const el = e.target as HTMLImageElement
  remember(el.naturalWidth, el.naturalHeight)
}

function onMeta(e: Event) {
  const el = e.target as HTMLVideoElement
  remember(el.videoWidth, el.videoHeight)
}

const frameStyle = computed(() => {
  const f = frameOf(item.value, props.uniform)
  return { aspectRatio: f }
})

/** 没探测到尺寸、也没手填时，用调用方给的比例类兜底 */
const fallbackCls = computed(() =>
  props.ratio || (isVideo.value ? 'r-16-9' : 'r-4-3')
)
const useKnownFrame = computed(() => effW.value > 0 && effH.value > 0 ||
  (item.value?.fit && item.value.fit !== 'auto'))
</script>

<template>
  <div
    class="ph media-frame"
    :class="[hasSrc ? 'is-real' : '', useKnownFrame ? '' : fallbackCls, `is-${orientation}`]"
    :style="useKnownFrame ? frameStyle : undefined"
  >
    <span v-if="item?.badge" class="tag-res">{{ item.badge }}</span>
    <span v-if="item?.dur" class="tag-dur">{{ item.dur }}</span>

    <video
      v-if="isVideo && hasSrc"
      :src="item!.src"
      :poster="item!.poster || undefined"
      playsinline
      preload="metadata"
      :controls="props.controls"
      :autoplay="props.autoplay"
      :loop="props.autoplay"
      :muted="props.autoplay"
      @loadedmetadata="onMeta"
    ></video>

    <img
      v-else-if="hasSrc"
      :src="item!.src"
      :alt="item!.title || ''"
      loading="lazy"
      decoding="async"
      @load="onImgLoad"
    />

    <div v-else class="ph__label">
      <span v-if="isVideo" class="play" :class="{ 'play--sm': !props.big }">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
      </span>
      <svg v-else class="ph__ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4">
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <circle cx="8.5" cy="9.5" r="2" />
        <path d="M21 16l-4.5-4.5L8 20" />
      </svg>
      {{ item?.title || item?.label || '素材占位' }}
      <small v-if="item?.hint">{{ item.hint }}</small>
      <small v-else>后台上传后自动替换</small>
    </div>
  </div>
</template>

<style scoped>
/* 注意：这里绝不写死浅色背景 —— 之前写死过 linear-gradient(#e7e7ec,#d3d4dc)，
   会把深色主题下 .ph 的深色底盖掉，导致图片/视频框在深色模式下变成一块块白卡片。 */
.is-real {
  border-color: transparent;
}
.ph video,
.ph img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}
/* 竖屏素材在统一画框里靠上裁切，避免把人脸/主体切掉 */
.is-portrait img,
.is-portrait video {
  object-position: center 35%;
}
.ph.is-real:deep(.tag-res),
.ph.is-real:deep(.tag-dur) {
  z-index: 3;
}
</style>
