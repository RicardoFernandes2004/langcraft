package dev.langcraft.challenge;

import java.util.Locale;

/** O que uma fatia das roletas diária, de desastre e de jackpot faz. O texto de cada um fica em {@code langcraft.effect.<id>}. */
public enum RouletteEffect {
	NOTHING,
	WEAR_GEAR,
	LOSE_HALF_LEVELS,
	CURSE,
	LOSE_STACKS,
	MOB_AMBUSH,
	NEAR_DEATH,
	LOSE_ALL_LEVELS,
	LOSE_ARMOR,
	LOSE_HOTBAR,
	LOSE_HALF_INVENTORY,
	BOSS,
	DEATH,
	LOSE_EVERYTHING,
	DIAMONDS,
	LEVELS,
	GOLDEN_APPLES,
	BLESSING,
	SHIELDS,
	TOTEM,
	NETHERITE,
	ENCHANTED_APPLE;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Matar ou cercar de mobs quem está saindo do jogo não funciona, então esses efeitos são trocados na saída. */
	public boolean needsPlayerOnline() {
		return this == MOB_AMBUSH || this == BOSS || this == DEATH;
	}
}
