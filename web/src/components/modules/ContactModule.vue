<script setup lang="ts">
/** 联系方式：信息列表 + 项目需求表单（提交到后端 /api/inquiry，进后台「工作对接」）。 */
import { reactive, ref } from 'vue'
import { submitInquiry } from '@/api'
import { trackEvent } from '@/util/tracking'
import type { ContactModule } from '@/types/content'

const props = defineProps<{ module: ContactModule }>()

const form = reactive({
  name: '',
  contact: '',
  type: (props.module.projectTypes || [])[0] || '',
  budget: '',
  message: ''
})

const state = ref<'idle' | 'sending' | 'ok' | 'error'>('idle')
const error = ref('')

async function submit() {
  if (state.value === 'sending') return
  state.value = 'sending'
  error.value = ''
  try {
    await submitInquiry({ ...form })
    trackEvent('inquiry', form.type || '')
    state.value = 'ok'
    form.name = form.contact = form.message = form.budget = ''
  } catch (e: any) {
    state.value = 'error'
    error.value = e?.message || '发送失败'
  }
}
</script>

<template>
  <div class="contact reveal">
    <div>
      <dl v-if="props.module.rows?.length" class="rows">
        <div v-for="r in props.module.rows" :key="r.k" class="row-i">
          <dt>{{ r.k }}</dt>
          <dd>{{ r.v }}</dd>
        </div>
      </dl>
    </div>

    <form class="form glass" @submit.prevent="submit">
      <div class="form__row">
        <div class="field">
          <label>姓名</label>
          <input v-model="form.name" type="text" placeholder="你的名字" required />
        </div>
        <div class="field">
          <label>邮箱 / 微信</label>
          <input v-model="form.contact" type="text" placeholder="方便我回复你" required />
        </div>
      </div>

      <div class="form__row">
        <div class="field">
          <label>项目类型</label>
          <select v-model="form.type">
            <option v-for="t in props.module.projectTypes || []" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="field">
          <label>预算区间（选填）</label>
          <input v-model="form.budget" type="text" placeholder="如 5k–1w" />
        </div>
      </div>

      <div class="field">
        <label>项目简介</label>
        <textarea v-model="form.message" placeholder="简单描述项目内容、时间与交付形式" required></textarea>
      </div>

      <div class="form__foot">
        <p class="small">
          <span v-if="state === 'ok'" style="color: #0a7d33">已收到，我会尽快回复。</span>
          <span v-else-if="state === 'error'" style="color: #d70015">{{ error }}</span>
          <span v-else>{{ props.module.formNote }}</span>
        </p>
        <button class="btn" type="submit" :disabled="state === 'sending'">
          {{ state === 'sending' ? '发送中…' : props.module.submitLabel || '发送' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.btn[disabled] { opacity: .6; cursor: default; transform: none; }
</style>
