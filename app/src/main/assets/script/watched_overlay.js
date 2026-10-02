// Grays out thumbnails of videos watched past 85% (per the YuTbe watch ledger)
// and lays a smoky overlay on top of them.
(function () {
  var OVERLAY_CLASS = 'yutbe-watched-overlay';
  var MARKED_CLASS = 'yutbe-watched';
  var IDS = new Set();

  function refreshIds() {
    try {
      var raw = lite.watchedIds();
      var list = JSON.parse(raw || '[]');
      IDS = new Set(list);
    } catch (ignored) {}
  }

  function injectStyle() {
    if (document.getElementById('yutbe-watched-style')) return;
    var style = document.createElement('style');
    style.id = 'yutbe-watched-style';
    style.textContent =
      '.' + MARKED_CLASS + ' img { filter: grayscale(1) brightness(.7); }' +
      '.' + OVERLAY_CLASS + ' { position:absolute; inset:0; background:rgba(0,0,0,.45);' +
      '  pointer-events:none; z-index:3; border-radius:inherit; }';
    (document.head || document.documentElement).appendChild(style);
  }

  function thumbContainerOf(host) {
    var img = host.querySelector('img');
    if (img && img.parentElement) return img.parentElement;
    var thumb = host.querySelector(
      'ytm-video-thumbnail-renderer, .video-thumbnail-container-compact, ' +
      '.video-thumbnail-container, .thumbnail-container');
    return thumb || null;
  }

  function applyTo(host, watched) {
    var container = thumbContainerOf(host);
    if (!container) return;
    if (watched) {
      host.classList.add(MARKED_CLASS);
      if (!container.querySelector(':scope > .' + OVERLAY_CLASS)) {
        var cs = getComputedStyle(container);
        if (cs.position === 'static') container.style.position = 'relative';
        var overlay = document.createElement('div');
        overlay.className = OVERLAY_CLASS;
        overlay.setAttribute('aria-hidden', 'true');
        container.appendChild(overlay);
      }
    } else {
      host.classList.remove(MARKED_CLASS);
      var old = container.querySelectorAll(':scope > .' + OVERLAY_CLASS);
      for (var i = 0; i < old.length; i++) old[i].remove();
    }
  }

  function sweep() {
    try {
      var links = document.querySelectorAll('a[href*="watch?v="]');
      for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute('href') || '';
        var m = /[?&]v=([\w-]{11})/.exec(href);
        if (!m) continue;
        var host = links[i].closest(
          'ytm-video-with-context-renderer, ytm-rich-item-renderer, ' +
          'ytm-compact-video-renderer, ytm-playlist-panel-video-renderer, ' +
          'ytm-video-card-renderer, ytm-reel-item-renderer, li') || links[i];
        applyTo(host, IDS.has(m[1]));
      }
    } catch (ignored) {}
  }

  refreshIds();
  injectStyle();
  sweep();
  // Re-read the ledger occasionally so newly watched videos gray out, and
  // re-sweep as feeds lazy-load more items.
  setInterval(function () {
    refreshIds();
    sweep();
  }, 3000);
  setInterval(sweep, 1200);
})();
