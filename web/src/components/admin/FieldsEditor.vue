<script setup lang="ts">
/**
 * 通用表单渲染器 —— 后台所有编辑界面都由 moduleSchema 驱动生成。
 * 递归处理 group / list，所以嵌套结构（按钮组、案例列表、页脚分栏）都不需要手写组件。
 */
import MediaItemEditor from './MediaItemEditor.vue'
import type { FieldDef } from '@/moduleSchema'
import type { MediaItem } from '@/types/content'

const props = defineProps<{
  fields: FieldDef[]
  /** 直接就地修改传入的对象（它是 reactive 的） */
  model: Record<string, any>
}>()

// 显式命名，才能在本组件模板里递归自引用
defineOptions({ name: 'FieldsEditor' })

const flatFields = () => props.fields

/* ---------- 值读写 ---------- */
function ensure(key: string, init: any) {
  if (props.model[key] === undefined || props.model[key] === null) props.model[key] = init
  return props.model[key]
}

function toList(key: string): any[] {
  return ensure(key, [])
}
function toObject(key: string): Record<string, any> {
  return ensure(key, {})
}
function linesToArray(key: string, text: string) {
  props.model[key] = text
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}
function arrayToLines(key: string): string {
  const v = props.model[key]
  return Array.isArray(v) ? v.join('\n') : ''
}

/* ---------- 列表操作 ---------- */
function addItem(f: FieldDef) {
  const item: Record<string, any> = {}
  for (const sub of f.fields || []) {
    if (sub.type === 'kv' || sub.type === 'stringlist') item[sub.key] = []
    else if (sub.type === 'list') item[sub.key] = []
    else if (sub.type === 'media') item[sub.key] = { kind: 'image', src: '', title: '' }
    else if (sub.type === 'select') item[sub.key] = sub.options?.[0]?.v ?? ''
    else if (sub.type === 'switch') item[sub.key] = false
    else item[sub.key] = ''
  }
  toList(f.key).push(item)
}

function addMedia(key: string) {
  toList(key).push({ kind: 'image', src: '', title: '' } as MediaItem)
}

function move(arr: any[], i: number, dir: -1 | 1) {
  const j = i + dir
  if (j < 0 || j >= arr.length) return
  const t = arr[i]
  arr[i] = arr[j]
  arr[j] = t
}

function remove(arr: any[], i: number) {
  arr.splice(i, 1)
}
</script>

<template>
  <div class="fields">
    <div v-for="f in flatFields()" :key="f.key" class="field-block" :class="'fb-' + f.type">
      <!-- 文本 / 富文本 / 下拉 -->
      <label v-if="f.type === 'text'" class="f">
        <span>{{ f.label }}</span>
        <input v-model="model[f.key]" :placeholder="f.hint" />
      </label>

      <label v-else-if="f.type === 'textarea'" class="f">
        <span>{{ f.label }}</span>
        <textarea v-model="model[f.key]" :placeholder="f.hint" rows="3"></textarea>
      </label>

      <label v-else-if="f.type === 'richtext'" class="f">
        <span>{{ f.label }}<i class="f-note">支持少量 HTML</i></span>
        <textarea v-model="model[f.key]" :placeholder="f.hint" rows="2"></textarea>
      </label>

      <label v-else-if="f.type === 'select'" class="f">
        <span>{{ f.label }}</span>
        <select v-model="model[f.key]">
          <option v-for="o in f.options || []" :key="o.v" :value="o.v">{{ o.label }}</option>
        </select>
      </label>

      <label v-else-if="f.type === 'switch'" class="f f-switch">
        <span>{{ f.label }}</span>
        <input v-model="model[f.key]" type="checkbox" />
      </label>

      <!-- 字符串数组：一行一项 -->
      <label v-else-if="f.type === 'stringlist'" class="f">
        <span>{{ f.label }}<i class="f-note">一行一项</i></span>
        <textarea
          :value="arrayToLines(f.key)"
          rows="4"
          :placeholder="f.hint"
          @input="linesToArray(f.key, ($event.target as HTMLTextAreaElement).value)"
        ></textarea>
      </label>

      <!-- 键值对 -->
      <div v-else-if="f.type === 'kv'" class="f">
        <div class="f-head">
          <span>{{ f.label }}</span>
          <button class="mini" @click="toList(f.key).push({ k: '', v: '' })">+ 添加</button>
        </div>
        <div v-for="(row, i) in toList(f.key)" :key="i" class="kv-row">
          <input v-model="row.k" placeholder="名称" />
          <input v-model="row.v" placeholder="内容" />
          <button class="mini icon" @click="move(toList(f.key), i, -1)" :disabled="i === 0">↑</button>
          <button class="mini icon" @click="move(toList(f.key), i, 1)" :disabled="i === toList(f.key).length - 1">↓</button>
          <button class="mini danger" @click="remove(toList(f.key), i)">✕</button>
        </div>
      </div>

      <!-- 单个素材 -->
      <div v-else-if="f.type === 'media'" class="f">
        <div class="f-head"><span>{{ f.label }}</span></div>
        <MediaItemEditor :item="toObject(f.key) as MediaItem" />
      </div>

      <!-- 素材列表 -->
      <div v-else-if="f.type === 'medialist'" class="f">
        <div class="f-head">
          <span>{{ f.label }}<i class="f-note">{{ toList(f.key).length }} 项</i></span>
          <button class="mini" @click="addMedia(f.key)">+ 添加素材</button>
        </div>
        <div v-for="(item, i) in toList(f.key)" :key="i" class="media-row">
          <MediaItemEditor :item="item" />
          <div class="row-tools">
            <button class="mini icon" @click="move(toList(f.key), i, -1)" :disabled="i === 0">↑</button>
            <button class="mini icon" @click="move(toList(f.key), i, 1)" :disabled="i === toList(f.key).length - 1">↓</button>
            <button class="mini danger" @click="remove(toList(f.key), i)">✕</button>
          </div>
        </div>
      </div>

      <!-- 嵌套对象 -->
      <div v-else-if="f.type === 'group'" class="f f-group">
        <div class="f-head"><span>{{ f.label }}</span></div>
        <FieldsEditor :fields="f.fields || []" :model="toObject(f.key)" compact />
      </div>

      <!-- 对象列表 -->
      <div v-else-if="f.type === 'list'" class="f">
        <div class="f-head">
          <span>{{ f.label }}<i class="f-note">{{ toList(f.key).length }} 项</i></span>
          <button class="mini" @click="addItem(f)">+ 添加</button>
        </div>
        <div v-for="(item, i) in toList(f.key)" :key="i" class="list-item">
          <div class="list-item__bar">
            <b>{{ item[f.titleKey || 'title'] || `第 ${i + 1} 项` }}</b>
            <span class="row-tools">
              <button class="mini icon" @click="move(toList(f.key), i, -1)" :disabled="i === 0">↑</button>
              <button class="mini icon" @click="move(toList(f.key), i, 1)" :disabled="i === toList(f.key).length - 1">↓</button>
              <button class="mini danger" @click="remove(toList(f.key), i)">✕</button>
            </span>
          </div>
          <FieldsEditor :fields="f.fields || []" :model="item" compact />
        </div>
      </div>
    </div>
  </div>
</template>
