package de.badstarry.crashfix;

import de.badstarry.crashfix.client.SelfTestCommand;

import net.fabricmc.api.ClientModInitializer;

public class CrashfixClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SelfTestCommand.register();

		Crashfix.LOGGER.info("Crashfix client ready - use /crashfix selftest to create the bundle that used to crash MC-306072");
	}
}