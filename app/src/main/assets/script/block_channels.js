// Hides feed items from blocked channels. Matching is by exact channel name
// against channel links inside item renderers.
(function () {
  var BLOCKED = [];

  function refreshBlocked() {
    try {
      BLOCKED = JSON.parse(lite.getBlockedChannels() || '[]')
        .map(function (n) { return String(n).replace(/\s+/g, ' ').trim().toLowerCase(); })
        .filter(Boolean);
    } catch (ignored) {}
  }

  function isBlockedName(text) {
    if (!text) return false;
    var name = text.replace(/\s+/g, ' ').trim().toLowerCase();
    if (!name) return false;
    return BLOCKED.indexOf(name) >= 0;
  }

  function hideRenderer(renderer) {
    renderer.style.display = 'none';
    renderer.setAttribute('aria-hidden', 'true');
  }

  function sweep() {
    if (!BLOCKED.length) return;
    try {
      // Channel names render as links to /@handle or /channel/...
      var links = document.querySelectorAll('a[href^="/@"], a[href*="/channel/"], a[href^="https://m.youtube.com/@"]');
      for (var i = 0; i < links.length; i++) {
        if (!isBlockedName(links[i].textContent)) continue;
        var host = links[i].closest(
          'ytm-video-with-context-renderer, ytm-rich-item-renderer, ' +
          'ytm-compact-video-renderer, ytm-video-card-renderer, ' +
          'ytm-channel-renderer, ytm-reel-item-renderer, ytm-rich-section-renderer, li');
        if (host) hideRenderer(host);
      }
    } catch (ignored) {}
  }

  refreshBlocked();
  sweep();
  setInterval(function () {
    refreshBlocked();
    sweep();
  }, 2000);
})();
