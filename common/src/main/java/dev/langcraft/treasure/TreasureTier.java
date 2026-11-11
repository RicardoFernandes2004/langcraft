package dev.langcraft.treasure;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** As faixas do tesouro, da mais comum para a mais rara. */
public enum TreasureTier {
	COMMON(0xFFB8B8B8, 16, 32, 0.60),
	RARE(0xFF55B6FF, 8, 16, 0.25),
	EPIC(0xFFB46CFF, 4, 8, 0.10),
	LEGENDARY(0xFFFFB02E, 1, 3, 0.04),
	MYTHIC(0xFFFF4D6D, 1, 1, 0.01);

	private final int color;
	private final int minCount;
	private final int maxCount;
	/** A parte do modpack que fica nesta faixa, contando a partir da mais rara. */
	private final double share;

	TreasureTier(int color, int minCount, int maxCount, double share) {
		this.color = color;
		this.minCount = minCount;
		this.maxCount = maxCount;
		this.share = share;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int color() {
		return color;
	}

	/** Quantos itens empilháveis vêm de uma vez. Arma, armadura e o que não empilha vêm sempre um. */
	public int minCount() {
		return minCount;
	}

	public int maxCount() {
		return maxCount;
	}

	double share() {
		return share;
	}

	/** Lendário e mítico são os que zeram a sorte acumulada. */
	public boolean isTop() {
		return this == LEGENDARY || this == MYTHIC;
	}

	public static @Nullable TreasureTier byId(String id) {
		for (TreasureTier tier : values()) {
			if (tier.id().equals(id)) {
				return tier;
			}
		}
		return null;
	}
}
