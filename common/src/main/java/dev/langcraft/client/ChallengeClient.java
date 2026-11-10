package dev.langcraft.client;

import dev.langcraft.LangCraft;
import dev.langcraft.challenge.Minigame;
import dev.langcraft.challenge.PlayerTitle;
import dev.langcraft.challenge.SubjectType;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.match.AnswerMatcher;
import dev.langcraft.client.match.KanaNormalizer;
import dev.langcraft.client.ui.QuizScreen;
import dev.langcraft.client.study.AnswerMethod;
import dev.langcraft.client.study.FlashcardDeck;
import dev.langcraft.client.study.WorldFlashcards;
import dev.langcraft.client.ui.NotebookScreen;
import dev.langcraft.client.ui.RouletteScreen;
import dev.langcraft.client.ui.TypeAnswerScreen;
import dev.langcraft.client.voice.VoiceRecognizer;
import dev.langcraft.client.voice.VoskGrammar;
import dev.langcraft.client.voice.VoskVocabulary;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeResultPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/** Recebe os desafios do servidor, cuida do estado local e avalia as respostas. */
public final class ChallengeClient {
	private static final int WRONG_FLASH_TICKS = 16;
	private static final int CORRECT_DISPLAY_TICKS = 30;
	private static final float BELL_PITCH = 1.2f;
	private static final float BELL_VOLUME = 0.7f;

	private static @Nullable ChallengeView current;
	private static @Nullable ScorePayload score;

	private ChallengeClient() {
	}

	public static @Nullable ChallengeView current() {
		return current;
	}

	public static void onStart(ChallengeStartPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<String> word = GameNames.name(payload.language(), payload.subjectType(), payload.subject());
		if (word.isEmpty() || GameNames.nativeNameBroken(payload.subjectType(), payload.subject(), payload.language())) {
			respond(payload.id(), ChallengeResultPayload.Result.UNAVAILABLE);
			return;
		}
		// Quem está com o caderno aberto não vê o alerta direito, então nenhum desafio começa por baixo da tela.
		if (minecraft.screen instanceof NotebookScreen || payload.minigame() == Minigame.QUIZ && minecraft.screen != null) {
			respond(payload.id(), ChallengeResultPayload.Result.BUSY);
			return;
		}
		if (payload.minigame() == Minigame.SPEAK && payload.drawn() && !VoskVocabulary.canSpeak(word.get(), payload.language())) {
			LangCraft.LOGGER.info("[voz] '{}' não está no vocabulário de {}. O desafio vira popup.", word.get(), payload.language());
			respond(payload.id(), ChallengeResultPayload.Result.NOT_SPEAKABLE);
			return;
		}
		endCurrent();
		ChallengeView view = new ChallengeView(payload.id(), payload.minigame(), payload.subjectType(), payload.subject(), payload.language(), word.get(),
				GameNames.nativeName(payload.subjectType(), payload.subject(), payload.language()), GameNames.icon(payload.subjectType(), payload.subject()),
				segments(word.get(), payload.language()), payload.durationTicks(), payload.streak(), payload.streakGoal());
		current = view;
		play(SoundEvents.NOTE_BLOCK_BELL.value(), BELL_PITCH, BELL_VOLUME);
		if (view.minigame == Minigame.QUIZ) {
			minecraft.setScreen(new QuizScreen(view));
		} else if (view.minigame == Minigame.SPEAK) {
			startVoice(view);
		}
	}

	private static void startVoice(ChallengeView view) {
		view.voice = listenFor(view.word, view.language, GameNames.allNames(view.language, view.type),
				(shown, correct) -> onHeard(view, shown, correct));
	}

