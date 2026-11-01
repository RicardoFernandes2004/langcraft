package dev.langcraft.challenge;

import dev.langcraft.LangCraft;
import dev.langcraft.data.PlayerProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Aplica no jogador o efeito sorteado em uma roleta. */
final class RouletteEffects {
	private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final Set<String> AMBUSH_TIERS = Set.of("hostile", "dangerous");
	private static final Set<String> BOSS_TIERS = Set.of("extreme");
	private static final int HOTBAR_SIZE = 9;
	private static final int STACKS_LOST = 3;
	private static final int AMBUSH_MOBS = 3;
	private static final int CURSE_TICKS = 180 * 20;
	private static final int BLESSING_TICKS = 300 * 20;
	private static final int NEAR_DEATH_HEALTH = 2;
	private static final int NEAR_DEATH_FOOD = 2;
	private static final int SHIELDS_GIVEN = 2;
	private static final int LEVELS_GIVEN = 15;

	private RouletteEffects() {
	}

	static void apply(ServerPlayer player, RouletteEffect effect, List<MobPunishment.Tier> tiers, Random random) {
		boolean applied = switch (effect) {
			case NOTHING -> true;
			case WEAR_GEAR -> wearGear(player);
			case LOSE_HALF_LEVELS -> loseLevels(player, (player.experienceLevel + 1) / 2);
			case LOSE_ALL_LEVELS -> loseLevels(player, player.experienceLevel);
			case CURSE -> curse(player);
			case LOSE_STACKS -> loseRandomStacks(player, STACKS_LOST, random);
			case LOSE_HALF_INVENTORY -> loseRandomStacks(player, (InventoryPicker.occupiedSlots(player).size() + 1) / 2, random);
			case LOSE_HOTBAR -> loseHotbar(player);
			case LOSE_ARMOR -> loseArmor(player);
			case LOSE_EVERYTHING -> loseEverything(player);
			case NEAR_DEATH -> nearDeath(player);
			case DEATH -> {
				player.kill();
				yield true;
			}
			case MOB_AMBUSH -> {
				if (!spawn(player, tiers, AMBUSH_TIERS, AMBUSH_MOBS, random)) {
					// No Pacífico não nasce monstro, e a roleta não pode virar sorte por causa disso.
					apply(player, RouletteEffect.LOSE_STACKS, tiers, random);
					yield false;
				}
				yield true;
			}
			case BOSS -> {
				if (!spawn(player, tiers, BOSS_TIERS, 1, random)) {
					apply(player, RouletteEffect.LOSE_HALF_INVENTORY, tiers, random);
					yield false;
				}
				yield true;
			}
			case DIAMONDS -> give(player, Items.DIAMOND, 5);
			case GOLDEN_APPLES -> give(player, Items.GOLDEN_APPLE, 4);
			case TOTEM -> give(player, Items.TOTEM_OF_UNDYING, 1);
			case NETHERITE -> give(player, Items.NETHERITE_INGOT, 1);
			case ENCHANTED_APPLE -> give(player, Items.ENCHANTED_GOLDEN_APPLE, 1);
			case LEVELS -> {
				player.giveExperienceLevels(LEVELS_GIVEN);
				yield true;
			}
			case BLESSING -> bless(player);
			case SHIELDS -> {
				PlayerProgress progress = LangCraft.progress(player);
				LangCraft.setProgress(player, progress.withShields(Math.min(LangCraft.config.maxShields, progress.shields() + SHIELDS_GIVEN)));
				yield true;
			}
		};
		if (applied) {
			player.sendSystemMessage(Component.translatable("langcraft.effect." + effect.id() + ".desc").withStyle(ChatFormatting.YELLOW));
		}
	}

