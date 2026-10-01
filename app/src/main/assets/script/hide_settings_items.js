// Hides account settings entries we don't want to surface.
(function () {
  var HIDDEN = ['billing and payments', 'purchases and memberships', 'connected apps'];

  function sweep() {
    try {
      var nodes = document.querySelectorAll(
        'ytm-settings-list-item-renderer, ytm-compact-link-renderer, ' +
        '.settings-list-item, a, button, [role="link"], [role="button"]');
      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        var text = (node.textContent || '').trim().toLowerCase();
        if (!text) continue;
        for (var j = 0; j < HIDDEN.length; j++) {
          if (text === HIDDEN[j] || text.indexOf(HIDDEN[j]) === 0) {
            node.style.display = 'none';
            node.setAttribute('aria-hidden', 'true');
            break;
          }
        }
      }
    } catch (ignored) {}
  }

  sweep();
  setInterval(sweep, 1000);
})();
