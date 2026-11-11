package dev.langcraft.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreasureTest {
	private static final Set<String> ITEMS = Set.of("minecraft:stick", "minecraft:diamond", "minecraft:diamond_sword", "minecraft:iron_ingot",
			"minecraft:raw_iron", "minecraft:iron_block", "magic:wand", "magic:essence", "magic:altar_core");

	@Test
	void recipeScanSeparatesResultsFromIngredientsInAnyFormat() {
		JsonScan.Mentions crafting = JsonScan.recipe(JsonParser.parseString("""
				{"type": "minecraft:crafting_shaped", "key": {"#": "minecraft:stick", "X": "#c:gems/diamond"},
				 "pattern": ["X", "X", "#"], "result": {"id": "minecraft:diamond_sword", "count": 1}}
				"""), ITEMS::contains);
		assertEquals(Set.of("minecraft:diamond_sword"), crafting.outputs());
		assertEquals(List.of("minecraft:stick", "#c:gems/diamond"), crafting.inputs());

		// Um ritual de um mod qualquer, com nomes de campo próprios e uma condição que cita outro item.
		JsonScan.Mentions ritual = JsonScan.recipe(JsonParser.parseString("""
				{"type": "magic:ritual", "neoforge:conditions": [{"type": "item_exists", "item": "minecraft:diamond"}],
				 "catalyst": {"item": "magic:altar_core"}, "reagents": [{"tag": "c:ingots/iron"}, "magic:essence"],
				 "output_item": "magic:wand"}
				"""), ITEMS::contains);
		assertEquals(Set.of("magic:wand"), ritual.outputs());
		assertEquals(List.of("magic:altar_core", "#c:ingots/iron", "magic:essence"), ritual.inputs());
	}

	@Test
	void lootScanSkipsConditionsAndKeepsTags() {
		Set<String> loot = JsonScan.loot(JsonParser.parseString("""
				{"pools": [{"entries": [
				  {"type": "minecraft:item", "name": "minecraft:raw_iron", "conditions": [{"condition": "match_tool", "predicate": {"items": "minecraft:stick"}}]},
				  {"type": "minecraft:tag", "name": "c:gems", "expand": true}
				]}]}
				"""), ITEMS::contains);
		assertEquals(Set.of("minecraft:raw_iron", "#c:gems"), loot);
	}

	@Test
	void advancementScanReadsInventoryCriteria() {
		JsonScan.Advancement advancement = JsonScan.advancement(JsonParser.parseString("""
				{"parent": "magic:root", "criteria": {
				  "has_wand": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "magic:wand"}]}},
				  "killed": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": {"items": "magic:essence"}}}
				}}
				"""), ITEMS::contains);
		assertEquals("magic:root", advancement.parent());
		assertEquals(Set.of("magic:wand"), advancement.items());
	}

	@Test
	void graphFindsReachableItemsStepsAndInheritance() {
		List<RecipeGraph.Recipe> recipes = List.of(
				new RecipeGraph.Recipe(Set.of("minecraft:iron_ingot"), List.of(Set.of("minecraft:raw_iron"))),
				new RecipeGraph.Recipe(Set.of("minecraft:iron_block"), List.of(Set.of("minecraft:iron_ingot"))),
				new RecipeGraph.Recipe(Set.of("minecraft:iron_ingot"), List.of(Set.of("minecraft:iron_block"))),
				new RecipeGraph.Recipe(Set.of("magic:wand"), List.of(Set.of("magic:essence"), Set.of("minecraft:iron_ingot"))),
				new RecipeGraph.Recipe(Set.of("minecraft:diamond_sword"), List.of(Set.of("minecraft:diamond"), Set.of("minecraft:stick"))));
		Map<String, Double> own = new HashMap<>(Map.of("magic:essence", 4.0, "minecraft:raw_iron", 1.0));
		RecipeGraph graph = new RecipeGraph(Set.of("minecraft:raw_iron", "magic:essence", "minecraft:stick"), recipes, own);

		assertEquals(1, graph.nodes().get("minecraft:iron_ingot").depth(), "o caminho curto vence o ciclo com o bloco");
		assertEquals(2, graph.nodes().get("magic:wand").depth());
		assertEquals(4.0, graph.nodes().get("magic:wand").inherited(), 1e-9);
		assertEquals("magic:essence", graph.nodes().get("magic:wand").inheritedFrom());
		assertFalse(graph.nodes().containsKey("minecraft:diamond_sword"), "sem diamante em nenhum loot, a espada não dá para conseguir");
	}

	@Test
	void magicItemsScoreByProgressionEvenWithoutAttributes() {
		TreasureRules.Signals sword = new TreasureRules.Signals(8, 0, 2031, true, 0, 3, 10, 0, 0, false, 0);
		TreasureRules.Signals wand = new TreasureRules.Signals(0, 0, 0, false, 0, 6, 200, 5, 0, false, 0);
		TreasureRules.Signals dirt = TreasureRules.Signals.NONE;
		assertEquals(5, TreasureRules.ownScore(sword), 1e-9);
		assertTrue(TreasureRules.ownScore(wand) >= 4, "receita funda e conquista no fundo da árvore");
		assertEquals(0, TreasureRules.ownScore(dirt), 1e-9);
		assertEquals(5, TreasureRules.progression(new TreasureRules.Signals(0, 0, 0, false, 0, 0, 1, 0, 300, false, 0)), 1e-9);
		assertEquals(0, TreasureRules.advancementPoints(2, 2), 1e-9, "árvore rasa não diz nada");
	}

	@Test
	void tiersFollowTheModpackAndBossDropsAreAtLeastLegendary() {
		Map<String, Double> scores = new HashMap<>();
		for (int i = 0; i < 100; i++) {
			scores.put("mod:item" + i, (double) i);
		}
		scores.put("mod:zero", 0.0);
		scores.put("mod:boss", 1.0);
		Map<String, TreasureTier> tiers = TreasureRules.tiers(scores, Set.of("mod:boss"));
		assertEquals(TreasureTier.MYTHIC, tiers.get("mod:item99"));
		assertEquals(TreasureTier.LEGENDARY, tiers.get("mod:item96"));
		assertEquals(TreasureTier.COMMON, tiers.get("mod:zero"));
		assertEquals(TreasureTier.LEGENDARY, tiers.get("mod:boss"));
		long mythic = tiers.values().stream().filter(tier -> tier == TreasureTier.MYTHIC).count();
		assertEquals(2, mythic, "1% de 102 itens, arredondado para cima");
	}

	@Test
	void luckRaisesTopTiersAndGuaranteesThemAtTheLimit() {
		Map<TreasureTier, Integer> base = new EnumMap<>(Map.of(TreasureTier.COMMON, 50, TreasureTier.RARE, 30, TreasureTier.EPIC, 13,
				TreasureTier.LEGENDARY, 6, TreasureTier.MYTHIC, 1));
		Set<TreasureTier> all = Set.of(TreasureTier.values());
		assertEquals(6.0, TreasureDraw.weights(base, all, 0, 20).get(TreasureTier.LEGENDARY), 1e-9);
		assertEquals(6.0, TreasureDraw.weights(base, all, 9, 20).get(TreasureTier.LEGENDARY), 1e-9);
		assertTrue(TreasureDraw.weights(base, all, 15, 20).get(TreasureTier.LEGENDARY) > 6.0);
		Map<TreasureTier, Double> guaranteed = TreasureDraw.weights(base, all, 19, 20);
		assertEquals(Set.of(TreasureTier.LEGENDARY, TreasureTier.MYTHIC), guaranteed.keySet());
		assertEquals(0.5, TreasureDraw.luck(10, 20), 1e-9);
		assertEquals(0, TreasureDraw.luck(10, 0), 1e-9);
	}

	@Test
	void emptyTiersPassTheirWeightDown() {
		Map<TreasureTier, Integer> base = new EnumMap<>(Map.of(TreasureTier.COMMON, 50, TreasureTier.RARE, 30, TreasureTier.EPIC, 13,
				TreasureTier.LEGENDARY, 6, TreasureTier.MYTHIC, 1));
		Map<TreasureTier, Double> weights = TreasureDraw.weights(base, Set.of(TreasureTier.COMMON, TreasureTier.EPIC), 0, 20);
		assertEquals(Set.of(TreasureTier.COMMON, TreasureTier.EPIC), weights.keySet());
		assertEquals(20.0, weights.get(TreasureTier.EPIC), 1e-9);
		assertEquals(80.0, weights.get(TreasureTier.COMMON), 1e-9);
		assertEquals(Set.of(TreasureTier.COMMON, TreasureTier.EPIC), TreasureDraw.weights(base, Set.of(TreasureTier.COMMON, TreasureTier.EPIC), 19, 20).keySet(),
				"sem lendário nem mítico no modpack, a garantia não esvazia o sorteio");
	}

	@Test
	void recentModsAreSkippedUnlessNothingElseIsLeft() {
		List<String> items = List.of("big:a", "big:b", "big:c", "small:x");
		Random random = new Random(1);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			String picked = TreasureDraw.pickItem(items, true, List.of("big"), random);
			seen.add(picked);
		}
		assertEquals(Set.of("small:x"), seen);
		assertEquals("big:a", TreasureDraw.pickItem(List.of("big:a"), true, List.of("big"), random));
		assertEquals(List.of("c", "b", "a"), TreasureDraw.remember(List.of("b", "a", "z"), "c", 3));
		assertEquals(List.of(), TreasureDraw.remember(List.of("b"), "c", 0));
	}

	@Test
	void byModGivesSmallModsAFairChance() {
		List<String> items = new java.util.ArrayList<>();
		for (int i = 0; i < 99; i++) {
			items.add("big:item" + i);
		}
		items.add("small:x");
		Random random = new Random(7);
		int small = 0;
		for (int i = 0; i < 1000; i++) {
			if (TreasureDraw.pickItem(items, true, List.of(), random).equals("small:x")) {
				small++;
			}
		}
		assertTrue(small > 400 && small < 600, "metade das vezes, e não 1%: " + small);
	}

	@Test
	void stacksDependOnTier() {
		Random random = new Random(3);
		assertEquals(1, TreasureDraw.count(TreasureTier.COMMON, 1, random), "arma e armadura vêm uma");
		int common = TreasureDraw.count(TreasureTier.COMMON, 64, random);
		assertTrue(common >= 16 && common <= 32);
		assertEquals(1, TreasureDraw.count(TreasureTier.MYTHIC, 64, random));
		assertEquals(16, TreasureDraw.count(TreasureTier.COMMON, 16, random) <= 16 ? 16 : 0);
		assertNotEquals(TreasureTier.COMMON.color(), TreasureTier.MYTHIC.color());
	}
}
