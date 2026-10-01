// Replaces the YouTube wordmark in the mobile top bar with a YuTbe mark.
(function () {
  var MARK_ID = 'yutbe-logo-mark';

  function buildMark() {
    var span = document.createElement('span');
    span.id = MARK_ID;
    span.style.cssText =
      'display:inline-flex;align-items:center;gap:6px;' +
      'font-family:Roboto,Arial,sans-serif;font-size:17px;font-weight:600;' +
      'letter-spacing:-0.5px;color:var(--yt-spec-text-primary,#fff);';
    var badge = document.createElement('span');
    badge.style.cssText =
      'display:inline-flex;align-items:center;justify-content:center;' +
      'width:22px;height:22px;border-radius:6px;background:#000;' +
      'border:1px solid rgba(128,128,128,.35);';
    badge.innerHTML =
      '<svg viewBox="0 0 24 24" width="13" height="13" style="display:block">' +
      '<path fill="#fff" d="M5 7.5Q5 5 8 4.8L16 4.8Q19 5 19 7.5L19 16.5Q19 19 16 19.2L8 19.2Q5 19 5 16.5Z"/>' +
      '<path d="M10.2 8.6l5.4 3.4-5.4 3.4z" fill="#000"/></svg>';
    var label = document.createElement('span');
    label.textContent = 'YuTbe';
    span.appendChild(badge);
    span.appendChild(label);
    return span;
  }

  function replaceContent(node) {
    if (!node || node.querySelector('#' + MARK_ID)) return;
    while (node.firstChild) node.removeChild(node.firstChild);
    node.appendChild(buildMark());
    node.setAttribute('aria-label', 'YuTbe');
  }

  function sweep() {
    try {
      if (document.getElementById(MARK_ID)) return;
      // YouTube renders the wordmark as a yoodle/logo renderer inside a link
      var logo = document.querySelector(
        'ytm-yoodle-renderer, ytm-logo, .ytm-mobile-topbar-renderer-logo, ' +
        'ytm-mobile-topbar-renderer a#logo, #logo');
      if (!logo) return;
      // replace the whole clickable container so no original art remains
      var host = logo.closest('a, button, ytm-topbar-logo-renderer') || logo;
      // keep the element box so the header layout stays intact
      replaceContent(host);
    } catch (ignored) {}
  }

  sweep();
  setInterval(sweep, 800);
})();
