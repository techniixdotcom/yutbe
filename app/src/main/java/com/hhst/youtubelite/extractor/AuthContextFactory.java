package com.hhst.youtubelite.extractor;

import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory that builds extractor auth snapshots from the WebView cookie store.
 */
@Singleton
public final class AuthContextFactory {

	@Inject
	public AuthContextFactory() {
	}

	@NonNull
	public AuthContext create(@NonNull String url) {
		String cookies = normalize(CookieManager.getInstance().getCookie(url));
		if (cookies == null) {
			cookies = normalize(CookieManager.getInstance().getCookie("https://www.youtube.com"));
		}
		boolean loggedIn = cookies != null
						&& (cookies.contains("SID=") || cookies.contains("__Secure-3PSID="));
		return new AuthContext(
						"webview",
						cookies,
						getCookieValue(cookies, "VISITOR_INFO1_LIVE"),
						null,
						null,
						null,
						loggedIn,
						false,
						System.currentTimeMillis());
	}

	@Nullable
	private String getCookieValue(@Nullable String cookies,
	                              @NonNull String name) {
		if (cookies == null || cookies.isEmpty()) {
			return null;
		}
		String prefix = name + "=";
		for (String part : cookies.split(";")) {
			String trimmed = part.trim();
			if (trimmed.startsWith(prefix) && trimmed.length() > prefix.length()) {
				return trimmed.substring(prefix.length());
			}
		}
		return null;
	}

	@Nullable
	private String normalize(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
