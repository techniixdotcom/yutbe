(function () {
    if (window.yutbeNavBar?.update) {
        window.yutbeNavBar.update();
        return;
    }

    const BAR_ID = "yutbe-nav-bar";
    const STYLE_ID = "yutbe-nav-bar-style";
    const ROOT_CLASS = "yutbe-nav-shown";
    const ICONS = {
        home: "M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z",
        shorts: "M7 3h10a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3zm3 5v8l6-4z",
        subscriptions: "M4 7h16v13H4zm2-4h12v2H6zm4 7v7l5.5-3.5z",
        you: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-8 9c0-3.9 3.6-7 8-7s8 3.1 8 7z"
    };
    const ITEMS = [
        { key: "home", href: "/" },
        { key: "shorts", href: "/shorts" },
        { key: "subscriptions", href: "/feed/subscriptions" },
        { key: "you", href: "/feed/you" }
    ];
    let scheduled = false;

    function preferences() {
        try {
            return JSON.parse(yutbe.getPreferences?.() || "{}");
        } catch {
            return {};
        }
    }

    function labels() {
        try {
            return JSON.parse(yutbe.getNavLabels?.() || "{}");
        } catch {
            return {};
        }
    }

    function ensureStyle() {
        if (document.getElementById(STYLE_ID)) return;
        const target = document.head || document.documentElement;
        if (!target) return;
        const style = document.createElement("style");
        style.id = STYLE_ID;
        style.textContent =
            "#" + BAR_ID + "{position:fixed;left:0;right:0;bottom:0;height:48px;display:flex;z-index:3;" +
            "background:var(--yt-spec-base-background,#0f0f0f);color:var(--yt-spec-text-primary,#f1f1f1);" +
            "border-top:1px solid var(--yt-spec-10-percent-layer,rgba(255,255,255,.1));box-sizing:border-box;}" +
            "#" + BAR_ID + " a{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;" +
            "color:inherit!important;text-decoration:none!important;font-family:Roboto,Arial,sans-serif;font-size:10px;line-height:12px;gap:2px;}" +
            "#" + BAR_ID + " svg{width:24px;height:24px;fill:currentColor;}" +
            "#" + BAR_ID + " a[aria-current=\"page\"]{font-weight:500;}" +
            "html." + ROOT_CLASS + " body{padding-bottom:48px!important;}";
        target.appendChild(style);
    }

    function nativeBarVisible() {
        const bar = document.querySelector("ytm-pivot-bar-renderer");
        if (!bar) return false;
        const style = getComputedStyle(bar);
        if (style.display === "none" || style.visibility === "hidden") return false;
        const rect = bar.getBoundingClientRect();
        return rect.height > 0 && rect.width > 0 && rect.top < window.innerHeight;
    }

    function shouldShow() {
        if (location.pathname.startsWith("/shorts")) return false;
        return !nativeBarVisible();
    }

    function activeKey() {
        const path = location.pathname;
        if (path === "/" || path === "") return "home";
        if (path.startsWith("/feed/subscriptions")) return "subscriptions";
        if (path.startsWith("/feed/you") || path.startsWith("/feed/library")) return "you";
        return null;
    }

    function build() {
        const text = labels();
        const hideShorts = !!preferences().enable_hide_shorts;
        const bar = document.createElement("nav");
        bar.id = BAR_ID;
        const ns = "http://www.w3.org/2000/svg";
        for (const item of ITEMS) {
            if (item.key === "shorts" && hideShorts) continue;
            const link = document.createElement("a");
            link.href = item.href;
            link.dataset.key = item.key;
            const svg = document.createElementNS(ns, "svg");
            svg.setAttribute("viewBox", "0 0 24 24");
            svg.setAttribute("aria-hidden", "true");
            const path = document.createElementNS(ns, "path");
            path.setAttribute("d", ICONS[item.key]);
            svg.appendChild(path);
            const label = document.createElement("span");
            label.textContent = text[item.key] || item.key;
            link.append(svg, label);
            bar.appendChild(link);
        }
        return bar;
    }

    function update() {
        scheduled = false;
        if (!document.body) return;
        ensureStyle();
        let bar = document.getElementById(BAR_ID);
        if (!shouldShow()) {
            bar?.remove();
            document.documentElement.classList.remove(ROOT_CLASS);
            return;
        }
        const hideShorts = !!preferences().enable_hide_shorts;
        const hasShorts = !!bar?.querySelector("a[data-key=\"shorts\"]");
        if (bar && hasShorts === hideShorts) {
            bar.remove();
            bar = null;
        }
        if (!bar) {
            bar = build();
            document.body.appendChild(bar);
        }
        const active = activeKey();
        for (const link of bar.querySelectorAll("a")) {
            if (link.dataset.key === active) link.setAttribute("aria-current", "page");
            else link.removeAttribute("aria-current");
        }
        document.documentElement.classList.add(ROOT_CLASS);
    }

    function schedule() {
        if (scheduled) return;
        scheduled = true;
        setTimeout(update, 200);
    }

    const observer = new MutationObserver(schedule);
    function start() {
        const root = document.body;
        if (!root) {
            setTimeout(start, 100);
            return;
        }
        observer.observe(root, { childList: true, subtree: true });
        update();
    }

    for (const name of ["yutbePreferencesChanged", "onPageFinished", "doUpdateVisitedHistory", "yt-navigate-finish", "state-navigateend", "resize"]) {
        window.addEventListener(name, schedule, true);
    }

    window.yutbeNavBar = { update: schedule };
    start();
})();
