package dev.langcraft.challenge;

/** As contas do que cada acerto e cada erro custam. Só números, para poder testar sem abrir o jogo. */
public final class Consequences {
	private static final int MAX_LEVELS = 20;
	private static final int MAX_DEBUFF_SECONDS = 180;
	private static final int MAX_AMPLIFIER = 2;
	private static final int MAX_MOBS = 3;
	private static final int STREAK_BONUS = 2;
	private static final int MAX_BONUS_STREAK = 10;
	private static final int SPEAK_MULTIPLIER = 2;

	/** O castigo que acompanha um erro comum, e que cresce a cada erro seguido. */
	public record LossPenalty(int levels, int mobs) {
	}

	/** Lentidão e fraqueza que acompanham a roleta de desastre. */
	public record Debuff(int seconds, int amplifier) {
	}

	private Consequences() {
	}

	public static LossPenalty lossPenalty(int lossStreak, int levelsPerError) {
		int streak = Math.max(1, lossStreak);
		return new LossPenalty(Math.min(MAX_LEVELS, Math.max(0, levelsPerError) * streak), Math.min(MAX_MOBS, streak));
	}

	/** Piora a cada desastre da mesma sequência de erros. */
	public static Debuff disasterDebuff(int lossStreak, int disasterStreak, int secondsPerDisaster) {
		int disasters = disasterStreak > 0 ? Math.max(1, lossStreak / disasterStreak) : 1;
		return new Debuff(Math.min(MAX_DEBUFF_SECONDS, Math.max(0, secondsPerDisaster) * disasters), Math.min(MAX_AMPLIFIER, disasters - 1));
	}

	/** A sequência aumenta a experiência de cada acerto. Falar é o mais difícil, então vale o dobro. */
	public static int winExperience(int base, int streak, Minigame minigame) {
		if (base <= 0) {
			return 0;
		}
		int experience = base + STREAK_BONUS * Math.min(MAX_BONUS_STREAK, Math.max(0, streak));
		return minigame == Minigame.SPEAK ? experience * SPEAK_MULTIPLIER : experience;
	}
}
