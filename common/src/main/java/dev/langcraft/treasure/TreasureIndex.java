package dev.langcraft.treasure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A classificação de todos os itens que podem sair no tesouro. É feita uma vez por servidor, fora da thread
 * do jogo, e gravada em {@code treasure.json} para o admin conferir e ajustar.
 */
public final class TreasureIndex {
	public record Entry(String id, TreasureTier tier, double score, List<String> reasons) {
	}

	private final Map<String, Entry> entries;
	private final Map<TreasureTier, List<String>> byTier = new EnumMap<>(TreasureTier.class);

	private TreasureIndex(Map<String, Entry> entries) {
		this.entries = entries;
		for (TreasureTier tier : TreasureTier.values()) {
			byTier.put(tier, new ArrayList<>());
		}
		entries.values().forEach(entry -> byTier.get(entry.tier()).add(entry.id()));
	}

	public @Nullable Entry entry(String id) {
		return entries.get(id);
	}

	public List<String> items(TreasureTier tier) {
		return byTier.get(tier);
	}

	public Set<TreasureTier> available() {
		Set<TreasureTier> available = new HashSet<>();
		byTier.forEach((tier, items) -> {
			if (!items.isEmpty()) {
				available.add(tier);
			}
		});
		return available;
	}

	public int size() {
		return entries.size();
	}

