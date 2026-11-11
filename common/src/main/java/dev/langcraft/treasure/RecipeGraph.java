package dev.langcraft.treasure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * As receitas de todos os tipos, inclusive altares e rituais de outros mods, como um grafo de itens. Calcula,
 * a partir do que sai de loot, quantas etapas cada item exige, quanto material cru ele consome e a nota que
 * ele herda do ingrediente mais forte. Um item que nenhum caminho alcança não dá para conseguir no modo sobrevivência.
 */
public final class RecipeGraph {
	private static final int MAX_COST = 100_000;
	private static final int MAX_ROUNDS = 64;

	/** Cada requisito é um ingrediente com as opções que servem para ele, como os itens de uma tag. */
	public record Recipe(Set<String> outputs, List<Set<String>> requirements) {
	}

	public record Node(int depth, int cost, double inherited, @Nullable String inheritedFrom) {
	}

	private final Map<String, Node> nodes = new HashMap<>();

	/**
	 * @param raws o que sai de algum loot, como drops de blocos e mobs e baús de estruturas
	 * @param ownScores a nota de cada item antes da herança. Item que não está aqui conta como zero.
	 */
	public RecipeGraph(Collection<String> raws, Collection<Recipe> recipes, Map<String, Double> ownScores) {
		Map<String, Integer> depth = new HashMap<>();
		Map<String, Integer> cost = new HashMap<>();
		for (String raw : raws) {
			depth.put(raw, 0);
			cost.put(raw, 1);
		}
		relaxPaths(recipes, depth, cost);

		Map<String, Double> score = new HashMap<>();
		depth.keySet().forEach(id -> score.put(id, ownScores.getOrDefault(id, 0.0)));
		Map<String, String> from = new HashMap<>();
		Map<String, Double> inherited = inherit(recipes, ownScores, score, from);
		for (String id : depth.keySet()) {
			nodes.put(id, new Node(depth.get(id), cost.get(id), inherited.getOrDefault(id, 0.0), from.get(id)));
		}
	}

	/** Os itens alcançáveis, ou seja, os que dá para conseguir no modo sobrevivência. */
	public Map<String, Node> nodes() {
		return nodes;
	}

	/** Etapas e custo pelo caminho mais curto, repetindo até nada mudar, porque há receitas em ciclo, como bloco e lingote. */
	private static void relaxPaths(Collection<Recipe> recipes, Map<String, Integer> depth, Map<String, Integer> cost) {
		for (int round = 0; round < MAX_ROUNDS; round++) {
			boolean changed = false;
			for (Recipe recipe : recipes) {
				int recipeDepth = 0;
				long recipeCost = 0;
				boolean reachable = true;
				for (Set<String> options : recipe.requirements()) {
					Integer bestDepth = null;
					Integer bestCost = null;
					for (String option : options) {
						Integer d = depth.get(option);
						if (d != null && (bestDepth == null || d < bestDepth)) {
							bestDepth = d;
						}
						Integer c = cost.get(option);
						if (c != null && (bestCost == null || c < bestCost)) {
							bestCost = c;
						}
					}
					if (bestDepth == null) {
						reachable = false;
						break;
					}
					recipeDepth = Math.max(recipeDepth, bestDepth);
					recipeCost += bestCost;
				}
				if (!reachable) {
					continue;
				}
				int outputDepth = recipe.requirements().isEmpty() ? 0 : recipeDepth + 1;
				int outputCost = (int) Math.min(MAX_COST, Math.max(1, recipeCost));
				for (String output : recipe.outputs()) {
					Integer currentDepth = depth.get(output);
					Integer currentCost = cost.get(output);
					if (currentDepth == null || outputDepth < currentDepth) {
						depth.put(output, outputDepth);
						changed = true;
					}
					if (currentCost == null || outputCost < currentCost) {
						cost.put(output, outputCost);
						changed = true;
					}
				}
			}
			if (!changed) {
				return;
			}
		}
	}

	/**
	 * A nota herdada é a do ingrediente mais forte, na receita mais barata: quem tem um caminho fácil não fica
	 * caro só porque também existe um caminho difícil. Começa das notas próprias e só sobe, então sempre termina.
	 */
	private static Map<String, Double> inherit(Collection<Recipe> recipes, Map<String, Double> ownScores, Map<String, Double> score, Map<String, String> from) {
		Map<String, List<Recipe>> byOutput = new HashMap<>();
		for (Recipe recipe : recipes) {
			for (String output : recipe.outputs()) {
				if (score.containsKey(output)) {
					byOutput.computeIfAbsent(output, unused -> new ArrayList<>()).add(recipe);
				}
			}
		}
		Map<String, Double> inherited = new HashMap<>();
		for (int round = 0; round < MAX_ROUNDS; round++) {
			boolean changed = false;
			for (Map.Entry<String, List<Recipe>> entry : byOutput.entrySet()) {
				double best = Double.MAX_VALUE;
				String bestFrom = null;
				for (Recipe recipe : entry.getValue()) {
					double strongest = 0;
					String strongestId = null;
					boolean reachable = true;
					for (Set<String> options : recipe.requirements()) {
						double cheapest = Double.MAX_VALUE;
						String cheapestId = null;
						for (String option : options) {
							Double value = score.get(option);
							if (value != null && value < cheapest) {
								cheapest = value;
								cheapestId = option;
							}
						}
						if (cheapestId == null) {
							reachable = false;
							break;
						}
						if (cheapest > strongest) {
							strongest = cheapest;
							strongestId = cheapestId;
						}
					}
					if (reachable && strongest < best) {
						best = strongest;
						bestFrom = strongestId;
					}
				}
				if (best == Double.MAX_VALUE) {
					continue;
				}
				String id = entry.getKey();
				double updated = Math.max(ownScores.getOrDefault(id, 0.0), best);
				if (updated > score.get(id) + 1e-9) {
					score.put(id, updated);
					changed = true;
				}
				if (best > inherited.getOrDefault(id, 0.0)) {
					inherited.put(id, best);
					from.put(id, bestFrom);
				}
			}
			if (!changed) {
				break;
			}
		}
		return inherited;
	}
}
