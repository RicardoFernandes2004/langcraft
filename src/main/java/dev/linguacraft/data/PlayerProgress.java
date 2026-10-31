package dev.linguacraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;

/** Idioma, sequência de acertos e preferências, salvos por jogador. */
public record PlayerProgress(String language, int streak, boolean itemsEnabled, boolean mobsEnabled, boolean languageChosen) {
	public static final String KOREAN = "ko_kr";
	public static final String JAPANESE = "ja_jp";
	public static final String BOTH = "both";
	private static final Set<String> VALID_LANGUAGES = Set.of(KOREAN, JAPANESE, BOTH);

	public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("language").forGetter(PlayerProgress::language),
			Codec.INT.fieldOf("streak").forGetter(PlayerProgress::streak),
			Codec.BOOL.fieldOf("itemsEnabled").forGetter(PlayerProgress::itemsEnabled),
			Codec.BOOL.fieldOf("mobsEnabled").forGetter(PlayerProgress::mobsEnabled),
			Codec.BOOL.optionalFieldOf("languageChosen", false).forGetter(PlayerProgress::languageChosen)
	).apply(instance, PlayerProgress::new));

	public static PlayerProgress initial(String language) {
		return new PlayerProgress(language, 0, true, true, false);
	}

	public static boolean isValidLanguage(String language) {
		return VALID_LANGUAGES.contains(language);
	}

	/** Os idiomas individuais que uma escolha representa. */
	public static List<String> languagesOf(String choice) {
		return choice.equals(BOTH) ? List.of(KOREAN, JAPANESE) : List.of(choice);
	}

	public PlayerProgress afterWin() {
		return new PlayerProgress(language, streak + 1, itemsEnabled, mobsEnabled, languageChosen);
	}

	public PlayerProgress afterLoss() {
		return new PlayerProgress(language, 0, itemsEnabled, mobsEnabled, languageChosen);
	}

	public boolean rewardDue(int goal) {
		return goal > 0 && streak >= goal;
	}

	public PlayerProgress afterReward() {
		return new PlayerProgress(language, 0, itemsEnabled, mobsEnabled, languageChosen);
	}

	public PlayerProgress withLanguage(String newLanguage) {
		return new PlayerProgress(newLanguage, streak, itemsEnabled, mobsEnabled, true);
	}

	public PlayerProgress withEnabled(boolean items, boolean mobs) {
		return new PlayerProgress(language, streak, items, mobs, languageChosen);
	}
}
