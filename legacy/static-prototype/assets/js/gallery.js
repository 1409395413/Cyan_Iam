/* =========================================================================
   StackGallery — 卡牌叠加画廊
   -------------------------------------------------------------------------
   · 素材多时自动横向叠加成一副"扇形牌"（宽度自适应，不限数量）
   · 鼠标移到哪张，哪张放大成主播放位（视频自动播放），移开后保持
   · 点击卡片右上角 ✕ 收牌还原；支持 ← → 切换、Esc 关闭、键盘操作
   · 选中瞬间有 squash & stretch 果冻动效
   · 窄屏自动切换为横向滑动 + 全屏灯箱
   ========================================================================= */
(function () {
  var esc = window.UI.esc, media = window.UI.media;

  var X_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';

  /* 单例灯箱（窄屏使用） */
  var lb, lbMedia, lbTitle, lbMeta;
  function ensureLightbox() {
    if (lb) return;
    lb = document.createElement('div');
    lb.className = 'lightbox';
    lb.innerHTML =
      '<div class="lightbox__box">' +
        '<div class="lightbox__media"></div>' +
        '<div class="lightbox__bar">' +
          '<div><h4></h4><span class="small"></span></div>' +
          '<button class="lightbox__close" aria-label="关闭">' + X_ICON + '</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(lb);
    lbMedia = lb.querySelector('.lightbox__media');
    lbTitle = lb.querySelector('h4');
    lbMeta = lb.querySelector('.small');
    lb.addEventListener('click', function (e) {
      if (e.target === lb || e.target.closest('.lightbox__close')) closeLightbox();
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeLightbox(); });
  }
  function openLightbox(m) {
    ensureLightbox();
    lbMedia.innerHTML = media({ kind: m.kind, src: m.src, poster: m.poster, title: m.title, hint: m.hint, ratio: 'r-16-9' }, true);
    lbTitle.textContent = m.title || '';
    lbMeta.textContent = m.meta || '';
    var v = lbMedia.querySelector('video');
    if (v) { v.controls = true; v.muted = false; try { v.play(); } catch (e) {} }
    lb.classList.add('is-open');
    document.body.style.overflow = 'hidden';
  }
  function closeLightbox() {
    if (!lb || !lb.classList.contains('is-open')) return;
    var v = lb.querySelector('video'); if (v) v.pause();
    lb.classList.remove('is-open');
    lbMedia.innerHTML = '';
    document.body.style.overflow = '';
  }

  function StackGallery(root, list) {
    this.el = root;
    this.items = list || [];
    this.active = null;
    this.carousel = false;
    this.hoverTimer = null;
    this.build();
  }

  StackGallery.prototype.build = function () {
    var self = this;
    if (!this.items.length) {
      this.el.innerHTML = '<div class="ph r-16-9"><div class="ph__label">' +
        window.UI.ICO_IMAGE + '暂无素材<small>去后台添加图片或视频</small></div></div>';
      return;
    }

    var html = this.items.map(function (m, i) {
      var inner = '<div class="sitem__body">' +
        media({ kind: m.kind, src: m.src, poster: m.poster, title: m.title, hint: m.hint, dur: m.dur, badge: m.badge, ratio: 'r-3-4' }) +
        '<div class="sitem__meta"><h4>' + esc(m.title || ('作品 ' + (i + 1))) + '</h4>' +
          (m.meta ? '<span>' + esc(m.meta) + '</span>' : '') + '</div>' +
        '<button class="sitem__close" aria-label="收牌">' + X_ICON + '</button>' +
      '</div>';
      return '<div class="sitem" data-i="' + i + '" role="button" tabindex="0" aria-label="' +
        esc(m.title || ('作品 ' + (i + 1))) + '">' + inner + '</div>';
    }).join('');

    html += '<div class="stack__nav">' +
      '<button data-dir="-1" aria-label="上一个">‹</button>' +
      '<button data-dir="0" class="stack__count">1 / ' + this.items.length + '</button>' +
      '<button data-dir="1" aria-label="下一个">›</button>' +
    '</div>';
    html += '<div class="stack__hint">移到任意一张查看 · 点 ✕ 收牌</div>';

    this.el.innerHTML = html;
    this.el.classList.add('stack-ready');
    this.cards = [].slice.call(this.el.querySelectorAll('.sitem'));
    this.countEl = this.el.querySelector('.stack__count');

    /* --- 事件绑定 --- */
    this.cards.forEach(function (card) {
      var i = +card.dataset.i;
      card.addEventListener('click', function (e) {
        if (e.target.closest('.sitem__close')) { e.stopPropagation(); self.setActive(null); return; }
        e.preventDefault();
        if (self.carousel) openLightbox(self.items[i]);
        else self.setActive(i === self.active ? null : i);
      });
      card.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          if (self.carousel) openLightbox(self.items[i]); else self.setActive(i);
        }
      });
      card.addEventListener('pointerenter', function (e) {
        if (self.carousel || e.pointerType === 'touch' || self.active === i) return;
        clearTimeout(self.hoverTimer);
        self.hoverTimer = setTimeout(function () { if (self.active !== i) self.setActive(i); }, 110);
      });
      card.addEventListener('pointerleave', function () { clearTimeout(self.hoverTimer); });
    });

    this.el.querySelectorAll('.stack__nav button').forEach(function (b) {
      b.addEventListener('click', function (e) {
        e.stopPropagation();
        var dir = +b.dataset.dir;
        if (!dir) { self.setActive(null); return; }
        var next = ((self.active === null ? 0 : self.active + dir) + self.items.length) % self.items.length;
        self.setActive(next);
      });
    });

    document.addEventListener('keydown', function (e) {
      if (self.active === null) return;
      if (e.key === 'Escape') self.setActive(null);
      if (e.key === 'ArrowRight') self.setActive((self.active + 1) % self.items.length);
      if (e.key === 'ArrowLeft') self.setActive((self.active - 1 + self.items.length) % self.items.length);
    });

    var rt;
    window.addEventListener('resize', function () {
      clearTimeout(rt);
      rt = setTimeout(function () { self.layout(); }, 120);
    });

    this.layout();
  };

  StackGallery.prototype.layout = function () {
    if (!this.cards || !this.cards.length) return;
    var W = this.el.clientWidth;
    if (!W) return;

    /* 窄屏 / 素材过多 → 横向滑动模式 */
    this.carousel = W < 620;
    this.el.classList.toggle('stack--carousel', this.carousel);

    if (this.carousel) {
      this.cards.forEach(function (c) {
        c.style.width = c.style.height = '';
        c.style.setProperty('--x', '0px'); c.style.setProperty('--y', '0px');
        c.style.setProperty('--rot', '0deg'); c.style.setProperty('--sc', '1');
        c.style.zIndex = '';
        c.classList.remove('is-dim', 'is-active');
      });
      this.el.style.height = '';
      return;
    }

    var n = this.cards.length;
    var H = Math.max(360, Math.min(520, W * 0.38));
    this.el.style.height = H + 'px';

    var ratio = 0.74;                       // 卡牌宽高比
    var cardH = Math.min(H * 0.80, 460);
    var cardW = Math.min(cardH * ratio, Math.max(180, W * 0.26));
    cardH = cardW / ratio;

    var stageW = Math.min(W - 24, 880);
    var stageH = Math.min(H - 12, stageW * 0.6);
    if (stageH > H - 12) stageH = H - 12;

    var maxHalf = W / 2 - 12;
    var step = n > 1 ? Math.min(cardW * 0.46, (maxHalf * 2 - cardW) / (n - 1)) : 0;
    step = Math.max(step, 26);

    var a = this.active;
    this.cards.forEach(function (c, i) {
      var x = 0, y = 0, rot = 0, sc = 1, z = 1, w = cardW, h = cardH, dim = false;

      if (a === null || a === undefined) {
        var d = i - (n - 1) / 2;
        x = d * step;
        y = Math.abs(d) * 7;
        rot = d * 2.0;
        sc = 1 - Math.abs(d) * 0.022;
        z = 50 - Math.round(Math.abs(d));
      } else if (i === a) {
        w = stageW; h = stageH; z = 200;
      } else if (i < a) {
        var dl = a - i;
        x = -Math.min(maxHalf - 20, stageW / 2 + 52 + (dl - 1) * 30);
        rot = -7; sc = 0.80; y = 26; z = 120 - dl; dim = true;
      } else {
        var dr = i - a;
        x = Math.min(maxHalf - 20, stageW / 2 + 52 + (dr - 1) * 30);
        rot = 7; sc = 0.80; y = 26; z = 120 - dr; dim = true;
      }

      c.style.width = w + 'px';
      c.style.height = h + 'px';
      c.style.setProperty('--x', x.toFixed(1) + 'px');
      c.style.setProperty('--y', y.toFixed(1) + 'px');
      c.style.setProperty('--rot', rot.toFixed(2) + 'deg');
      c.style.setProperty('--sc', sc.toFixed(3));
      c.style.zIndex = z;
      c.classList.toggle('is-active', i === a);
      c.classList.toggle('is-dim', dim);
    });

    if (this.countEl) this.countEl.textContent = (a === null ? '—' : (a + 1)) + ' / ' + n;
  };

  StackGallery.prototype.setActive = function (i) {
    clearTimeout(this.hoverTimer);
    if (this.carousel) { this.active = i; this.layout(); return; }

    /* 停掉上一个视频 */
    if (this.active !== null && this.cards && this.cards[this.active]) {
      var pv = this.cards[this.active].querySelector('video');
      if (pv) { pv.pause(); try { pv.currentTime = 0; } catch (e) {} }
    }

    this.active = i;
    this.el.classList.toggle('has-active', i !== null);
    this.layout();

    if (i !== null && this.cards && this.cards[i]) {
      var card = this.cards[i];
      /* 果冻动效重启 */
      card.classList.remove('is-picking');
      void card.offsetWidth;
      card.classList.add('is-picking');
      setTimeout(function () { card.classList.remove('is-picking'); }, 700);

      var v = card.querySelector('video');
      if (v) {
        if (!v.hasAttribute('controls')) { v.controls = true; }
        var p = v.play();
        if (p && p.catch) {
          p.catch(function () { v.muted = true; v.play().catch(function () {}); });
        }
      }
    }
  };

  window.StackGallery = StackGallery;
  window.StackGallery.closeLightbox = closeLightbox;
})();
