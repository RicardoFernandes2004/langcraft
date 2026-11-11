package dev.langcraft.treasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** O que a classificação lê do jogo. É a parte que muda de uma versão do Minecraft para outra. */
final class TreasureItems {
	static final String RECIPES = "recipe";
	static final String LOOT_TABLES = "loot_table";
	static final String ADVANCEMENTS = "advancement";

	private static final TagKey<Block> NEEDS_NETHERITE_COMMON = TagKey.create(Registries.BLOCK, Identifier.parse("c:needs_netherite_tool"));
	private static final TagKey<Block> NEEDS_NETHERITE_FORGE = TagKey.create(Registries.BLOCK, Identifier.parse("forge:needs_netherite_tool"));
	private static final TagKey<Item> HIDDEN = TagKey.create(Registries.ITEM, Identifier.parse("c:hidden_from_recipe_viewers"));
	private static final TagKey<EntityType<?>> BOSSES_COMMON = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("c:bosses"));
	private static final TagKey<EntityType<?>> BOSSES_FORGE = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("forge:bosses"));

	private TreasureItems() {
	}

	static boolean isItem(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed) && BuiltInRegistries.ITEM.getValue(parsed) != Items.AIR;
	}

	static @Nullable Item item(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed) ? null : BuiltInRegistries.ITEM.getValue(parsed);
	}

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static Set<String> tagMembers(String tagId) {
		Identifier parsed = Identifier.tryParse(tagId);
		if (parsed == null) {
			return Set.of();
		}
		Iterable<Holder<Item>> holders = BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, parsed));
		return StreamSupport.stream(holders.spliterator(), false).map(holder -> id(holder.value())).collect(Collectors.toSet());
	}

	/** Os ids das receitas que o servidor de fato carregou, depois das condições de cada mod. */
	static Set<String> loadedRecipes(MinecraftServer server) {
		return server.getRecipeManager().getRecipes().stream().map(holder -> holder.id().identifier().toString()).collect(Collectors.toSet());
	}

	/** Ovos de mob e o que os mods escondem das listas de receitas nunca saem. */
	static boolean excluded(Item item) {
		return item instanceof SpawnEggItem || item.getDefaultInstance().is(HIDDEN);
	}

	static double attack(ItemStack stack) {
		double damage = sum(stack, Attributes.ATTACK_DAMAGE);
		return damage > 0 ? damage + 1 : 0;
	}

	static double armor(ItemStack stack) {
		return sum(stack, Attributes.ARMOR) + sum(stack, Attributes.ARMOR_TOUGHNESS);
	}

	private static double sum(ItemStack stack, Holder<Attribute> attribute) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double total = 0;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(attribute) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}

	static boolean fireResistant(ItemStack stack) {
		return stack.has(DataComponents.DAMAGE_RESISTANT);
	}

	static int rarity(ItemStack stack) {
		return stack.getRarity().ordinal();
	}

	/** O bloco do item, se ele for um bloco, para ver a ferramenta que o bloco exige. */
	static @Nullable Block block(Item item) {
		return item instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}

	static @Nullable Block block(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null || !BuiltInRegistries.BLOCK.containsKey(parsed) ? null : BuiltInRegistries.BLOCK.getValue(parsed);
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
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(parsed) ? null : BuiltInRegistries.ENTITY_TYPE.getValue(parsed);
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
