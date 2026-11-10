package dev.langcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Idioma, placar e preferências, salvos por jogador. */
public record PlayerProgress(String language, int streak, boolean itemsEnabled, boolean mobsEnabled, boolean languageChosen, int bestStreak, int totalCorrect, int totalWrong,
		int lossStreak, int lastDailyDay, int shields, Preferences preferences, Discoveries discoveries) {
	public static final String KOREAN = "ko_kr";
	public static final String JAPANESE = "ja_jp";
	public static final String ENGLISH = "en_us";
	public static final String GERMAN = "de_de";
	public static final String SPANISH = "es_es";
	/** Coreano e japonês. É o valor salvo nos mundos antigos, de quando só havia esses dois. */
	public static final String BOTH = "both";
	/** A roleta diária ainda não saiu nenhuma vez. */
	public static final int NO_DAILY_YET = -1;
	private static final String SEPARATOR = ",";

	private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf().xmap(LinkedHashSet::new, ArrayList::new);

	/** Os desafios que o jogador desligou e os itens que ele bloqueou para si. */
	public record Preferences(Set<String> disabledChallenges, Set<String> blockedItems) {
		public static final Preferences EMPTY = new Preferences(Set.of(), Set.of());
		static final Codec<Preferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				STRING_SET.optionalFieldOf("disabledChallenges", Set.of()).forGetter(Preferences::disabledChallenges),
				STRING_SET.optionalFieldOf("blockedItems", Set.of()).forGetter(Preferences::blockedItems)
		).apply(instance, Preferences::new));
	}

	/**
	 * Os itens que já passaram pelo inventário e os biomas já visitados. Na primeira contagem, o que o jogador
	 * já tem entra sem desafio, então {@code started} diz se essa contagem já aconteceu.
	 */
	public record Discoveries(boolean started, Set<String> items, Set<String> biomes) {
		public static final Discoveries EMPTY = new Discoveries(false, Set.of(), Set.of());
		static final Codec<Discoveries> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("started", false).forGetter(Discoveries::started),
				STRING_SET.optionalFieldOf("items", Set.of()).forGetter(Discoveries::items),
				STRING_SET.optionalFieldOf("biomes", Set.of()).forGetter(Discoveries::biomes)
		).apply(instance, Discoveries::new));
	}

	public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("language").forGetter(PlayerProgress::language),
			Codec.INT.fieldOf("streak").forGetter(PlayerProgress::streak),
			Codec.BOOL.fieldOf("itemsEnabled").forGetter(PlayerProgress::itemsEnabled),
			Codec.BOOL.fieldOf("mobsEnabled").forGetter(PlayerProgress::mobsEnabled),
			Codec.BOOL.optionalFieldOf("languageChosen", false).forGetter(PlayerProgress::languageChosen),
			Codec.INT.optionalFieldOf("bestStreak", 0).forGetter(PlayerProgress::bestStreak),
			Codec.INT.optionalFieldOf("totalCorrect", 0).forGetter(PlayerProgress::totalCorrect),
			Codec.INT.optionalFieldOf("totalWrong", 0).forGetter(PlayerProgress::totalWrong),
			Codec.INT.optionalFieldOf("lossStreak", 0).forGetter(PlayerProgress::lossStreak),
			Codec.INT.optionalFieldOf("lastDailyDay", NO_DAILY_YET).forGetter(PlayerProgress::lastDailyDay),
			Codec.INT.optionalFieldOf("shields", 0).forGetter(PlayerProgress::shields),
			Preferences.CODEC.optionalFieldOf("preferences", Preferences.EMPTY).forGetter(PlayerProgress::preferences),
			Discoveries.CODEC.optionalFieldOf("discoveries", Discoveries.EMPTY).forGetter(PlayerProgress::discoveries)
	).apply(instance, PlayerProgress::new));

	public static PlayerProgress initial(String language) {
		return new PlayerProgress(language, 0, true, true, false, 0, 0, 0, 0, NO_DAILY_YET, 0, Preferences.EMPTY, Discoveries.EMPTY);
	}

	public static boolean isValidLanguage(String choice) {
		if (choice.equals(BOTH)) {
			return true;
		}
		String[] parts = choice.split(SEPARATOR);
		return parts.length > 0 && Arrays.stream(parts).allMatch(part -> StudyLanguage.byCode(part) != null);
	}

	/** Os idiomas individuais que uma escolha representa. A escolha pode ter vários, separados por vírgula. */
	public static List<String> languagesOf(String choice) {
		return choice.equals(BOTH) ? List.of(KOREAN, JAPANESE) : List.of(choice.split(SEPARATOR));
	}

	/** O contrário de {@link #languagesOf}, para salvar uma escolha de vários idiomas. */
	public static String choiceOf(Collection<String> languages) {
		return String.join(SEPARATOR, languages);
	}

	/** Verdadeiro em todo múltiplo da meta. Uma meta zerada desliga a contagem. */
	public static boolean milestone(int count, int goal) {
		return goal > 0 && count > 0 && count % goal == 0;
	}

	/** O próximo múltiplo da meta depois de {@code count}. */
	public static int nextMilestone(int count, int goal) {
		return goal > 0 ? (count / goal + 1) * goal : 0;
	}

	public PlayerProgress afterWin() {
		int newStreak = streak + 1;
		return new PlayerProgress(language, newStreak, itemsEnabled, mobsEnabled, languageChosen, Math.max(bestStreak, newStreak), totalCorrect + 1, totalWrong, 0, lastDailyDay, shields,
				preferences, discoveries);
	}

	public PlayerProgress afterLoss() {
		return new PlayerProgress(language, 0, itemsEnabled, mobsEnabled, languageChosen, bestStreak, totalCorrect, totalWrong + 1, lossStreak + 1, lastDailyDay, shields,
				preferences, discoveries);
	}

	public boolean rewardDue(int goal) {
		return milestone(streak, goal);
	}

	public boolean disasterDue(int goal) {
		return milestone(lossStreak, goal);
	}

	public boolean dailyAvailable(int day) {
		return lastDailyDay != day;
	}

	/** Verdadeiro se o jogador não desligou esse desafio. */
	public boolean allows(String challengeId) {
		return !preferences.disabledChallenges().contains(challengeId);
	}

	public boolean blocks(String itemId) {
		return preferences.blockedItems().contains(itemId);
	}

	public PlayerProgress withLanguage(String newLanguage) {
		return new PlayerProgress(newLanguage, streak, itemsEnabled, mobsEnabled, true, bestStreak, totalCorrect, totalWrong, lossStreak, lastDailyDay, shields, preferences, discoveries);
	}

	public PlayerProgress withEnabled(boolean items, boolean mobs) {
		return new PlayerProgress(language, streak, items, mobs, languageChosen, bestStreak, totalCorrect, totalWrong, lossStreak, lastDailyDay, shields, preferences, discoveries);
	}

	public PlayerProgress withLastDailyDay(int day) {
		return new PlayerProgress(language, streak, itemsEnabled, mobsEnabled, languageChosen, bestStreak, totalCorrect, totalWrong, lossStreak, day, shields, preferences, discoveries);
	}

	public PlayerProgress withShields(int count) {
		return new PlayerProgress(language, streak, itemsEnabled, mobsEnabled, languageChosen, bestStreak, totalCorrect, totalWrong, lossStreak, lastDailyDay, Math.max(0, count),
				preferences, discoveries);
	}

	public PlayerProgress withChallenge(String challengeId, boolean enabled) {
		Set<String> disabled = new LinkedHashSet<>(preferences.disabledChallenges());
		if (enabled) {
			disabled.remove(challengeId);
		} else {
			disabled.add(challengeId);
		}
		return withPreferences(new Preferences(disabled, preferences.blockedItems()));
	}

	public PlayerProgress withBlocked(String itemId, boolean blocked) {
		Set<String> items = new LinkedHashSet<>(preferences.blockedItems());
		if (blocked) {
			items.add(itemId);
		} else {
			items.remove(itemId);
		}
		return withPreferences(new Preferences(preferences.disabledChallenges(), items));
	}

	/** Começa a contagem das descobertas com o que o jogador já tem, sem desafio. */
	public PlayerProgress withDiscoveriesStarted(Collection<String> currentItems) {
		Set<String> items = new LinkedHashSet<>(discoveries.items());
		items.addAll(currentItems);
		return withDiscoveries(new Discoveries(true, items, discoveries.biomes()));
	}

	public PlayerProgress withSeenItem(String itemId) {
		Set<String> items = new LinkedHashSet<>(discoveries.items());
		items.add(itemId);
		return withDiscoveries(new Discoveries(discoveries.started(), items, discoveries.biomes()));
	}

	public PlayerProgress withVisitedBiome(String biomeId) {
		Set<String> biomes = new LinkedHashSet<>(discoveries.biomes());
		biomes.add(biomeId);
		return withDiscoveries(new Discoveries(discoveries.started(), discoveries.items(), biomes));
	}

	private PlayerProgress withPreferences(Preferences newPreferences) {
		return new PlayerProgress(language, streak, itemsEnabled, mobsEnabled, languageChosen, bestStreak, totalCorrect, totalWrong, lossStreak, lastDailyDay, shields,
				newPreferences, discoveries);
	}

	private PlayerProgress withDiscoveries(Discoveries newDiscoveries) {
		return new PlayerProgress(language, streak, itemsEnabled, mobsEnabled, languageChosen, bestStreak, totalCorrect, totalWrong, lossStreak, lastDailyDay, shields,
				preferences, newDiscoveries);
	}
}
