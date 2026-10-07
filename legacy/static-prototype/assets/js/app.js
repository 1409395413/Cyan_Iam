/* =========================================================================
   bootstrap — 取数据 → 渲染 → 挂交互
   ========================================================================= */
(function () {
  var navPill = document.getElementById('navPill');
  var app = document.getElementById('app');
  var footer = document.getElementById('siteFooter');
  var burger = document.getElementById('burger');

  window.ContentAPI.get().then(function (data) {
    document.title = (data.site.brandName || '作品集') + ' · 影像创作作品集';
    navPill.innerHTML = window.Renderer.nav(data.site, data.modules);
    app.innerHTML = window.Renderer.page(data);
    footer.innerHTML = window.Renderer.footer(data.site);
    window.Renderer.mount(app);
    afterRender(data);
  });

  function afterRender(data) {
    /* ---- 移动端菜单 ---- */
    burger.addEventListener('click', function () {
      var open = navPill.classList.toggle('is-open');
      burger.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    navPill.addEventListener('click', function (e) {
      if (e.target.closest('a')) {
        navPill.classList.remove('is-open');
        burger.setAttribute('aria-expanded', 'false');
      }
    });

    /* ---- 滚动高亮 ---- */
    var links = [].slice.call(navPill.querySelectorAll('.nav__link'));
    var targets = links.map(function (a) { return document.querySelector(a.getAttribute('href')); });
    function spy() {
      var idx = -1, min = Infinity;
      targets.forEach(function (sec, i) {
        if (!sec) return;
        var d = Math.abs(sec.getBoundingClientRect().top - 140);
        if (d < min) { min = d; idx = i; }
      });
      links.forEach(function (a, i) { a.classList.toggle('is-active', i === idx); });
    }
    window.addEventListener('scroll', spy, { passive: true });
    spy();

    /* ---- 入场动画 ---- */
    var items = [].slice.call(document.querySelectorAll('.reveal'));
    if ('IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (e) {
          if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target); }
        });
      }, { rootMargin: '0px 0px -8% 0px', threshold: 0.06 });
      items.forEach(function (t, i) {
        t.style.transitionDelay = Math.min(i % 6, 5) * 60 + 'ms';
        io.observe(t);
      });
    } else {
      items.forEach(function (t) { t.classList.add('is-in'); });
    }
  }
})();