	/** Começa a ouvir uma palavra. {@code onResult} recebe, na thread do jogo, o que foi entendido e se está certo. */
	public static VoiceRecognizer listenFor(String word, String language, List<String> names, BiConsumer<String, Boolean> onResult) {
		Predicate<String> accepts = text -> AnswerMatcher.matchesSpoken(word, text, language, LangCraft.config.voiceSimilarity);
		VoiceRecognizer voice = new VoiceRecognizer(language, () -> VoskGrammar.build(word, names, language, new Random()),
				() -> VoskGrammar.whisperPrompt(word, names, new Random()), accepts, text -> {
			boolean correct = accepts.test(text);
			LangCraft.LOGGER.info("[voz] esperado '{}', ouvido '{}', {}", word, text, correct ? "aceito" : "recusado");
			String shown = inlineWord(text, language);
			Minecraft.getInstance().execute(() -> onResult.accept(shown, correct));
		});
		voice.start();
		return voice;
	}

	public static List<Furigana.Segment> segments(String text, String language) {
		return LangCraft.config.showFurigana ? Furigana.segments(text, language) : List.of(new Furigana.Segment(text, null));
	}

	private static String inlineWord(String text, String language) {
		return LangCraft.config.showFurigana ? Furigana.inline(text, language) : text;
	}

	/** Carrega o dicionário japonês em segundo plano, para o primeiro desafio não travar o jogo. */
	public static void preloadDictionary() {
		CompletableFuture.runAsync(KanaNormalizer::preload);
	}

	private static void onHeard(ChallengeView view, String text, boolean correct) {
		if (view == current && !view.finished) {
			view.heard = text;
			view.lastAttemptTyped = false;
			if (correct) {
				submitCorrect(view);
			} else {
				view.lastWrongAnswer = text;
				flashWrong(view);
			}
		}
	}

	/** Chamado pela caixa de digitação. Retorna verdadeiro se a resposta estiver certa. */
	public static boolean submitTyped(ChallengeView view, String text) {
		if (view != current || view.finished) {
			return false;
		}
		view.lastAttemptTyped = true;
		if (AnswerMatcher.matchesTyped(view.word, text, view.language)) {
			submitCorrect(view);
			return true;
		}
		view.lastWrongAnswer = text;
		flashWrong(view);
		return false;
	}

	/** Chamado pelo popup quando uma alternativa é escolhida. */
	public static void submitOption(ChallengeView view, boolean correct, String option) {
		if (view == current && !view.finished) {
			if (correct) {
				submitCorrect(view);
			} else {
				view.lastWrongAnswer = option;
				flashWrong(view);
			}
		}
	}

	/** Chamado pelo popup depois de mostrar qual era a alternativa certa. */
	public static void submitWrong(ChallengeView view) {
		if (view == current && !view.finished) {
			respond(view.id, ChallengeResultPayload.Result.WRONG);
		}
	}

	private static void submitCorrect(ChallengeView view) {
		respond(view.id, ChallengeResultPayload.Result.CORRECT);
	}

