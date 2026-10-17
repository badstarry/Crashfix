package de.badstarry.crashfix.repair;

import java.util.Set;

import de.badstarry.crashfix.util.OneShotLog;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Turns a malformed {@link ItemStack} into a usable one.
 *
 * <p>Minecraft rejects item stacks whose data contradicts itself, for example:
 * <ul>
 *     <li>{@code minecraft:max_stack_size} set to something other than {@code 1}
 *         while {@code minecraft:max_damage} is present;</li>
 *     <li>a {@code count} that is larger than {@code minecraft:max_stack_size}.</li>
 * </ul>
 *
 * <p>{@code ItemStackTemplate#validate} simply throws such stacks away
 * ({@code ItemStack.EMPTY}). Inside a bundle that empty stack is then converted
 * back with {@code ItemStackTemplate#fromNonEmptyStack}, which requires a
 * non-empty stack and crashes the game (MC-306072).
 *
 * <p>Instead of dropping the stack, only the components that contradict each
 * other are removed. The count is deliberately kept as it is, even when it is
 * larger than the maximum stack size, so the bundle still shows the exact
 * amount of items it holds: bundle weight is computed as
 * {@code count / maxStackSize}, which yields exactly the same weight as the
 * equivalent bundle of legitimate, split stacks.
 */
public final class ItemStackRepair {
	/**
	 * Components that describe stack size and durability. They are the ones that
	 * can contradict each other and make {@code ItemStack#validateStrict} fail.
	 */
	private static final Set<DataComponentType<?>> SUSPECT_COMPONENTS = Set.<DataComponentType<?>>of(
			DataComponents.MAX_STACK_SIZE,
			DataComponents.MAX_DAMAGE,
			DataComponents.DAMAGE,
			DataComponents.UNBREAKABLE);

	private static final String LOG_KEY = "repaired-stack";

	private ItemStackRepair() {
	}

	/**
	 * Attempts to build a valid stack out of a malformed one.
	 *
	 * @param broken malformed stack, must not be {@code null}
	 * @return a valid non-empty stack, or {@code null} when the stack cannot be repaired
	 */
	public static ItemStack repair(ItemStack broken) {
		if (broken == null) {
			return null;
		}

		Item item = broken.getItem();
		if (item == Items.AIR) {
			// Nothing meaningful left to repair; callers have to skip this entry.
			return null;
		}

		Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
		DataComponentPatch sanitized = broken.getComponentsPatch().forget(SUSPECT_COMPONENTS::contains);

		ItemStack withoutSuspectComponents = build(holder, sanitized, broken.getCount());
		if (withoutSuspectComponents != null) {
			logRepairOnce();
			return withoutSuspectComponents;
		}

		ItemStack plain = build(holder, DataComponentPatch.EMPTY, broken.getCount());
		if (plain != null) {
			logRepairOnce();
			return plain;
		}

		return null;
	}

	/**
	 * Builds a stack with the given components and the original count.
	 *
	 * <p>The components are validated with a probe stack of {@code 1} item, because the
	 * requested count may exceed the maximum stack size - which vanilla rejects but which
	 * is exactly what has to be preserved here.
	 *
	 * @return a non-empty stack, or {@code null} when even the components are unusable
	 */
	private static ItemStack build(Holder<Item> holder, DataComponentPatch components, int requestedCount) {
		ItemStack probe = new ItemStack(holder, 1, components);
		if (probe.isEmpty() || ItemStack.validateStrict(probe).error().isPresent()) {
			return null;
		}

		return new ItemStack(holder, Math.max(1, requestedCount), components);
	}

	private static void logRepairOnce() {
		OneShotLog.warnOnce(LOG_KEY,
				"Repaired a malformed item stack (for example a stack with max_stack_size != 1 and max_damage, "
						+ "or a count above max_stack_size). The item and its amount are kept, the bundle keeps "
						+ "working instead of crashing the game.");
	}
}