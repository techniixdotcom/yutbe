// On watch pages YouTube sometimes defaults the feed below the player to a
// filtered rail (e.g. "From your subscriptions") instead of the suggested/
// similar videos. This re-selects the first chip ("All") once per page so the
// list under the playing video shows related content like stock YouTube.
(function () {
  var appliedFor = null;

  function sweep() {
    try {
      if (!/[?&]v=[\w-]{11}/.test(location.href)) return;
      if (appliedFor === location.href) return;
      var chips = document.querySelectorAll(
        'ytm-chip-cloud-chip-renderer, .chip-cloud-chip, ytm-feed-filter-chip-bar-renderer button');
      if (!chips.length) return;
      var first = chips[0];
      var selected = first.classList.contains('chip-selected')
        || first.getAttribute('aria-selected') === 'true'
        || first.getAttribute('aria-pressed') === 'true';
      if (!selected) {
        first.click();
      }
      appliedFor = location.href;
    } catch (ignored) {}
  }

  sweep();
  setInterval(sweep, 1000);
})();
