package dev.langcraft.challenge;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Cada tipo de desafio que o jogador pode ligar ou desligar para si. */
public enum ChallengeKind {
	ITEM_SPEAK(SubjectType.ITEM, Minigame.SPEAK),
	ITEM_QUIZ(SubjectType.ITEM, Minigame.QUIZ),
	ITEM_HOLD(SubjectType.ITEM, Minigame.HOLD),
	MOB_SPEAK(SubjectType.MOB, Minigame.SPEAK),
	MOB_QUIZ(SubjectType.MOB, Minigame.QUIZ),
	/** Falar o nome do bioma na primeira vez que o jogador entra nele. */
	BIOME(SubjectType.BIOME, Minigame.SPEAK),
	/** Falar o nome de um item na primeira vez que ele aparece no inventário. */
	NEW_ITEM(SubjectType.ITEM, Minigame.SPEAK);

	private final SubjectType type;
	private final Minigame minigame;

	ChallengeKind(SubjectType type, Minigame minigame) {
		this.type = type;
		this.minigame = minigame;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public SubjectType type() {
		return type;
	}

	public Minigame minigame() {
		return minigame;
	}

	/** Os desafios dos intervalos, ou seja, todos menos as descobertas. */
	public boolean timed() {
		return this != BIOME && this != NEW_ITEM;
	}

	/** O desafio de intervalo de um tipo e minigame. */
	public static @Nullable ChallengeKind timed(SubjectType type, Minigame minigame) {
		for (ChallengeKind kind : values()) {
			if (kind.timed() && kind.type == type && kind.minigame == minigame) {
				return kind;
			}
		}
		return null;
	}

	public static @Nullable ChallengeKind byId(String id) {
		for (ChallengeKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
