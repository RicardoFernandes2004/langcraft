package dev.linguacraft.client;

import dev.linguacraft.challenge.HoldTracker;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.client.lang.Furigana;
import dev.linguacraft.client.voice.VoiceRecognizer;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** O desafio em andamento, do jeito que a interface precisa dele. */
public final class ChallengeView {
	public final int id;
	public final Minigame minigame;
	public final SubjectType type;
	public final String language;
	public final String word;
	public final String nativeName;
	public final ItemStack icon;
	public final List<Furigana.Segment> segments;
	public final int totalTicks;
	public final int streakGoal;

	public int streak;
	public int remainingTicks;
	public int wrongFlashTicks;
	public int correctTicks;
	public boolean finished;
	public @Nullable String heard;
	public boolean wrongItem;
	public @Nullable VoiceRecognizer voice;

	final HoldTracker wrongHold = new HoldTracker();
	@Nullable Item lastHeldItem;

	ChallengeView(int id, Minigame minigame, SubjectType type, String language, String word, String nativeName, ItemStack icon,
			List<Furigana.Segment> segments, int totalTicks, int streak, int streakGoal) {
		this.segments = segments;
		this.id = id;
		this.minigame = minigame;
		this.type = type;
		this.language = language;
		this.word = word;
		this.nativeName = nativeName;
		this.icon = icon;
		this.totalTicks = totalTicks;
		this.remainingTicks = totalTicks;
		this.streak = streak;
		this.streakGoal = streakGoal;
	}

	public float remainingFraction() {
		return totalTicks <= 0 ? 0 : Math.max(0, (float) remainingTicks / totalTicks);
	}
}
