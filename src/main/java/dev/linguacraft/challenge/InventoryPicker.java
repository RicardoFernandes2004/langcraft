package dev.linguacraft.challenge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Consulta os slots que podem entrar nos desafios: inventário, hotbar e mão secundária. */
public final class InventoryPicker {
	private InventoryPicker() {
	}

	private static List<Integer> slots() {
		List<Integer> slots = new ArrayList<>(Inventory.INVENTORY_SIZE + 1);
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			slots.add(i);
		}
		slots.add(Inventory.SLOT_OFFHAND);
		return slots;
	}

	public static List<Item> itemTypes(ServerPlayer player, Collection<String> excludedIds) {
		Set<Item> types = new LinkedHashSet<>();
		for (int slot : slots()) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && !excludedIds.contains(idOf(stack.getItem()))) {
				types.add(stack.getItem());
			}
		}
		return new ArrayList<>(types);
	}

	/** Primeira pilha do tipo, ou uma pilha vazia se o jogador não tiver mais o item. */
	public static ItemStack firstStack(ServerPlayer player, Item item) {
		for (int slot : slots()) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public static List<ItemStack> stacks(ServerPlayer player, Predicate<ItemStack> filter) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot : slots()) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && filter.test(stack)) {
				stacks.add(stack);
			}
		}
		return stacks;
	}

	public static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
