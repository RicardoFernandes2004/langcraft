package dev.linguacraft.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Os títulos que o jogador ganha conforme acerta desafios, do menor para o maior. */
public enum PlayerTitle {
	NOVICE("novato", "Novato", 0, ChatFormatting.GRAY),
	APPRENTICE("aprendiz", "Aprendiz", 25, ChatFormatting.GREEN),
	STUDENT("estudante", "Estudante", 100, ChatFormatting.AQUA),
	POLYGLOT("poliglota", "Poliglota", 250, ChatFormatting.BLUE),
	TRANSLATOR("tradutor", "Tradutor", 500, ChatFormatting.LIGHT_PURPLE),
	MASTER("mestre", "Mestre das Línguas", 1000, ChatFormatting.GOLD);

	private final String id;
	private final String fallbackName;
	private final int requiredCorrect;
	private final ChatFormatting color;

	PlayerTitle(String id, String fallbackName, int requiredCorrect, ChatFormatting color) {
		this.id = id;
		this.fallbackName = fallbackName;
		this.requiredCorrect = requiredCorrect;
		this.color = color;
	}

	public static PlayerTitle of(int totalCorrect) {
		PlayerTitle title = NOVICE;
		for (PlayerTitle candidate : values()) {
			if (totalCorrect >= candidate.requiredCorrect) {
				title = candidate;
			}
		}
		return title;
	}

	/** O próximo título, ou nulo no último. */
	public @Nullable PlayerTitle next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	public String id() {
		return id;
	}

	public int requiredCorrect() {
		return requiredCorrect;
	}

	public ChatFormatting color() {
		return color;
	}

	/** O nome no idioma de quem vê. O nome reserva aparece para quem entrou no servidor sem o mod. */
	public Component displayName() {
		return Component.translatableWithFallback("linguacraft.title." + id, fallbackName);
	}

	public Component tag() {
		return Component.literal("[").append(displayName()).append("]").withStyle(color);
	}
}
