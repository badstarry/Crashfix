package de.badstarry.crashfix.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.badstarry.crashfix.Crashfix;

/**
 * Logs a warning the first time it happens and everything else at debug level.
 *
 * <p>Kept outside of the mixin classes on purpose: Mixin does not merge field
 * initialisers into its target, so the state has to live in a normal class.
 */
public final class OneShotLog {
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private OneShotLog() {
	}

	public static void warnOnce(String key, String message, Object... args) {
		if (WARNED.add(key)) {
			Crashfix.LOGGER.warn(message, args);
			return;
		}

		Crashfix.LOGGER.debug(message, args);
	}
}