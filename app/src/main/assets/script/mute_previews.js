(() => {
    'use strict';

    // Mute inline feed preview videos while the native player is playing,
    // so preview audio never overlaps playback audio.
    const mutePreviews = () => {
        try {
            if (!globalThis.lite?.isNativePlaying?.()) return;
            document.querySelectorAll('video').forEach((video) => {
                if (!video.muted) video.muted = true;
            });
        } catch (ignored) {
            // bridge not ready yet; retry on next tick
        }
    };

    setInterval(mutePreviews, 800);
})();
