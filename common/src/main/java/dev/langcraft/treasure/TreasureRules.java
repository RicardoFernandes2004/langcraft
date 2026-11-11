package dev.langcraft.treasure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As contas da classificação, sem nada do jogo, para dar para testar. Cada sinal vira pontos numa escala
 * parecida, em que 4 é o nível da netherita. A nota final é a maior entre poder e progressão, mais o bônus da
 * raridade que o próprio item declara.
 */
public final class TreasureRules {
	private static final double NETHERITE_ATTACK = 8;
	private static final double NETHERITE_ARMOR = 11;
	private static final double NETHERITE_DURABILITY = 2031;
	private static final double TOP_POINTS = 4;
	/** Um mob com essa vida, ou um chefe, faz o que ele dropa nunca ficar abaixo de lendário. */
	public static final double BOSS_HEALTH = 200;
	/** Uma árvore de conquistas mais rasa que isso não diz nada sobre a progressão. */
	private static final int MIN_TREE_DEPTH = 3;

	private TreasureRules() {
	}

	/** Os sinais de um item, já lidos do jogo. */
	public record Signals(double attack, double armor, int durability, boolean fireResistant, int miningTier,
			int recipeDepth, int recipeCost, double advancement, double dropperHealth, boolean bossDrop, int rarity) {
		public static final Signals NONE = new Signals(0, 0, 0, false, 0, 0, 1, 0, 0, false, 0);
	}

	/** Dano, armadura e durabilidade comparados à netherita, mais 1 se não queima. */
	public static double power(Signals signals) {
		double attack = scaled(signals.attack(), NETHERITE_ATTACK);
		double armor = scaled(signals.armor(), NETHERITE_ARMOR);
		double durability = scaled(signals.durability(), NETHERITE_DURABILITY) * 0.75;
		return Math.max(attack, Math.max(armor, durability)) + (signals.fireResistant() ? 1 : 0);
	}

	/** Minério, receita, conquista e quem dropa: o quanto o jogo exige para chegar até o item. */
	public static double progression(Signals signals) {
		double mining = Math.min(TOP_POINTS, signals.miningTier());
		double depth = Math.min(TOP_POINTS, signals.recipeDepth() * 0.7);
		double cost = Math.min(3, Math.log(Math.max(1, signals.recipeCost())) / Math.log(2) / 2);
		double advancement = Math.min(TOP_POINTS + 1, signals.advancement());
		return Math.max(Math.max(mining, depth), Math.max(Math.max(cost, advancement), dropperPoints(signals.dropperHealth(), signals.bossDrop())));
	}

	static double dropperPoints(double health, boolean boss) {
		if (boss || health >= BOSS_HEALTH) {
			return 5;
		}
		if (health >= 100) {
			return 4;
		}
		return health >= 50 ? 2.5 : 0;
	}

	/** A profundidade da conquista dentro da própria árvore, de 0 ao fundo, em pontos. */
	public static double advancementPoints(int depth, int treeDepth) {
		return treeDepth < MIN_TREE_DEPTH ? 0 : (TOP_POINTS + 1) * depth / treeDepth;
	}

	/** A nota do próprio item, sem herança de receita. */
	public static double ownScore(Signals signals) {
		return Math.max(power(signals), progression(signals));
	}

	/** A nota com a herança e o bônus da raridade e do mod. */
	public static double finalScore(double own, double inherited, int rarity, double modBonus) {
		return Math.max(own, inherited) + rarity + modBonus;
	}

	private static double scaled(double value, double netherite) {
		return Math.max(0, TOP_POINTS * value / netherite);
	}

	/**
	 * Divide os itens em faixas pela posição na lista de notas, porque o topo de um modpack é o topo daquele
	 * mundo. Item com nota zero é sempre comum. Os itens marcados como drop de chefe nunca ficam abaixo de lendário.
	 */
	public static Map<String, TreasureTier> tiers(Map<String, Double> scores, Collection<String> bossDrops) {
		List<String> ranked = new ArrayList<>(scores.keySet());
		ranked.sort(Comparator.comparingDouble((String id) -> scores.get(id)).reversed().thenComparing(Comparator.naturalOrder()));
		Map<TreasureTier, Double> thresholds = new EnumMap<>(TreasureTier.class);
		double share = 0;
		TreasureTier[] tiers = TreasureTier.values();
		for (int i = tiers.length - 1; i > 0; i--) {
			share += tiers[i].share();
			int index = Math.min(ranked.size() - 1, (int) Math.ceil(ranked.size() * share) - 1);
			thresholds.put(tiers[i], index < 0 ? Double.MAX_VALUE : scores.get(ranked.get(index)));
		}
		Map<String, TreasureTier> result = new HashMap<>();
		for (String id : ranked) {
			double score = scores.get(id);
			TreasureTier tier = TreasureTier.COMMON;
			if (score > 0) {
				for (int i = tiers.length - 1; i > 0; i--) {
					if (score >= thresholds.get(tiers[i])) {
						tier = tiers[i];
						break;
					}
				}
			}
			if (bossDrops.contains(id) && tier.ordinal() < TreasureTier.LEGENDARY.ordinal()) {
				tier = TreasureTier.LEGENDARY;
			}
			result.put(id, tier);
		}
		return result;
	}
}
