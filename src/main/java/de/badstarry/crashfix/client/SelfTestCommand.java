package de.badstarry.crashfix.client;

import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

/**
 * {@code /crashfix selftest} hands the player a bundle that reproduces MC-306072.
 *
 * <p>Hovering that bundle crashes the game when the mod is disabled, which makes
 * it easy to confirm the fix works.
 */
public final class SelfTestCommand {
	private SelfTestCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("crashfix")
						.then(ClientCommands.literal("selftest")
								.executes(SelfTestCommand::runSelfTest))));
	}

	private static int runSelfTest(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			source.sendError(Component.translatable("crashfix.command.selftest.no_player"));
			return 0;
		}

		if (!client.player.getInventory().add(createBrokenBundle())) {
			source.sendError(Component.translatable("crashfix.command.selftest.inventory_full"));
			return 0;
		}

		source.sendFeedback(Component.translatable("crashfix.command.selftest.given"));
		return 1;
	}

	/**
	 * Builds a bundle holding the two malformed stacks described in MC-306072:
	 * a stackable item that also carries {@code max_damage}, and a stack whose
	 * count exceeds {@code max_stack_size}.
	 */
	public static ItemStack createBrokenBundle() {
		Holder<Item> sword = BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_SWORD);
		Holder<Item> snowball = BuiltInRegistries.ITEM.wrapAsHolder(Items.SNOWBALL);

		ItemStackTemplate damagedStackable = new ItemStackTemplate(sword, 1, DataComponentPatch.builder()
				.set(DataComponents.MAX_STACK_SIZE, 99)
				.set(DataComponents.MAX_DAMAGE, 250)
				.build());

		ItemStackTemplate oversizedCount = new ItemStackTemplate(snowball, 100, DataComponentPatch.builder()
				.set(DataComponents.MAX_STACK_SIZE, 64)
				.build());

		BundleContents contents = new BundleContents(List.of(damagedStackable, oversizedCount));

		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.applyComponents(DataComponentPatch.builder()
				.set(DataComponents.BUNDLE_CONTENTS, contents)
				.build());
		return bundle;
	}
}