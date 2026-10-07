<script setup lang="ts">
/**
 * 单个素材条目：上传 / 直接填外链 / 补全标题等展示信息。
 *
 * 上传或粘贴外链后会立刻探测真实像素并回填 w/h —— 前台据此判断横竖屏，
 * 决定是走统一画框还是按原始比例展开，所以"传什么尺寸都能自己兼容"。
 */
import { ref } from 'vue'
import { adminApi } from '@/api'
import type { MediaItem } from '@/types/content'
import { describeMedia, orientationOf } from '@/util/content'

const props = defineProps<{ item: MediaItem }>()
const emit = defineEmits<{ remove: [] }>()

const file = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const error = ref('')

/** 读真实像素：图片用 Image，视频用 <video> 的 loadedmetadata */
function probe(src: string, kind: string) {
  return new Promise<{ w: number; h: number } | null>((resolve) => {
    if (!src) return resolve(null)
    const done = (w: number, h: number) => resolve(w && h ? { w, h } : null)
    const fail = () => resolve(null)

    if (kind === 'video') {
      const v = document.createElement('video')
      v.preload = 'metadata'
      v.muted = true
      v.onloadedmetadata = () => done(v.videoWidth, v.videoHeight)
      v.onerror = fail
      v.src = src
      setTimeout(fail, 8000)
      return
    }
    const img = new Image()
    img.onload = () => done(img.naturalWidth, img.naturalHeight)
    img.onerror = fail
    img.src = src
    setTimeout(fail, 8000)
  })
}

async function applyMeta(src: string, kind: string) {
  const dim = await probe(src, kind)
  if (dim) {
    props.item.w = dim.w
    props.item.h = dim.h
  }
}

async function pick(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  uploading.value = true
  error.value = ''
  try {
    const res = await adminApi.upload(f)
    props.item.src = res.url
    props.item.kind = res.mime.startsWith('video/') ? 'video' : 'image'
    await applyMeta(res.url, props.item.kind)
  } catch (err: any) {
    error.value = err?.message || '上传失败'
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function onSrcChange(v: string) {
  if (v) await applyMeta(v, props.item.kind)
}

const orientText = () => {
  const o = orientationOf(props.item.w, props.item.h)
  return o === 'portrait' ? '竖屏' : o === 'landscape' ? '横屏' : o === 'square' ? '方图' : ''
}
</script>

<template>
  <div class="media-card">
    <div class="media-thumb">
      <video v-if="item.kind === 'video' && item.src" :src="item.src" muted playsinline></video>
      <img v-else-if="item.src" :src="item.src" alt="" />
      <span v-else class="media-ph">{{ item.kind === 'video' ? '视频' : '图片' }}</span>
    </div>

    <div class="media-main">
      <div class="media-actions">
        <button class="mini" :disabled="uploading" @click="file?.click()">
          {{ uploading ? '上传中…' : '上传本地文件' }}
        </button>
        <input ref="file" type="file" accept="image/*,video/*" hidden @change="pick" />
        <input
          class="mini-input"
          v-model="item.src"
          placeholder="或粘贴外链 URL"
          @change="onSrcChange(item.src || '')"
        />
        <button class="mini danger" @click="emit('remove')">删除</button>
      </div>

      <div class="media-meta-row">
        <span class="media-dim">
          {{ describeMedia(item) }}
          <i v-if="orientText()">{{ orientText() }}</i>
        </span>
        <span class="small">画框策略决定它和横屏素材并排时怎么被裁切</span>
        <button class="mini" @click="applyMeta(item.src || '', item.kind)">重新探测尺寸</button>
      </div>

      <div class="media-grid">
        <label>
          <span>类型</span>
          <select v-model="item.kind">
            <option value="image">图片</option>
            <option value="video">视频</option>
          </select>
        </label>
        <label>
          <span>标题</span>
          <input v-model="item.title" />
        </label>
        <label>
          <span>副标题</span>
          <input v-model="item.meta" />
        </label>
        <label>
          <span>角标</span>
          <input v-model="item.badge" />
        </label>
        <label>
          <span>时长</span>
          <input v-model="item.dur" />
        </label>
        <label>
          <span>占位提示</span>
          <input v-model="item.hint" />
        </label>
        <label>
          <span>画框</span>
          <select v-model="item.fit">
            <option value="auto">自动（按真实方向）</option>
            <option value="uniform">统一画框</option>
            <option value="native">原始比例</option>
          </select>
        </label>
        <label>
          <span>宽 px</span>
          <input v-model.number="item.w" type="number" min="0" />
        </label>
        <label>
          <span>高 px</span>
          <input v-model.number="item.h" type="number" min="0" />
        </label>
      </div>

      <p v-if="error" class="media-error">{{ error }}</p>
    </div>
  </div>
</template>

<style scoped>
.media-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.media-dim {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: var(--r-full);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.media-dim i { font-style: normal; opacity: .75; }
</style>
