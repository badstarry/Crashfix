package de.badstarry.crashfix.repair;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.badstarry.crashfix.util.OneShotLog;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
 *
 * <p>Repairs are cached per {@link ItemStackTemplate}: the tooltip renderer
 * calls {@code create()} for every entry on every frame while a bundle is
 * hovered, so the (comparatively expensive) validation has to run only once per
 * distinct template. Templates are immutable records and therefore safe cache
 * keys, and {@link Repair#toStack()} hands out a fresh stack each time so
 * callers can never mutate the cached result.
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

	/** Upper bound so hostile data cannot grow the cache without end. */
	private static final int CACHE_LIMIT = 1024;

	private static final Map<ItemStackTemplate, Repair> CACHE = new ConcurrentHashMap<>();

	/**
	 * The result of a repair: everything needed to build the replacement stack.
	 * The count is the original count, the components are the original patch
	 * minus the contradictory components.
	 */
	public record Repair(Holder<Item> item, int count, DataComponentPatch components) {
		public ItemStack toStack() {
			return new ItemStack(item, count, components);
		}
	}

	private ItemStackRepair() {
	}

	/**
	 * Attempts to compute a repair for a malformed template.
	 *
	 * @return the repair, or {@code null} when the template cannot be repaired
	 */
	public static Repair repair(ItemStackTemplate template) {
		Repair cached = CACHE.get(template);
		if (cached != null) {
			return cached;
		}

		if (CACHE.size() >= CACHE_LIMIT) {
			CACHE.clear();
		}

		Repair repaired = computeRepair(template);
		if (repaired != null) {
			CACHE.put(template, repaired);
		}

		return repaired;
	}

	private static Repair computeRepair(ItemStackTemplate template) {
		Item item = template.item().value();
		if (item == Items.AIR) {
			// Nothing meaningful left to repair; callers have to skip this entry.
			return null;
		}

		DataComponentPatch sanitized = template.components().forget(SUSPECT_COMPONENTS::contains);

		Repair withoutSuspectComponents = build(template.item(), template.count(), sanitized);
		if (withoutSuspectComponents != null) {
			logRepairOnce();
			return withoutSuspectComponents;
		}

		Repair plain = build(template.item(), template.count(), DataComponentPatch.EMPTY);
		if (plain != null) {
			logRepairOnce();
			return plain;
		}

		return null;
	}

	/**
	 * Validates the components with a probe stack of {@code 1} item, because the
	 * requested count may exceed the maximum stack size - which vanilla rejects
	 * but which is exactly what has to be preserved here.
	 */
	private static Repair build(Holder<Item> holder, int requestedCount, DataComponentPatch components) {
		ItemStack probe = new ItemStack(holder, 1, components);
		if (probe.isEmpty() || ItemStack.validateStrict(probe).error().isPresent()) {
			return null;
		}

		return new Repair(holder, Math.max(1, requestedCount), components);
	}

	private static void logRepairOnce() {
		OneShotLog.warnOnce(LOG_KEY,
				"Repaired a malformed item stack (for example a stack with max_stack_size != 1 and max_damage, "
						+ "or a count above max_stack_size). The item and its amount are kept, the bundle keeps "
						+ "working instead of crashing the game.");
	}
}