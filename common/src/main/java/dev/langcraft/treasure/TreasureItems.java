package dev.langcraft.treasure;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** O que a classificação lê do jogo. É a parte que muda de uma versão do Minecraft para outra. */
final class TreasureItems {
	static final String RECIPES = "recipes";
	static final String LOOT_TABLES = "loot_tables";
	static final String ADVANCEMENTS = "advancements";

	private static final TagKey<Block> NEEDS_NETHERITE_COMMON = TagKey.create(Registries.BLOCK, new ResourceLocation("c:needs_netherite_tool"));
	private static final TagKey<Block> NEEDS_NETHERITE_FORGE = TagKey.create(Registries.BLOCK, new ResourceLocation("forge:needs_netherite_tool"));
	private static final TagKey<Item> HIDDEN = TagKey.create(Registries.ITEM, new ResourceLocation("c:hidden_from_recipe_viewers"));
	private static final TagKey<EntityType<?>> BOSSES_COMMON = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c:bosses"));
	private static final TagKey<EntityType<?>> BOSSES_FORGE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge:bosses"));

	private TreasureItems() {
	}

	static boolean isItem(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed) && BuiltInRegistries.ITEM.get(parsed) != Items.AIR;
	}

	static @Nullable Item item(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		return parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed) ? null : BuiltInRegistries.ITEM.get(parsed);
	}

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static Set<String> tagMembers(String tagId) {
		ResourceLocation parsed = ResourceLocation.tryParse(tagId);
		if (parsed == null) {
			return Set.of();
		}
		Iterable<Holder<Item>> holders = BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, parsed));
		return StreamSupport.stream(holders.spliterator(), false).map(holder -> id(holder.value())).collect(Collectors.toSet());
	}

	/** Os ids das receitas que o servidor de fato carregou, depois das condições de cada mod. */
	static Set<String> loadedRecipes(MinecraftServer server) {
		return server.getRecipeManager().getRecipeIds().map(ResourceLocation::toString).collect(Collectors.toSet());
	}

	/** Ovos de mob e o que os mods escondem das listas de receitas nunca saem. */
	static boolean excluded(Item item) {
		return item instanceof SpawnEggItem || item.getDefaultInstance().is(HIDDEN);
	}

	static double attack(ItemStack stack) {
		double damage = sum(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
		return damage > 0 ? damage + 1 : 0;
	}

	static double armor(ItemStack stack) {
		double total = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.ARMOR) {
				total += sum(stack, Attributes.ARMOR, slot) + sum(stack, Attributes.ARMOR_TOUGHNESS, slot);
			}
		}
		return total;
	}

	private static double sum(ItemStack stack, Attribute attribute, EquipmentSlot slot) {
		Multimap<Attribute, AttributeModifier> modifiers = stack.getItem().getDefaultAttributeModifiers(slot);
		double total = 0;
		for (AttributeModifier modifier : modifiers.get(attribute)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				total += modifier.getAmount();
			}
		}
		return total;
	}

	static boolean fireResistant(ItemStack stack) {
		return stack.getItem().isFireResistant();
	}

	static int rarity(ItemStack stack) {
		return stack.getRarity().ordinal();
	}

	/** O bloco do item, se ele for um bloco, para ver a ferramenta que o bloco exige. */
	static @Nullable Block block(Item item) {
		return item instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}

	static @Nullable Block block(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		return parsed == null || !BuiltInRegistries.BLOCK.containsKey(parsed) ? null : BuiltInRegistries.BLOCK.get(parsed);
	}

	/** 1 para pedra, 2 para ferro, 3 para diamante e 4 para netherita. */
	static int miningTier(Block block) {
		var state = block.defaultBlockState();
		if (state.is(NEEDS_NETHERITE_COMMON) || state.is(NEEDS_NETHERITE_FORGE)) {
			return 4;
		}
		if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
			return 3;
		}
		if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
			return 2;
		}
		return state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
	}

	static @Nullable EntityType<?> entity(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		return parsed == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(parsed) ? null : BuiltInRegistries.ENTITY_TYPE.get(parsed);
	}

	@SuppressWarnings("unchecked")
	static double maxHealth(EntityType<?> type) {
		if (!DefaultAttributes.hasSupplier(type)) {
			return 0;
		}
		return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type).getBaseValue(Attributes.MAX_HEALTH);
	}

	static boolean isBoss(EntityType<?> type) {
		return type.is(BOSSES_COMMON) || type.is(BOSSES_FORGE);
	}

	static List<Item> allItems() {
		List<Item> items = new ArrayList<>();
		BuiltInRegistries.ITEM.forEach(items::add);
		return items;
	}
}