	private static void flashWrong(ChallengeView view) {
		view.wrongFlashTicks = WRONG_FLASH_TICKS;
		play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f);
	}

	public static void onEnded(ChallengeEndedPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof RouletteScreen roulette && roulette.id() == payload.id()) {
			minecraft.setScreen(null);
			return;
		}
		ChallengeView view = current;
		if (view == null || view.id != payload.id()) {
			return;
		}
		stopVoice(view);
		closeChallengeScreens(minecraft);
		switch (payload.outcome()) {
			case WON -> {
				view.finished = true;
				view.streak = payload.streak();
				view.correctTicks = CORRECT_DISPLAY_TICKS;
				play(SoundEvents.PLAYER_LEVELUP, 1.4f);
				recordFlashcard(view, true);
			}
			case LOST, SHIELDED -> {
				current = null;
				recordFlashcard(view, false);
				showAnswer(minecraft, view);
				play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6f);
				if (payload.outcome() == ChallengeEndedPayload.Outcome.LOST && view.type == SubjectType.MOB) {
					minecraft.gui.setTitle(Component.translatable("langcraft.message.mob_title"));
					minecraft.gui.setSubtitle(Component.translatable("langcraft.message.mob_subtitle", inlineWord(view.word, view.language) + " · " + view.nativeName));
				}
			}
			case CANCELLED -> current = null;
		}
	}

	private static void recordFlashcard(ChallengeView view, boolean won) {
		FlashcardDeck deck = WorldFlashcards.current();
		if (deck != null) {
			deck.record(view.language, view.type.name(), view.subject, view.word, view.nativeName, answerMethod(view), won, view.lastWrongAnswer);
		}
	}

	/** Sem nenhuma tentativa, o tempo acabou na habilidade que o minigame pedia: falar. */
	private static AnswerMethod answerMethod(ChallengeView view) {
		return switch (view.minigame) {
			case SPEAK -> view.lastAttemptTyped ? AnswerMethod.TYPED : AnswerMethod.SPEAK;
			case QUIZ -> AnswerMethod.QUIZ;
			case HOLD -> AnswerMethod.HOLD;
		};
	}

	public static @Nullable ScorePayload score() {
		return score;
	}

	/** O primeiro placar de cada entrada confirma que o servidor tem o mod, então o aviso sobre o /lang é verdadeiro. */
	public static void onScore(ScorePayload payload) {
		if (score == null) {
			showWelcome();
		} else if (LangCraft.config.showTitles && PlayerTitle.of(payload.totalCorrect()).ordinal() > PlayerTitle.of(score.totalCorrect()).ordinal()) {
			play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
		}
		score = payload;
	}

	private static void showWelcome() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Component key = LangCraftClient.studyKey.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW);
		Component command = Component.literal("/lang").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand("/lang"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("langcraft.message.welcome.click"))));
		minecraft.player.displayClientMessage(Component.literal("✦ LangCraft ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		minecraft.player.displayClientMessage(welcomeLine("langcraft.message.welcome.notebook", key), false);
		minecraft.player.displayClientMessage(welcomeLine("langcraft.message.welcome.commands", command), false);
	}

	private static Component welcomeLine(String key, Component highlighted) {
		return Component.literal(" » ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable(key, highlighted).withStyle(ChatFormatting.GRAY));
	}

	private static void showAnswer(Minecraft minecraft, ChallengeView view) {
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.translatable("langcraft.message.answer", inlineWord(view.word, view.language), view.nativeName), false);
		}
	}

	public static void onRouletteStart(RouletteStartPayload payload) {
		Minecraft.getInstance().setScreen(new RouletteScreen(payload));
	}

	public static void onRouletteResult(RouletteResultPayload payload) {
		if (Minecraft.getInstance().screen instanceof RouletteScreen roulette && roulette.id() == payload.id()) {
			roulette.startSpin(payload.sliceIndex(), payload.spinTicks());
		}
	}

	public static void tick(Minecraft minecraft, KeyMapping typeKey) {
		boolean typePressed = typeKey.consumeClick();
		ChallengeView view = current;
		if (view == null || minecraft.player == null) {
			return;
		}
		if (view.finished) {
			if (--view.correctTicks <= 0) {
				current = null;
			}
			return;
		}
		if (!minecraft.isPaused()) {
			view.remainingTicks--;
			if (view.wrongFlashTicks > 0) {
				view.wrongFlashTicks--;
			}
		}
		if (view.minigame == Minigame.SPEAK && typePressed && minecraft.screen == null) {
			minecraft.setScreen(new TypeAnswerScreen(view));
		}
	}

	public static void clear() {
		endCurrent();
		score = null;
		WorldFlashcards.clear();
		GameNames.clear();
		TooltipNames.clear();
		ClientPreferences.clear();
	}

	private static void endCurrent() {
		if (current != null) {
			stopVoice(current);
			current = null;
		}
	}

	private static void stopVoice(ChallengeView view) {
		if (view.voice != null) {
			view.voice.stop();
		}
	}

	private static void closeChallengeScreens(Minecraft minecraft) {
		Screen screen = minecraft.screen;
		if (screen instanceof QuizScreen || screen instanceof TypeAnswerScreen) {
			minecraft.setScreen(null);
		}
	}

	public static void respond(int id, ChallengeResultPayload.Result result) {
		LangCraft.platform.sendToServer(new ChallengeResultPayload(id, result));
	}

	public static void play(SoundEvent sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}
}
