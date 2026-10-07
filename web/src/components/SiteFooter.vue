<script setup lang="ts">
import type { SiteConfig } from '@/types/content'

const props = defineProps<{ site: SiteConfig }>()

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <footer class="page footer">
    <div class="footer__cols">
      <div>
        <span class="nav__brand" style="padding: 0">
          <span class="nav__mark">{{ props.site.brandMark }}</span>
          {{ props.site.brandName }}
        </span>
        <p class="body" style="margin-top: 16px; max-width: 30ch" v-html="props.site.footerNote"></p>
      </div>

      <div v-for="(c, i) in props.site.footerCols || []" :key="i">
        <h5>{{ c.title }}</h5>
        <ul>
          <li v-for="(l, j) in c.links || []" :key="j">
            <a :href="l.href || '#'">{{ l.label }}</a>
          </li>
        </ul>
      </div>
    </div>

    <div class="footer__bottom">
      <span>{{ props.site.copyright }}</span>
      <a class="to-top" href="#" @click.prevent="toTop">回到顶部 ↑</a>
    </div>
  </footer>
</template>