	/** Monta a classificação. Os ajustes do admin no arquivo valem por cima do que foi calculado. */
	public static TreasureIndex build(MinecraftServer server, TreasureFile file, Collection<String> blockedForAll) {
		long started = System.currentTimeMillis();
		Map<String, Set<String>> tagCache = new HashMap<>();

		Set<String> loaded = TreasureItems.loadedRecipes(server);
		List<RecipeGraph.Recipe> recipes = new ArrayList<>();
		read(server, TreasureItems.RECIPES, (id, json) -> {
			if (!loaded.contains(id)) {
				return;
			}
			JsonScan.Mentions mentions = JsonScan.recipe(json, TreasureItems::isItem);
			if (mentions.outputs().isEmpty()) {
				return;
			}
			List<Set<String>> requirements = new ArrayList<>();
			for (String input : mentions.inputs()) {
				Set<String> options = resolve(input, tagCache);
				if (options.isEmpty()) {
					// Uma tag vazia não deixa a receita ser feita.
					return;
				}
				requirements.add(options);
			}
			recipes.add(new RecipeGraph.Recipe(mentions.outputs(), requirements));
		});

		Set<String> raws = new HashSet<>();
		Map<String, Integer> blockTier = new HashMap<>();
		Map<String, Double> dropperHealth = new HashMap<>();
		Map<String, String> dropper = new HashMap<>();
		Set<String> bossDrops = new HashSet<>();
		read(server, TreasureItems.LOOT_TABLES, (id, json) -> {
			Set<String> items = new LinkedHashSet<>();
			for (String reference : JsonScan.loot(json, TreasureItems::isItem)) {
				items.addAll(resolve(reference, tagCache));
			}
			raws.addAll(items);
			ResourceLocation parsed = new ResourceLocation(id);
			String path = parsed.getPath();
			if (path.startsWith("blocks/")) {
				Block block = TreasureItems.block(parsed.getNamespace() + ":" + path.substring("blocks/".length()));
				if (block != null) {
					int tier = TreasureItems.miningTier(block);
					items.forEach(item -> blockTier.merge(item, tier, Math::max));
				}
			} else if (path.startsWith("entities/")) {
				String entityPath = path.substring("entities/".length());
				int slash = entityPath.indexOf('/');
				String entityId = parsed.getNamespace() + ":" + (slash < 0 ? entityPath : entityPath.substring(0, slash));
				EntityType<?> type = TreasureItems.entity(entityId);
				if (type != null) {
					double health = TreasureItems.maxHealth(type);
					boolean boss = TreasureItems.isBoss(type);
					for (String item : items) {
						if (health > dropperHealth.getOrDefault(item, 0.0)) {
							dropperHealth.put(item, health);
							dropper.put(item, entityId);
						}
						if (boss || health >= TreasureRules.BOSS_HEALTH) {
							bossDrops.add(item);
						}
					}
				}
			}
		});

		Map<String, Double> advancement = advancementPoints(server, tagCache);

		// Primeiro só os caminhos, para saber o que dá para conseguir, a quantas etapas e com que custo.
		RecipeGraph paths = new RecipeGraph(raws, recipes, Map.of());
		Map<String, TreasureRules.Signals> signals = new HashMap<>();
		Map<String, Double> own = new HashMap<>();
		Set<String> bannedMods = new HashSet<>(file.bannedMods);
		for (Map.Entry<String, RecipeGraph.Node> node : paths.nodes().entrySet()) {
			String id = node.getKey();
			Item item = TreasureItems.item(id);
			if (item == null || TreasureItems.excluded(item)) {
				continue;
			}
			ItemStack stack = item.getDefaultInstance();
			Block block = TreasureItems.block(item);
			int mining = Math.max(blockTier.getOrDefault(id, 0), block == null ? 0 : TreasureItems.miningTier(block));
			TreasureRules.Signals itemSignals = new TreasureRules.Signals(TreasureItems.attack(stack), TreasureItems.armor(stack), stack.getMaxDamage(),
					TreasureItems.fireResistant(stack), mining, node.getValue().depth(), node.getValue().cost(), advancement.getOrDefault(id, 0.0),
					dropperHealth.getOrDefault(id, 0.0), bossDrops.contains(id), TreasureItems.rarity(stack));
			signals.put(id, itemSignals);
			own.put(id, TreasureRules.ownScore(itemSignals));
		}
		// Depois a herança: a espada de netherita vale o que vale o lingote de netherita.
		RecipeGraph inheritance = new RecipeGraph(raws, recipes, own);

		Map<String, Double> scores = new HashMap<>();
		for (Map.Entry<String, TreasureRules.Signals> entry : signals.entrySet()) {
			String id = entry.getKey();
			String mod = TreasureDraw.modOf(id);
			if (file.banned.contains(id) || bannedMods.contains(mod) || blockedForAll.contains(id)) {
				continue;
			}
			RecipeGraph.Node node = inheritance.nodes().get(id);
			double inherited = node == null ? 0 : node.inherited();
			scores.put(id, TreasureRules.finalScore(own.get(id), inherited, entry.getValue().rarity(), file.modBonus.getOrDefault(mod, 0.0)));
		}
		Map<String, TreasureTier> tiers = TreasureRules.tiers(scores, bossDrops);

		Map<String, Entry> entries = new LinkedHashMap<>();
		for (Map.Entry<String, Double> score : scores.entrySet()) {
			String id = score.getKey();
			TreasureTier tier = tiers.get(id);
			List<String> reasons = reasons(signals.get(id), inheritance.nodes().get(id), dropper.get(id));
			TreasureTier forced = file.overrides.containsKey(id) ? TreasureTier.byId(file.overrides.get(id).toLowerCase(Locale.ROOT)) : null;
			if (forced != null) {
				tier = forced;
				reasons.add(0, "override");
			}
			entries.put(id, new Entry(id, tier, score.getValue(), reasons));
		}

		file.items = new LinkedHashMap<>();
		entries.values().stream()
				.sorted((a, b) -> a.tier() != b.tier() ? b.tier().compareTo(a.tier()) : Double.compare(b.score(), a.score()))
				.forEach(entry -> file.items.put(entry.id(), new TreasureFile.Entry(entry.tier().id(), Math.round(entry.score() * 100) / 100.0, entry.reasons())));
		file.save();
		LangCraft.LOGGER.info("[tesouro] {} itens classificados em {} ms, com {} receitas", entries.size(), System.currentTimeMillis() - started, recipes.size());
		return new TreasureIndex(entries);
	}