	private static boolean spared(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("langcraft.effect.spared").withStyle(ChatFormatting.GRAY));
		return false;
	}

	private static boolean loseRandomStacks(ServerPlayer player, int count, Random random) {
		List<Integer> slots = new ArrayList<>(InventoryPicker.occupiedSlots(player));
		if (slots.isEmpty()) {
			return spared(player);
		}
		Collections.shuffle(slots, random);
		for (int slot : slots.subList(0, Math.min(count, slots.size()))) {
			player.getInventory().setItem(slot, ItemStack.EMPTY);
		}
		player.getInventory().setChanged();
		return true;
	}

	private static boolean loseHotbar(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		boolean any = false;
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			any |= !inventory.getItem(slot).isEmpty();
			inventory.setItem(slot, ItemStack.EMPTY);
		}
		inventory.setChanged();
		return any || spared(player);
	}

	private static boolean loseArmor(ServerPlayer player) {
		boolean any = false;
		for (EquipmentSlot slot : ARMOR) {
			any |= !player.getItemBySlot(slot).isEmpty();
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		return any || spared(player);
	}

	private static boolean loseEverything(ServerPlayer player) {
		player.getInventory().clearContent();
		player.getInventory().setChanged();
		return true;
	}

	/** Tira metade da durabilidade que resta da armadura e do que está na mão, sem chegar a quebrar. */
	private static boolean wearGear(ServerPlayer player) {
		boolean any = false;
		List<EquipmentSlot> slots = new ArrayList<>(ARMOR);
		slots.add(EquipmentSlot.MAINHAND);
		for (EquipmentSlot slot : slots) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isDamageableItem()) {
				stack.setDamageValue(stack.getDamageValue() + (stack.getMaxDamage() - stack.getDamageValue()) / 2);
				any = true;
			}
		}
		return any || spared(player);
	}

	private static boolean loseLevels(ServerPlayer player, int levels) {
		if (levels <= 0) {
			return spared(player);
		}
		player.giveExperienceLevels(-levels);
		return true;
	}

	private static boolean curse(ServerPlayer player) {
		addEffect(player, MobEffects.MOVEMENT_SLOWDOWN, CURSE_TICKS, 1);
		addEffect(player, MobEffects.WEAKNESS, CURSE_TICKS, 1);
		addEffect(player, MobEffects.DIG_SLOWDOWN, CURSE_TICKS, 1);
		addEffect(player, MobEffects.HUNGER, CURSE_TICKS, 0);
		return true;
	}

	private static boolean bless(ServerPlayer player) {
		addEffect(player, MobEffects.DIG_SPEED, BLESSING_TICKS, 1);
		addEffect(player, MobEffects.MOVEMENT_SPEED, BLESSING_TICKS, 0);
		addEffect(player, MobEffects.LUCK, BLESSING_TICKS, 0);
		addEffect(player, MobEffects.DAMAGE_RESISTANCE, BLESSING_TICKS, 0);
		return true;
	}

	private static void addEffect(ServerPlayer player, MobEffect effect, int ticks, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, ticks, amplifier));
	}

	private static boolean nearDeath(ServerPlayer player) {
		player.setHealth(Math.min(player.getHealth(), NEAR_DEATH_HEALTH));
		player.getFoodData().setFoodLevel(Math.min(player.getFoodData().getFoodLevel(), NEAR_DEATH_FOOD));
		player.getFoodData().setSaturation(0);
		return true;
	}

	private static boolean spawn(ServerPlayer player, List<MobPunishment.Tier> tiers, Set<String> names, int count, Random random) {
		List<MobPunishment.Tier> chosen = tiers.stream().filter(tier -> names.contains(tier.name())).toList();
		boolean any = false;
		for (int i = 0; i < count; i++) {
			Optional<String> mob = MobPunishment.draw(chosen, id -> MobPunishment.canSpawn(id, player.serverLevel()), random);
			if (mob.isPresent()) {
				MobPunishment.spawnAbove(player, mob.get());
				any = true;
			}
		}
		return any;
	}

	private static boolean give(ServerPlayer player, Item item, int count) {
		player.getInventory().placeItemBackInInventory(new ItemStack(item, count));
		return true;
	}
}
