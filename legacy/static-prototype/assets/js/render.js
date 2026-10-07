/* =========================================================================
   Renderer — 把 content JSON 渲染成页面
   -------------------------------------------------------------------------
   新增/删除/调整模块都不需要改这里：
   · 每个模块按 size 或 auto 规则换算成 12 栅格的 span
   · grid-auto-flow: dense 会自动补齐空位，版面永远被填满
   · 未知 type 会走通用渲染分支，后续扩展新类型也不会崩
   ========================================================================= */
(function () {
  var esc = window.UI.esc, media = window.UI.media;

  var SPAN = { sm: 3, md: 4, lg: 6, xl: 8, full: 12 };

  function autoSpan(m) {
    var c = m.media ? m.media.length : 0;
    switch (m.type) {
      case 'hero':   return 12;
      case 'quote':  return 12;
      case 'about':  return 12;
      case 'stack':  return c >= 5 ? 12 : (c >= 3 ? 8 : 6);
      case 'cases':  return (m.cases && m.cases.length > 1) ? 12 : 8;
      case 'contact':return 12;
      case 'text':   return String(m.body || '').length > 320 ? 8 : 6;
      default:       return c > 3 ? 12 : 6;
    }
  }

  function head(m) {
    var h = '';
    if (m.eyebrow) h += '<p class="eyebrow">' + esc(m.eyebrow) + '</p>';
    if (m.title)   h += '<div class="rule"></div><h2 class="t-lg">' + m.title + '</h2>';
    if (m.lead)    h += '<p class="lead">' + m.lead + '</p>';
    return h ? '<div class="mod__head">' + h + '</div>' : '';
  }

  function actions(m) {
    if (!m.actions || !m.actions.length) return '';
    return '<div class="btn-row" style="margin-top:24px">' + m.actions.map(function (a) {
      return '<a class="btn ' + (a.kind === 'primary' ? '' : 'btn--ghost') + '" href="' + esc(a.href || '#') + '">' + esc(a.label) + '</a>';
    }).join('') + '</div>';
  }

  function chips(m) {
    if (!m.chips || !m.chips.length) return '';
    return '<div class="chip-row" style="margin-top:26px">' + m.chips.map(function (c) {
      return '<span class="chip">' + esc(c) + '</span>';
    }).join('') + '</div>';
  }

  /* ---------------- module renderers ---------------- */
  var R = {
    hero: function (m) {
      var reel = m.reel || {};
      var rm = reel.media || {};
      return '<div class="hero__grid reveal">' +
        '<div>' +
          (m.eyebrow ? '<p class="eyebrow">' + esc(m.eyebrow) + '</p>' : '') +
          '<h1 class="t-mega" style="margin-top:16px">' + (m.title || '') + '</h1>' +
          (m.lead ? '<p class="lead hero__lead">' + m.lead + '</p>' : '') +
          chips(m) + actions(m) +
        '</div>' +
        '<div class="reel__shell glass reveal">' +
          '<div class="reel__bar"><span>' + esc(reel.bar || 'Showreel') + '</span>' +
            '<span class="reel__dots"><i></i><i></i><i></i></span></div>' +
          media({ kind: rm.kind, src: rm.src, poster: rm.poster, title: rm.title, hint: rm.hint, dur: rm.dur, badge: rm.badge, ratio: 'r-16-9', loop: true }, true) +
        '</div>' +
      '</div>' +
      (m.stats && m.stats.length ? '<div class="hero__stats">' + m.stats.map(function (s) {
        return '<div class="stat reveal"><b class="mono">' + esc(s.value) + '</b><span>' + esc(s.label) + '</span></div>';
      }).join('') + '</div>' : '');
    },

    about: function (m) {
      var md = m.media || {};
      return '<div class="about reveal">' +
        '<div class="about__frame glass">' +
          media({ kind: md.kind, src: md.src, title: md.title, hint: md.hint, ratio: 'r-3-4' }) +
          '<div class="about__cap"><span>' + esc(md.cap || '') + '</span><span>' + esc(md.cap2 || '') + '</span></div>' +
        '</div>' +
        '<div>' +
          (m.paragraphs || []).map(function (p) { return '<p class="body about__p">' + p + '</p>'; }).join('') +
          (m.facts && m.facts.length ? '<div class="about__facts">' + m.facts.map(function (f) {
            return '<div class="fact"><b>' + esc(f.k) + '</b><span>' + esc(f.v) + '</span></div>';
          }).join('') + '</div>' : '') +
          (m.skills && m.skills.length ? '<p class="eyebrow" style="margin-bottom:10px">能力关键词</p><div class="chip-row">' +
            m.skills.map(function (s) { return '<span class="chip chip--flat">' + esc(s) + '</span>'; }).join('') + '</div>' : '') +
        '</div>' +
      '</div>';
    },

    stack: function (m) {
      return '<div class="stack reveal" data-list="' + encodeURIComponent(JSON.stringify(m.media || [])) + '"></div>';
    },

    cases: function (m) {
      return (m.cases || []).map(function (c) {
        return '<div class="case reveal">' +
          '<div>' + media({ kind: c.media && c.media.kind, src: c.media && c.media.src, title: c.media && c.media.title, hint: c.media && c.media.hint, badge: c.media && c.media.badge, ratio: 'r-16-9' }) + '</div>' +
          '<div>' +
            '<h3 class="t-md">' + esc(c.title) + '</h3>' +
            (c.desc ? '<p class="body" style="margin-top:12px">' + c.desc + '</p>' : '') +
            (c.specs && c.specs.length ? '<dl class="specs">' + c.specs.map(function (s) {
              return '<div class="spec"><dt>' + esc(s.k) + '</dt><dd>' + esc(s.v) + '</dd></div>';
            }).join('') + '</dl>' : '') +
            (c.tags && c.tags.length ? '<div class="chip-row" style="margin-top:18px">' +
              c.tags.map(function (t) { return '<span class="chip chip--flat">' + esc(t) + '</span>'; }).join('') + '</div>' : '') +
          '</div>' +
        '</div>';
      }).join('');
    },

    quote: function (m) {
      return '<h2 class="t-lg">' + (m.title || '') + '</h2>' + (m.lead ? '<p class="lead">' + m.lead + '</p>' : '');
    },

    contact: function (m) {
      return '<div class="contact reveal">' +
        '<div>' +
          (m.rows && m.rows.length ? '<dl class="rows">' + m.rows.map(function (r) {
            return '<div class="row-i"><dt>' + esc(r.k) + '</dt><dd>' + esc(r.v) + '</dd></div>';
          }).join('') + '</dl>' : '') +
        '</div>' +
        '<form class="form glass" onsubmit="return false">' +
          '<div class="form__row">' +
            '<div class="field"><label>姓名</label><input type="text" placeholder="你的名字"></div>' +
            '<div class="field"><label>邮箱 / 微信</label><input type="text" placeholder="方便我回复你"></div>' +
          '</div>' +
          '<div class="field"><label>项目类型</label><select>' +
            (m.projectTypes || []).map(function (t) { return '<option>' + esc(t) + '</option>'; }).join('') +
          '</select></div>' +
          '<div class="field"><label>项目简介</label><textarea placeholder="简单描述项目内容、时间与预算区间"></textarea></div>' +
          '<div class="form__foot"><p class="small">' + esc(m.formNote || '') + '</p>' +
            '<button class="btn" type="submit">' + esc(m.submitLabel || '发送') + '</button></div>' +
        '</form>' +
      '</div>';
    },

    text: function (m) {
      return '<div class="textblock">' + (m.body || '') + '</div>';
    },

    generic: function (m) {
      var html = '<div class="textblock">' + (m.body || m.lead || '') + '</div>';
      if (m.media && m.media.length) {
        html += '<div class="grid" style="margin-top:20px">' + m.media.map(function (md) {
          return '<div class="mod" style="--span:' + (m.media.length > 2 ? 4 : 6) + '">' + media(md) + '</div>';
        }).join('') + '</div>';
      }
      return html;
    }
  };

  function renderModule(m) {
    var span = SPAN[m.size] || autoSpan(m);
    var inner = (R[m.type] || R.generic)(m);
    var body = (m.type === 'quote')
      ? '<div class="mod__inner quote">' + inner + '</div>'
      : '<div class="mod__inner">' + head(m) + inner + actions(m) + '</div>';

    return '<section class="mod ' + (m.flat ? 'mod--flat' : 'glass reveal') + '" id="' + esc(m.id) +
      '" style="--span:' + span + '">' + body + '</section>';
  }

  window.Renderer = {
    nav: function (site, modules) {
      return '<span class="nav__brand"><span class="nav__mark">' + esc(site.brandMark || 'Y') + '</span>' +
        esc(site.brandName || '') + '</span>' +
        modules.filter(function (m) { return m.showNav && m.visible; }).map(function (m) {
          return '<a class="nav__link" href="#' + esc(m.id) + '">' + esc(m.navLabel || m.id) + '</a>';
        }).join('');
    },

    page: function (data) {
      return data.modules
        .filter(function (m) { return m.visible !== false; })
        .map(renderModule).join('');
    },

    footer: function (site) {
      return '<div class="footer__cols">' +
        '<div><span class="nav__brand" style="padding:0"><span class="nav__mark">' + esc(site.brandMark || 'Y') + '</span>' +
          esc(site.brandName || '') + '</span><p class="body" style="margin-top:16px;max-width:30ch">' + (site.footerNote || '') + '</p></div>' +
        (site.footerCols || []).map(function (c) {
          return '<div><h5>' + esc(c.title) + '</h5><ul>' + c.links.map(function (l) {
            return '<li><a href="' + esc(l.href) + '">' + esc(l.label) + '</a></li>';
          }).join('') + '</ul></div>';
        }).join('') +
      '</div>' +
      '<div class="footer__bottom"><span>' + esc(site.copyright || '') + '</span><a class="to-top" href="#home">回到顶部 ↑</a></div>';
    },

    /* 渲染完成后挂交互 */
    mount: function (app) {
      app.querySelectorAll('.stack[data-list]').forEach(function (el) {
        try {
          var list = JSON.parse(decodeURIComponent(el.dataset.list));
          new window.StackGallery(el, list);
        } catch (e) { console.warn('stack 初始化失败', e); }
      });
    }
  };
})();
