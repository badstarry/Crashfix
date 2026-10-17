package de.badstarry.crashfix.mixin;

import java.util.ArrayList;
import java.util.List;

import de.badstarry.crashfix.util.OneShotLog;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Safety net for bundles that still contain unusable stacks.
 *
 * <p>{@link ItemStackTemplateMixin} already repairs the stack when it is created,
 * but a bundle can still end up with an empty entry (for example an {@code air}
 * entry injected with commands). {@code BundleContents$Mutable#toImmutable} feeds
 * every entry into {@code ItemStackTemplate#fromNonEmptyStack}, which throws
 * {@code IllegalStateException: Stack must be non-empty} on an empty stack -
 * the crash reported as MC-306072.
 *
 * <p>Unusable entries are therefore dropped before the bundle is written back:
 * <ul>
 *     <li>right after the mutable contents are built from an existing bundle;</li>
 *     <li>and once more in {@code toImmutable()}, which is the exact crash site.</li>
 * </ul>
 */
@Mixin(BundleContents.Mutable.class)
public abstract class BundleContentsMutableMixin {
	private static final String LOG_KEY = "dropped-bundle-entry";

	@Shadow
	@Final
	private List<ItemStack> items;

	@Shadow
	private int selectedItem;

	@Inject(
			method = "<init>(Lnet/minecraft/world/item/component/BundleContents;)V",
			at = @At("RETURN"),
			require = 1)
	private void crashfix$dropUnusableEntries(BundleContents contents, CallbackInfo ci) {
		if (!dropEmptyStacks()) {
			return;
		}

		clampSelectedItem();
	}

	@Inject(method = "toImmutable", at = @At("HEAD"), cancellable = true, require = 1)
	private void crashfix$skipUnusableEntries(CallbackInfoReturnable<BundleContents> cir) {
		if (!containsEmptyStack()) {
			return;
		}

		List<ItemStackTemplate> templates = new ArrayList<>(items.size());
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				templates.add(ItemStackTemplate.fromStack(stack));
			}
		}

		logDropOnce(templates.size());

		if (templates.isEmpty()) {
			cir.setReturnValue(BundleContents.EMPTY);
			return;
		}

		// The selected slot index would no longer match, so start without a selection.
		cir.setReturnValue(new BundleContents(templates));
	}

	private boolean containsEmptyStack() {
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private boolean dropEmptyStacks() {
		if (!containsEmptyStack()) {
			return false;
		}

		items.removeIf(ItemStack::isEmpty);
		logDropOnce(items.size());
		return true;
	}

	private void clampSelectedItem() {
		if (selectedItem < 0 || selectedItem >= items.size()) {
			selectedItem = BundleContents.NO_SELECTED_ITEM_INDEX;
		}
	}

	private static void logDropOnce(int kept) {
		OneShotLog.warnOnce(LOG_KEY,
				"Removed unusable item(s) from a bundle while they would have crashed the game "
						+ "(MC-306072). Kept {} item(s).", kept);
	}
}