	/** Os motivos curtos de cada nota, para o admin entender e corrigir a classificação. */
	private static List<String> reasons(TreasureRules.Signals signals, @Nullable RecipeGraph.Node node, @Nullable String dropper) {
		List<String> reasons = new ArrayList<>();
		if (signals.attack() > 0) {
			reasons.add("attack " + round(signals.attack()));
		}
		if (signals.armor() > 0) {
			reasons.add("armor " + round(signals.armor()));
		}
		if (signals.durability() > 0) {
			reasons.add("durability " + signals.durability());
		}
		if (signals.fireResistant()) {
			reasons.add("fire resistant");
		}
		if (signals.miningTier() > 0) {
			reasons.add("mining tier " + signals.miningTier());
		}
		if (signals.recipeDepth() > 0) {
			reasons.add("recipe steps " + signals.recipeDepth() + ", cost " + signals.recipeCost());
		}
		if (signals.advancement() > 0) {
			reasons.add("advancement " + round(signals.advancement()));
		}
		if (dropper != null) {
			reasons.add("dropped by " + dropper + " (" + round(signals.dropperHealth()) + " hp)" + (signals.bossDrop() ? ", boss" : ""));
		}
		if (signals.rarity() > 0) {
			reasons.add("rarity +" + signals.rarity());
		}
		if (node != null && node.inheritedFrom() != null && node.inherited() > TreasureRules.ownScore(signals)) {
			reasons.add("inherits " + node.inheritedFrom());
		}
		return reasons;
	}

	private static String round(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	/**
	 * Cada item citado num critério de "ter no inventário" ganha pontos pela profundidade da conquista na
	 * própria árvore. Uma árvore funda, como a de um mod de magia, segue a progressão do mod.
	 */
	private static Map<String, Double> advancementPoints(MinecraftServer server, Map<String, Set<String>> tagCache) {
		Map<String, JsonScan.Advancement> advancements = new HashMap<>();
		read(server, TreasureItems.ADVANCEMENTS, (id, json) -> advancements.put(id, JsonScan.advancement(json, TreasureItems::isItem)));
		Map<String, Integer> depth = new HashMap<>();
		Map<String, String> root = new HashMap<>();
		for (String id : advancements.keySet()) {
			walkDepth(id, advancements, depth, root, new HashSet<>());
		}
		Map<String, Integer> treeDepth = new HashMap<>();
		depth.forEach((id, value) -> treeDepth.merge(root.get(id), value, Math::max));
		Map<String, Double> points = new HashMap<>();
		advancements.forEach((id, advancement) -> {
			double value = TreasureRules.advancementPoints(depth.get(id), treeDepth.get(root.get(id)));
			if (value > 0) {
				for (String reference : advancement.items()) {
					for (String item : resolve(reference, tagCache)) {
						points.merge(item, value, Math::max);
					}
				}
			}
		});
		return points;
	}

	private static int walkDepth(String id, Map<String, JsonScan.Advancement> advancements, Map<String, Integer> depth, Map<String, String> root, Set<String> visiting) {
		Integer known = depth.get(id);
		if (known != null) {
			return known;
		}
		JsonScan.Advancement advancement = advancements.get(id);
		String parent = advancement == null ? null : advancement.parent();
		int value = 0;
		String top = id;
		if (parent != null && advancements.containsKey(parent) && visiting.add(id)) {
			value = walkDepth(parent, advancements, depth, root, visiting) + 1;
			top = root.get(parent);
		}
		depth.put(id, value);
		root.put(id, top);
		return value;
	}

	private static Set<String> resolve(String reference, Map<String, Set<String>> tagCache) {
		if (JsonScan.isTag(reference)) {
			return tagCache.computeIfAbsent(JsonScan.tagId(reference), TreasureItems::tagMembers);
		}
		return Set.of(reference);
	}

	/** Lê todos os JSON de uma pasta dos dados. Um arquivo quebrado de algum mod só fica de fora. */
	private static void read(MinecraftServer server, String folder, BiConsumer<String, JsonElement> consumer) {
		Map<ResourceLocation, Resource> resources = server.getResourceManager().listResources(folder, path -> path.getPath().endsWith(".json"));
		for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
			ResourceLocation file = entry.getKey();
			String path = file.getPath().substring(folder.length() + 1, file.getPath().length() - ".json".length());
			try (Reader reader = entry.getValue().openAsReader()) {
				consumer.accept(file.getNamespace() + ":" + path, JsonParser.parseReader(reader));
			} catch (IOException | JsonParseException | IllegalStateException e) {
				LangCraft.LOGGER.debug("[tesouro] {} não foi lido", file, e);
			}
		}
	}
}
