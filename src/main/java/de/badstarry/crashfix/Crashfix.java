package de.badstarry.crashfix;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the mod.
 *
 * <p>The mod is a pure bug fix: it installs two mixins (see
 * {@code crashfix.mixins.json}) that stop malformed item data from reaching
 * {@code ItemStackTemplate#fromNonEmptyStack}, which is where Minecraft 26.2
 * throws {@code java.lang.IllegalStateException: Stack must be non-empty}
 * while rendering the tooltip of a bundle (Mojang bug MC-306072).
 */
public class Crashfix implements ModInitializer {
	public static final String MOD_ID = "crashfix";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Crashfix loaded - guarding malformed item stacks (MC-306072)");
	}
}