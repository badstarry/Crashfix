package de.badstarry.crashfix.mixin;

import de.badstarry.crashfix.util.OneShotLog;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Last line of defence, directly at the documented crash site.
 *
 * <p>{@code ItemStackTemplate#fromNonEmptyStack(ItemStack)} is the method that throws
 * {@code IllegalStateException: Stack must be non-empty} (MC-306072) whenever an empty
 * stack reaches it, no matter which code path produced that stack. Its bytecode is
 * identical in 26.2 and 26.3, so guarding it here keeps the net in place across
 * versions even though the surrounding classes ({@code BundleContents$Mutable} and
 * friends) were refactored in 26.3.
 *
 * <p>{@link ItemStackTemplateMixin} repairs every malformed stack at
 * {@code validate()} time, so this only ever fires for entries that cannot be
 * repaired at all (for example an {@code air} entry injected with commands). Those
 * are represented as a barrier placeholder instead of crashing the game, because
 * Minecraft's constructors refuse to build an {@code air} template.
 */
@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateEmptyGuardMixin {
	@Inject(
			method = "fromNonEmptyStack(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStackTemplate;",
			at = @At("HEAD"),
			cancellable = true,
			require = 1)
	private static void crashfix$neverThrowOnEmptyStack(ItemStack stack, CallbackInfoReturnable<ItemStackTemplate> cir) {
		if (stack == null || !stack.isEmpty()) {
			return;
		}

		OneShotLog.warnOnce("empty-stack-guard",
				"A bundle contained an item entry that could not be represented (it would have crashed the game, "
						+ "MC-306072). The entry was replaced with a barrier placeholder.");

		cir.setReturnValue(new ItemStackTemplate(Items.BARRIER));
	}
}