/* =========================================================================
   UI helpers — 转义 / 素材渲染。素材缺失时统一输出占位块。
   ========================================================================= */
(function () {
  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  var ICO_IMAGE = '<svg class="ph__ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4">' +
    '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="9.5" r="2"/><path d="M21 16l-4.5-4.5L8 20"/></svg>';
  var PLAY = '<span class="play"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg></span>';
  var PLAY_SM = '<span class="play play--sm"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg></span>';

  /* 返回一个 media 对象的 HTML。big=true 时使用大播放键。 */
  function media(m, big) {
    m = m || {};
    var ratio = m.ratio || (m.kind === 'video' ? 'r-16-9' : 'r-4-3');
    var head =
      (m.badge ? '<span class="tag-res">' + esc(m.badge) + '</span>' : '') +
      (m.dur ? '<span class="tag-dur">' + esc(m.dur) + '</span>' : '');

    if (m.kind === 'video' && m.src) {
      return '<div class="ph ' + ratio + '" style="padding:0">' + head +
        '<video src="' + esc(m.src) + '"' + (m.poster ? ' poster="' + esc(m.poster) + '"' : '') +
        ' playsinline preload="metadata"' + (m.loop ? ' loop muted autoplay' : ' controls') + '></video></div>';
    }
    if (m.src) {
      return '<div class="ph ' + ratio + '" style="padding:0">' + head +
        '<img src="' + esc(m.src) + '" alt="' + esc(m.title || m.label || '') + '" loading="lazy"></div>';
    }

    /* 占位 */
    return '<div class="ph ' + ratio + '">' + head +
      '<div class="ph__label">' +
        (m.kind === 'video' ? (big ? PLAY : PLAY_SM) : ICO_IMAGE) +
        esc(m.title || m.label || '素材占位') +
        (m.hint ? '<small>' + esc(m.hint) + '</small>' : '') +
      '</div></div>';
  }

  window.UI = { esc: esc, media: media, ICO_IMAGE: ICO_IMAGE, PLAY: PLAY };
})();
