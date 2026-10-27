package de.badstarry.crashfix.mixin;

import de.badstarry.crashfix.repair.ItemStackRepair;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps malformed bundles alive.
 *
 * <p>{@code ItemStackTemplate#validate} (called by {@code create()} and
 * {@code apply(int, DataComponentPatch)}) returns {@code ItemStack.EMPTY} for a
 * template whose data contradicts itself. Every later use of that empty stack -
 * most notably {@code BundleContents$Mutable#toImmutable} - then dies with
 * {@code IllegalStateException: Stack must be non-empty} (MC-306072).
 *
 * <p>This mixin repairs the stack instead of dropping it, so the bundle keeps
 * its content and nothing downstream ever sees an empty stack.
 *
 * <p>Two details matter here:
 * <ul>
 *     <li>the injection uses {@link At#RETURN} and not {@code TAIL}, because only
 *         {@code RETURN} hands the produced value to the
 *         {@link CallbackInfoReturnable};</li>
 *     <li>it sits at the end of the method, so the strict validation only runs
 *         again for data vanilla already rejected.</li>
 * </ul>
 *
 * <p>The repair result is cached per template (see {@link ItemStackRepair}),
 * because the bundle tooltip renderer calls {@code create()} for every entry on
 * every frame while the tooltip is open.
 */
@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateMixin {
	@Inject(method = "validate", at = @At("RETURN"), cancellable = true, require = 1)
	private void crashfix$repairMalformedStack(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (cir.getReturnValue() != ItemStack.EMPTY || stack.isEmpty()) {
			// Vanilla kept the stack, nothing to do.
			return;
		}

		ItemStackRepair.Repair repaired = ItemStackRepair.repair((ItemStackTemplate) (Object) this);
		if (repaired != null) {
			cir.setReturnValue(repaired.toStack());
		}
	}
}