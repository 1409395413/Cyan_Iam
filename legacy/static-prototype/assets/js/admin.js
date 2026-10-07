/* =========================================================================
   后台编辑器 (Admin) — 所有模块的名称、文案、图片/视频都在这里维护
   -------------------------------------------------------------------------
   数据流：ContentAPI.get() → 页面对象 state → 表单双向绑定 → ContentAPI.save()
   每个表单元素带 data-path，指向对象中的字段路径，改即生效，点保存落盘。
   ========================================================================= */
(function () {
  var esc = window.UI.esc;
  var state = null;
  var ui = { sel: 'site' };

  var SIDE = document.getElementById('side');
  var MAIN = document.getElementById('main');
  var TOAST = document.getElementById('toast');

  /* ---------- path utils ---------- */
  function splitPath(p) {
    return p.split('.').map(function (k) { return /^\d+$/.test(k) ? +k : k; });
  }
  function getByPath(p, obj) {
    return splitPath(p).reduce(function (o, k) { return o === undefined || o === null ? undefined : o[k]; }, obj || state);
  }
  function setByPath(p, val) {
    var ks = splitPath(p), last = ks.pop();
    var target = ks.reduce(function (o, k) {
      if (o[k] === undefined || o[k] === null) o[k] = typeof ks[ks.indexOf(k) + 1] === 'number' ? [] : {};
      return o[k];
    }, state);
    target[last] = val;
  }

  /* ---------- form helpers ---------- */
  function input(label, path, opts) {
    opts = opts || {};
    var v = getByPath(path);
    if (v === undefined || v === null) v = opts.def === undefined ? '' : opts.def;
    var body;
    if (opts.type === 'textarea') {
      body = '<textarea class="' + (opts.tall ? 'tall' : '') + '" data-path="' + path + '" placeholder="' +
        esc(opts.ph || '') + '">' + esc(v) + '</textarea>';
    } else if (opts.type === 'select') {
      body = '<select data-path="' + path + '">' + opts.options.map(function (o) {
        return '<option value="' + esc(o.v) + '"' + (String(o.v) === String(v) ? ' selected' : '') + '>' + esc(o.l) + '</option>';
      }).join('') + '</select>';
    } else if (opts.type === 'checkbox') {
      body = '<label style="display:flex;align-items:center;gap:8px;text-transform:none;letter-spacing:0;font-size:14px;font-weight:400;color:var(--ink)">' +
        '<input type="checkbox" data-path="' + path + '"' + (v ? ' checked' : '') + '>' + esc(opts.text || '') + '</label>';
    } else if (opts.type === 'file') {
      body = '<div style="display:flex;gap:8px;align-items:center">' +
        '<input type="text" data-path="' + path + '" value="' + esc(v) + '" placeholder="素材 URL，或用右侧按钮上传">' +
        '<label class="btn btn--ghost btn--xs" style="flex:none;cursor:pointer">上传' +
        '<input type="file" accept="image/*,video/*" data-upload="' + path + '" style="display:none"></label></div>';
    } else {
      body = '<input type="text" data-path="' + path + '" value="' + esc(v) + '" placeholder="' + esc(opts.ph || '') + '">';
    }
    return '<div class="f"><label>' + esc(label) + '</label>' + body +
      (opts.hint ? '<div class="hint">' + esc(opts.hint) + '</div>' : '') + '</div>';
  }

  function lines(label, path, hint) {
    var v = getByPath(path) || [];
    return '<div class="f"><label>' + esc(label) + '</label>' +
      '<textarea class="tall" data-lines="' + path + '">' + esc(Array.isArray(v) ? v.join('\n') : v) + '</textarea>' +
      (hint ? '<div class="hint">' + esc(hint) + '</div>' : '') + '</div>';
  }

  function itemHead(title, path, i, len) {
    return '<div class="item__hd"><span class="n">' + esc(title) + '</span><span class="sp"></span>' +
      (i > 0 ? '<button class="btn btn--ghost btn--icon btn--xs" data-act="up" data-path="' + path + '" data-i="' + i + '">↑</button>' : '') +
      (i < len - 1 ? '<button class="btn btn--ghost btn--icon btn--xs" data-act="down" data-path="' + path + '" data-i="' + i + '">↓</button>' : '') +
      '<button class="btn btn--danger btn--xs" data-act="del" data-path="' + path + '" data-i="' + i + '">删除</button></div>';
  }

  /* ---------- media editor (图片/视频，后台可增删) ---------- */
  function mediaEditor(label, base) {
    var list = getByPath(base) || [];
    var html = '<div class="sect"><h4>' + esc(label) + ' <span class="ty" style="color:var(--ink3);font-weight:400">(' + list.length + ')</span></h4>' +
      list.map(function (m, i) {
        return '<div class="item">' + itemHead((m.title || m.hint || '素材') + ' ' + (i + 1), base, i, list.length) +
          '<div class="grid3">' +
            input('类型', base + '.' + i + '.kind', { type: 'select', options: [{ v: 'image', l: '图片' }, { v: 'video', l: '视频' }] }) +
            input('时长 / 角标', base + '.' + i + '.dur', { ph: '03:42' }) +
            input('左上角标签', base + '.' + i + '.badge', { ph: '4K / Live / 封面' }) +
          '</div>' +
          '<div class="grid2">' +
            input('标题', base + '.' + i + '.title', { ph: '作品名称' }) +
            input('副标题', base + '.' + i + '.meta', { ph: '人像 · 12 帧' }) +
          '</div>' +
          input('素材', base + '.' + i + '.src', { type: 'file', hint: '留空则前台显示占位块；也可填 CDN 地址' }) +
          input('占位提示', base + '.' + i + '.hint', { ph: '建议尺寸 / 格式，仅在没传素材时显示' }) +
        '</div>';
      }).join('') +
      '<div class="btn-row" style="margin-top:6px">' +
        '<button class="btn btn--ghost btn--xs" data-act="addMedia" data-path="' + base + '">+ 添加图片/视频</button>' +
        '<button class="btn btn--ghost btn--xs" data-act="addMany" data-path="' + base + '">+ 批量添加 3 条</button>' +
      '</div></div>';
    return html;
  }

  /* ---------- per-type editors ---------- */
  var EDIT = {
    hero: function (m, p) {
      return '<div class="grid2">' +
        input('眉标题 (eyebrow)', p + '.eyebrow') +
        input('导航文案', p + '.navLabel') +
      '</div>' +
      input('主标题', p + '.title', { type: 'textarea', hint: '可用 &lt;br&gt; 换行，&lt;em&gt;文字&lt;/em&gt; 显示为强调色' }) +
      input('副标题', p + '.lead', { type: 'textarea' }) +
      lines('能力标签 chips', p + '.chips', '每行一个') +
      '<div class="sect"><h4>按钮动作</h4>' +
        (getByPath(p + '.actions') || []).map(function (a, i) {
          return '<div class="item">' + itemHead('动作 ' + (i + 1), p + '.actions', i, getByPath(p + '.actions').length) +
            '<div class="grid3">' + input('文案', p + '.actions.' + i + '.label') +
            input('链接', p + '.actions.' + i + '.href') +
            input('样式', p + '.actions.' + i + '.kind', { type: 'select', options: [{ v: 'primary', l: '主按钮' }, { v: 'ghost', l: '次按钮' }] }) +
            '</div></div>';
        }).join('') +
        '<button class="btn btn--ghost btn--xs" data-act="addAction" data-path="' + p + '.actions">+ 添加按钮</button>' +
      '</div>' +
      '<div class="sect"><h4>数据条 stats</h4>' +
        (getByPath(p + '.stats') || []).map(function (s, i) {
          return '<div class="kv">' + input('数值', p + '.stats.' + i + '.value') +
            input('说明', p + '.stats.' + i + '.label') +
            '<button class="btn btn--danger btn--xs" data-act="del" data-path="' + p + '.stats" data-i="' + i + '">删除</button></div>';
        }).join('') +
        '<button class="btn btn--ghost btn--xs" data-act="addKV" data-path="' + p + '.stats" data-k="value" data-v="label">+ 添加一格</button>' +
      '</div>' +
      '<div class="sect"><h4>右侧视频框</h4>' +
        input('标题栏', p + '.reel.bar') +
        input('素材', p + '.reel.media.src', { type: 'file', hint: '视频会被静音循环播放；留空显示占位' }) +
        '<div class="grid2">' + input('占位标题', p + '.reel.media.title') + input('占位提示', p + '.reel.media.hint') + '</div>' +
        '<div class="grid2">' + input('角标', p + '.reel.media.badge') + input('时长', p + '.reel.media.dur') + '</div>' +
      '</div>';
    },

    about: function (m, p) {
      return '<div class="grid2">' + input('眉标题', p + '.eyebrow') + input('导航文案', p + '.navLabel') + '</div>' +
        input('标题', p + '.title', { type: 'textarea', hint: '可用 &lt;br&gt; 换行' }) +
        lines('正文段落', p + '.paragraphs', '每段一行') +
        '<div class="sect"><h4>人像</h4>' +
          input('素材', p + '.media.src', { type: 'file' }) +
          '<div class="grid2">' + input('占位标题', p + '.media.title') + input('占位提示', p + '.media.hint') + '</div>' +
          '<div class="grid2">' + input('左下角文字', p + '.media.cap') + input('右下角文字', p + '.media.cap2') + '</div>' +
        '</div>' +
        '<div class="sect"><h4>信息表 facts</h4>' +
          (getByPath(p + '.facts') || []).map(function (f, i) {
            return '<div class="kv">' + input('项目', p + '.facts.' + i + '.k') + input('内容', p + '.facts.' + i + '.v') +
              '<button class="btn btn--danger btn--xs" data-act="del" data-path="' + p + '.facts" data-i="' + i + '">删除</button></div>';
          }).join('') +
          '<button class="btn btn--ghost btn--xs" data-act="addKV" data-path="' + p + '.facts" data-k="k" data-v="v">+ 添加一行</button>' +
        '</div>' +
        lines('能力标签', p + '.skills', '每行一个');
    },

    stack: function (m, p) {
      return '<div class="grid2">' + input('眉标题', p + '.eyebrow') + input('导航文案', p + '.navLabel') + '</div>' +
        input('标题', p + '.title', { type: 'textarea' }) +
        input('说明', p + '.lead', { type: 'textarea', hint: '会显示在标题下方' }) +
        '<div class="grid2">' + input('尺寸', p + '.size', { type: 'select', options: SIZE_OPTS }) +
          input('显示在导航', p + '.showNav', { type: 'checkbox', text: '显示' }) + '</div>' +
        '<div class="sect"><h4>按钮动作</h4>' +
          (getByPath(p + '.actions') || []).map(function (a, i) {
            return '<div class="item">' + itemHead('动作 ' + (i + 1), p + '.actions', i, getByPath(p + '.actions').length) +
              '<div class="grid3">' + input('文案', p + '.actions.' + i + '.label') + input('链接', p + '.actions.' + i + '.href') +
              input('样式', p + '.actions.' + i + '.kind', { type: 'select', options: [{ v: 'primary', l: '主按钮' }, { v: 'ghost', l: '次按钮' }] }) +
              '</div></div>';
          }).join('') +
          '<button class="btn btn--ghost btn--xs" data-act="addAction" data-path="' + p + '.actions">+ 添加按钮</button>' +
        '</div>' +
        mediaEditor('作品（自动排成卡牌叠加）', p + '.media');
    },

    cases: function (m, p) {
      return '<div class="grid2">' + input('眉标题', p + '.eyebrow') + input('导航文案', p + '.navLabel') + '</div>' +
        input('标题', p + '.title', { type: 'textarea' }) + input('说明', p + '.lead', { type: 'textarea' }) +
        '<div class="sect"><h4>按钮动作</h4><button class="btn btn--ghost btn--xs" data-act="addAction" data-path="' + p + '.actions">+ 添加按钮</button></div>' +
        '<div class="sect"><h4>案例</h4>' +
          (getByPath(p + '.cases') || []).map(function (c, i) {
            return '<div class="item">' + itemHead(c.title || ('案例 ' + (i + 1)), p + '.cases', i, getByPath(p + '.cases').length) +
              input('标题', p + '.cases.' + i + '.title') +
              input('描述', p + '.cases.' + i + '.desc', { type: 'textarea' }) +
              input('封面素材', p + '.cases.' + i + '.media.src', { type: 'file', hint: '留空显示占位' }) +
              '<div class="grid2">' + input('占位标题', p + '.cases.' + i + '.media.title') + input('角标', p + '.cases.' + i + '.media.badge') + '</div>' +
              '<div class="sect" style="margin-top:12px"><h4 style="font-size:13px">参数表</h4>' +
                (getByPath(p + '.cases.' + i + '.specs') || []).map(function (s, j) {
                  return '<div class="kv">' + input('名称', p + '.cases.' + i + '.specs.' + j + '.k') +
                    input('值', p + '.cases.' + i + '.specs.' + j + '.v') +
                    '<button class="btn btn--danger btn--xs" data-act="del" data-path="' + p + '.cases.' + i + '.specs" data-j="' + j + '" data-i="' + j + '">删除</button></div>';
                }).join('') +
                '<button class="btn btn--ghost btn--xs" data-act="addKV" data-path="' + p + '.cases.' + i + '.specs" data-k="k" data-v="v">+ 添加参数</button>' +
              '</div>' +
              lines('标签（每行一个）', p + '.cases.' + i + '.tags') +
            '</div>';
          }).join('') +
          '<button class="btn btn--ghost btn--xs" data-act="addCase" data-path="' + p + '.cases">+ 添加案例</button>' +
        '</div>';
    },

    quote: function (m, p) {
      return input('主句', p + '.title', { type: 'textarea', tall: true }) +
        input('副句', p + '.lead', { type: 'textarea' }) +
        input('显示在导航', p + '.showNav', { type: 'checkbox', text: '显示' });
    },

    contact: function (m, p) {
      return '<div class="grid2">' + input('眉标题', p + '.eyebrow') + input('导航文案', p + '.navLabel') + '</div>' +
        input('标题', p + '.title', { type: 'textarea' }) + input('说明', p + '.lead', { type: 'textarea' }) +
        '<div class="sect"><h4>联系信息</h4>' +
          (getByPath(p + '.rows') || []).map(function (r, i) {
            return '<div class="kv">' + input('名称', p + '.rows.' + i + '.k') + input('内容', p + '.rows.' + i + '.v') +
              '<button class="btn btn--danger btn--xs" data-act="del" data-path="' + p + '.rows" data-i="' + i + '">删除</button></div>';
          }).join('') +
          '<button class="btn btn--ghost btn--xs" data-act="addKV" data-path="' + p + '.rows" data-k="k" data-v="v">+ 添加一行</button>' +
        '</div>' +
        lines('项目类型下拉', p + '.projectTypes', '每行一个') +
        '<div class="grid2">' + input('表单提示', p + '.formNote') + input('提交按钮文案', p + '.submitLabel') + '</div>';
    },

    text: function (m, p) {
      return '<div class="grid2">' + input('眉标题', p + '.eyebrow') + input('导航文案', p + '.navLabel') + '</div>' +
        input('标题', p + '.title', { type: 'textarea' }) +
        input('正文', p + '.body', { type: 'textarea', tall: true, hint: '支持 HTML' }) +
        mediaEditor('配图', p + '.media');
    },

    generic: function (m, p) {
      return input('正文', p + '.body', { type: 'textarea', tall: true }) + mediaEditor('素材', p + '.media');
    }
  };

  var SIZE_OPTS = [
    { v: 'auto', l: '自动（推荐）' }, { v: 'sm', l: '小 · 1/4 宽' }, { v: 'md', l: '中 · 1/3 宽' },
    { v: 'lg', l: '半宽 · 1/2' }, { v: 'xl', l: '大 · 2/3 宽' }, { v: 'full', l: '通栏' }
  ];
  var TYPE_OPTS = [
    { v: 'stack', l: '作品卡牌叠加画廊' }, { v: 'cases', l: '案例列表（带参数表）' },
    { v: 'about', l: '图文简介' }, { v: 'quote', l: '一句话主张' },
    { v: 'contact', l: '联系方式 + 表单' }, { v: 'text', l: '纯文本 / 富文本' },
    { v: 'hero', l: '首屏横幅' }
  ];

  /* ---------- render ---------- */
  function renderSide() {
    var rows = '<div class="mlist">' +
      '<div class="mrow' + (ui.sel === 'site' ? ' is-sel' : '') + '" data-sel="site">' +
        '<span class="dot on"></span><span class="t">站点信息</span><span class="ty">site</span></div>' +
      state.modules.map(function (m, i) {
        return '<div class="mrow' + (ui.sel === m.id ? ' is-sel' : '') + '" data-sel="' + esc(m.id) + '">' +
          '<span class="dot' + (m.visible !== false ? ' on' : '') + '"></span>' +
          '<span class="t">' + esc(m.navLabel || m.title || m.type) + '</span>' +
          '<span class="ty">' + esc(m.type) + '</span></div>';
      }).join('') + '</div>' +
      '<div class="addrow">' +
        '<select id="newType">' + TYPE_OPTS.map(function (o) { return '<option value="' + o.v + '">' + o.l + '</option>'; }).join('') + '</select>' +
        '<button class="btn btn--xs" data-act="addModule">新增模块</button>' +
      '</div>';
    SIDE.innerHTML = '<h3>模块（可增删排序）</h3>' + rows;
  }

  function renderMain() {
    if (ui.sel === 'site') { renderSite(); return; }
    var idx = state.modules.findIndex(function (m) { return m.id === ui.sel; });
    var m = state.modules[idx], p = 'modules.' + idx;
    if (!m) { ui.sel = 'site'; renderSide(); renderSite(); return; }

    var ed = EDIT[m.type] || EDIT.generic;
    MAIN.innerHTML =
      '<div style="display:flex;align-items:center;gap:10px;margin-bottom:18px;flex-wrap:wrap">' +
        '<h2 style="font-size:20px">' + esc(m.navLabel || m.title || m.type) + '</h2>' +
        '<span class="badge-mode">' + esc(m.type) + '</span>' +
        '<span class="sp" style="flex:1"></span>' +
        '<button class="btn btn--ghost btn--xs" data-act="moduleUp" data-i="' + idx + '">↑ 上移</button>' +
        '<button class="btn btn--ghost btn--xs" data-act="moduleDown" data-i="' + idx + '">↓ 下移</button>' +
        '<button class="btn btn--danger btn--xs" data-act="moduleDel" data-i="' + idx + '">删除模块</button>' +
      '</div>' +
      '<div class="grid2">' +
        input('模块 ID（锚点）', p + '.id', { hint: '导航跳转用的 #锚点，改后会同步影响旧链接' }) +
        input('模块类型', p + '.type', { type: 'select', options: TYPE_OPTS, hint: '换类型后请检查字段是否匹配' }) +
      '</div>' +
      '<div class="grid3">' +
        input('版面占位', p + '.size', { type: 'select', options: SIZE_OPTS, hint: 'auto 会按内容量自动分配宽度' }) +
        input('显示在导航', p + '.showNav', { type: 'checkbox', text: '显示' }) +
        input('在前台渲染', p + '.visible', { type: 'checkbox', text: '启用' }) +
      '</div>' +
      '<div class="sect">' + ed(m, p) + '</div>';
  }

  function renderSite() {
    MAIN.innerHTML = '<h2 style="font-size:20px;margin-bottom:18px">站点信息</h2>' +
      '<div class="grid2">' + input('品牌名', 'site.brandName') + input('品牌标记字符', 'site.brandMark') + '</div>' +
      input('页脚简介', 'site.footerNote', { type: 'textarea' }) +
      input('版权信息', 'site.copyright') +
      '<div class="sect"><h4>页脚栏位</h4>' +
        (state.site.footerCols || []).map(function (c, i) {
          return '<div class="item">' + itemHead(c.title || ('栏位 ' + (i + 1)), 'site.footerCols', i, state.site.footerCols.length) +
            input('栏位标题', 'site.footerCols.' + i + '.title') +
            (getByPath('site.footerCols.' + i + '.links') || []).map(function (l, j) {
              return '<div class="kv">' + input('文案', 'site.footerCols.' + i + '.links.' + j + '.label') +
                input('链接', 'site.footerCols.' + i + '.links.' + j + '.href') +
                '<button class="btn btn--danger btn--xs" data-act="del" data-path="site.footerCols.' + i + '.links" data-i="' + j + '">删除</button></div>';
            }).join('') +
            '<button class="btn btn--ghost btn--xs" data-act="addLink" data-path="site.footerCols.' + i + '.links">+ 添加链接</button>' +
          '</div>';
        }).join('') +
        '<button class="btn btn--ghost btn--xs" data-act="addFooterCol">+ 添加页脚栏位</button></div>' +
      '<div class="sect"><h4>后端接口模式</h4>' +
        '<div class="grid2">' + input('数据源模式', '_mode', { type: 'select', options: [{ v: 'local', l: '本地存储（开箱即用）' }, { v: 'rest', l: 'REST 接口' }] }) +
        input('接口地址 base', '_base', { ph: 'https://api.example.com' }) + '</div>' +
        '<div class="hint">选 REST 后，读写都会走 GET/PUT {base}/content，素材上传走 POST {base}/media。接口不通时会自动回落本地数据。</div>' +
      '</div>';
  }

  function render() { renderSide(); renderMain(); }

  /* ---------- events (delegated) ---------- */
  document.addEventListener('input', function (e) {
    var el = e.target;
    if (!el.dataset) return;
    if (el.dataset.lines) {
      setByPath(el.dataset.lines, el.value.split('\n').map(function (s) { return s.trim(); }).filter(Boolean));
      return;
    }
    if (el.dataset.path && el.type !== 'file' && el.dataset.path.charAt(0) !== '_') {
      setByPath(el.dataset.path, el.type === 'checkbox' ? el.checked : el.value);
      if (el.dataset.path === 'modules.' + state.modules.findIndex(function (m) { return m.id === ui.sel; }) + '.id') {
        ui.sel = el.value; renderSide();
      }
      if (/\.(type|size|visible|showNav|id)$/.test(el.dataset.path)) {
        clearTimeout(window.__rr); window.__rr = setTimeout(renderSide, 400);
      }
    }
  });

  document.addEventListener('change', function (e) {
    var el = e.target;
    if (el.dataset && el.dataset.upload) {
      var f = el.files && el.files[0];
      if (!f) return;
      if (f.size > 3 * 1024 * 1024) {
        toast('文件 ' + Math.round(f.size / 1048576) + 'MB 偏大，本地存储有 5MB 上限，建议填 CDN 地址或接对象存储');
      }
      ContentAPI.uploadMedia(f).then(function (url) {
        setByPath(el.dataset.upload, url);
        var t = document.querySelector('input[type=text][data-path="' + el.dataset.upload + '"]');
        if (t) t.value = url;
        toast('素材已上传');
      }).catch(function () { toast('上传失败'); });
    }
    if (el.id === 'newType') { /* noop */ }
    if (el.dataset && el.dataset.path === '_mode') {
      el.value === 'rest' ? ContentAPI.useRest(document.querySelector('[data-path="_base"]').value) : ContentAPI.useLocal();
      toast('已切换为 ' + (el.value === 'rest' ? 'REST 接口' : '本地存储'));
    }
  });

  document.addEventListener('click', function (e) {
    var sel = e.target.closest('[data-sel]');
    if (sel) { ui.sel = sel.dataset.sel; render(); return; }

    var b = e.target.closest('[data-act]');
    if (!b) return;
    var act = b.dataset.act, path = b.dataset.path, i = +b.dataset.i;
    var arr = path ? getByPath(path) : null;

    switch (act) {
      case 'addMedia': arr.push({ kind: 'image', src: '', title: '新素材', meta: '', hint: '' }); break;
      case 'addMany':
        for (var k = 0; k < 3; k++) arr.push({ kind: 'image', src: '', title: '新素材 ' + (arr.length + 1), meta: '', hint: '' });
        break;
      case 'addAction': {
        var acts = getByPath(path);
        if (!Array.isArray(acts)) { acts = []; setByPath(path, acts); }
        acts.push({ label: '按钮', href: '#', kind: 'ghost' });
        break;
      }
      case 'addKV': {
        if (!Array.isArray(arr)) { arr = []; setByPath(path, arr); }
        var o = {}; o[b.dataset.k] = ''; o[b.dataset.v] = ''; arr.push(o); break;
      }
      case 'addLink': arr.push({ label: '链接', href: '#' }); break;
      case 'addFooterCol': state.site.footerCols.push({ title: '新栏位', links: [] }); break;
      case 'addCase':
        arr.push({ title: '新案例', desc: '', media: { kind: 'image', src: '', title: '封面占位' }, specs: [], tags: [] }); break;
      case 'del': arr.splice(i, 1); break;
      case 'up': arr.splice(i - 1, 0, arr.splice(i, 1)[0]); break;
      case 'down': arr.splice(i + 1, 0, arr.splice(i, 1)[0]); break;
      case 'addModule':
        var t = document.getElementById('newType').value;
        var id = 'mod-' + Date.now().toString(36);
        var nm = { id: id, type: t, visible: true, size: 'auto', showNav: true, navLabel: '新模块', title: '新模块', eyebrow: '' };
        if (t === 'stack') { nm.media = []; nm.actions = []; }
        if (t === 'cases') { nm.cases = []; nm.actions = []; }
        if (t === 'contact') { nm.rows = []; nm.projectTypes = ['商业摄影']; }
        if (t === 'about') { nm.paragraphs = []; nm.facts = []; nm.media = { kind: 'image', src: '' }; }
        state.modules.push(nm); ui.sel = id; render(); return;
      case 'moduleUp': if (i > 0) { var s = state.modules; s.splice(i - 1, 0, s.splice(i, 1)[0]); render(); } return;
      case 'moduleDown': if (i < state.modules.length - 1) { var s2 = state.modules; s2.splice(i + 1, 0, s2.splice(i, 1)[0]); render(); } return;
      case 'moduleDel':
        if (!confirm('确定删除该模块？删除后前台不再显示。')) return;
        state.modules.splice(i, 1); ui.sel = 'site'; render(); return;
      default: return;
    }
    render();
  });

  /* ---------- toolbar ---------- */
  function toast(msg) {
    TOAST.textContent = msg; TOAST.classList.add('on');
    clearTimeout(TOAST._t); TOAST._t = setTimeout(function () { TOAST.classList.remove('on'); }, 2200);
  }
  window.__render = render;

  document.getElementById('btnSave').addEventListener('click', function () {
    ContentAPI.save(state).then(function () { toast('已保存，前台刷新即可看到'); });
  });
  document.getElementById('btnPreview').addEventListener('click', function () {
    ContentAPI.save(state).then(function () { window.open('index.html', '_blank'); });
  });
  document.getElementById('btnExport').addEventListener('click', function () {
    var blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = 'content.json'; a.click();
  });
  document.getElementById('btnImport').addEventListener('click', function () {
    document.getElementById('fileImport').click();
  });
  document.getElementById('fileImport').addEventListener('change', function (e) {
    var f = e.target.files[0]; if (!f) return;
    var fr = new FileReader();
    fr.onload = function () {
      try {
        state = JSON.parse(fr.result);
        render(); toast('已导入');
      } catch (err) { toast('JSON 解析失败'); }
    };
    fr.readAsText(f);
  });
  document.getElementById('btnReset').addEventListener('click', function () {
    if (!confirm('恢复为出厂内容？你的修改会被覆盖。')) return;
    ContentAPI.reset().then(function (d) { state = d; render(); toast('已恢复默认'); });
  });

  /* ---------- boot ---------- */
  ContentAPI.get().then(function (d) {
    state = d;
    render();
    var sel = document.querySelector('[data-path="_mode"]');
    if (sel) sel.value = ContentAPI.mode;
    var base = document.querySelector('[data-path="_base"]');
    if (base) base.value = ContentAPI.base;
  });
})();
