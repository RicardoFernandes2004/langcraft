package dev.langcraft.treasure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * O sorteio do tesouro, sem nada do jogo. A sorte acumulada sobe a chance de lendário e mítico a partir da
 * metade do limite, até garantir um no limite.
 */
public final class TreasureDraw {
	/** No limite da sorte, a chance de lendário e mítico chega a 10 vezes a normal antes da garantia. */
	private static final double MAX_LUCK_FACTOR = 10;

	private TreasureDraw() {
	}

	/** Quanto da sorte acumulada já foi, de 0 a 1. Com o limite zerado, não há sorte acumulada. */
	public static double luck(int sinceTop, int pity) {
		return pity <= 0 ? 0 : Math.min(1, (double) sinceTop / pity);
	}

	/**
	 * Os pesos que valem agora. O peso de uma faixa sem nenhum item passa para a faixa vazia abaixo dela,
	 * para o sorteio nunca cair em nada. No limite da sorte, só lendário e mítico ficam.
	 */
	public static Map<TreasureTier, Double> weights(Map<TreasureTier, Integer> base, Set<TreasureTier> available, int sinceTop, int pity) {
		Map<TreasureTier, Double> weights = new EnumMap<>(TreasureTier.class);
		double carried = 0;
		TreasureTier[] tiers = TreasureTier.values();
		for (int i = tiers.length - 1; i >= 0; i--) {
			double weight = Math.max(0, base.getOrDefault(tiers[i], 0)) + carried;
			if (available.contains(tiers[i])) {
				weights.put(tiers[i], weight);
				carried = 0;
			} else {
				carried = weight;
			}
		}
		boolean anyTop = weights.keySet().stream().anyMatch(TreasureTier::isTop);
		if (pity > 0 && anyTop) {
			if (sinceTop + 1 >= pity) {
				weights.keySet().removeIf(tier -> !tier.isTop());
			} else if (sinceTop >= pity / 2) {
				double half = pity / 2.0;
				double factor = 1 + (MAX_LUCK_FACTOR - 1) * (sinceTop - half + 1) / (pity - half);
				weights.replaceAll((tier, weight) -> tier.isTop() ? weight * factor : weight);
			}
		}
		return weights;
	}

	public static TreasureTier drawTier(Map<TreasureTier, Double> weights, Random random) {
		double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
		double roll = random.nextDouble() * total;
		TreasureTier last = TreasureTier.COMMON;
		for (Map.Entry<TreasureTier, Double> entry : weights.entrySet()) {
			last = entry.getKey();
			roll -= entry.getValue();
			if (roll < 0) {
				return last;
			}
		}
		return last;
	}

	/**
	 * Um item da faixa, evitando os mods que saíram nos últimos tesouros, a não ser que só sobrem eles. Com
	 * {@code byMod}, primeiro sai o mod e depois o item, para um mod com milhares de itens não dominar.
	 */
	public static String pickItem(List<String> items, boolean byMod, Collection<String> recentMods, Random random) {
		List<String> fresh = items.stream().filter(id -> !recentMods.contains(modOf(id))).toList();
		List<String> pool = fresh.isEmpty() ? items : fresh;
		if (!byMod) {
			return pool.get(random.nextInt(pool.size()));
		}
		Map<String, List<String>> byNamespace = new LinkedHashMap<>();
		for (String id : pool) {
			byNamespace.computeIfAbsent(modOf(id), unused -> new ArrayList<>()).add(id);
		}
		List<List<String>> mods = new ArrayList<>(byNamespace.values());
		List<String> chosen = mods.get(random.nextInt(mods.size()));
		return chosen.get(random.nextInt(chosen.size()));
	}

	/** Os últimos mods, do mais recente para o mais antigo, sem passar da janela. */
	public static List<String> remember(List<String> recentMods, String mod, int window) {
		List<String> updated = new ArrayList<>();
		if (window <= 0) {
			return updated;
		}
		updated.add(mod);
		for (String previous : recentMods) {
			if (updated.size() >= window) {
				break;
			}
			updated.add(previous);
		}
		return updated;
	}

	public static int count(TreasureTier tier, int maxStackSize, Random random) {
		if (maxStackSize <= 1) {
			return 1;
		}
		int amount = tier.minCount() + random.nextInt(tier.maxCount() - tier.minCount() + 1);
		return Math.min(maxStackSize, amount);
	}

	public static String modOf(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? "minecraft" : id.substring(0, colon);
	}
}
