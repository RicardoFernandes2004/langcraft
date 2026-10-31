package dev.linguacraft.client;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.client.lang.Furigana;
import dev.linguacraft.client.lang.GameNames;
import dev.linguacraft.client.match.AnswerMatcher;
import dev.linguacraft.client.match.KanaNormalizer;
import dev.linguacraft.client.ui.QuizScreen;
import dev.linguacraft.client.ui.RouletteScreen;
import dev.linguacraft.client.ui.TypeAnswerScreen;
import dev.linguacraft.client.voice.VoiceRecognizer;
import dev.linguacraft.client.voice.VoskGrammar;
import dev.linguacraft.net.ChallengeEndedPayload;
import dev.linguacraft.net.ChallengeResultPayload;
import dev.linguacraft.net.ChallengeStartPayload;
import dev.linguacraft.net.RouletteResultPayload;
import dev.linguacraft.net.RouletteStartPayload;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Recebe os desafios do servidor, cuida do estado local e avalia as respostas. */
public final class ChallengeClient {
	private static final int WRONG_FLASH_TICKS = 16;
	private static final int CORRECT_DISPLAY_TICKS = 30;

	private static @Nullable ChallengeView current;

	private ChallengeClient() {
	}

	public static @Nullable ChallengeView current() {
		return current;
	}

	public static void onStart(ChallengeStartPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<String> word = GameNames.name(payload.language(), payload.subjectType(), payload.subject());
		if (word.isEmpty()) {
			respond(payload.id(), ChallengeResultPayload.Result.UNAVAILABLE);
			return;
		}
		if (payload.minigame() == Minigame.QUIZ && minecraft.screen != null) {
			respond(payload.id(), ChallengeResultPayload.Result.BUSY);
			return;
		}
		endCurrent();
		ChallengeView view = new ChallengeView(payload.id(), payload.minigame(), payload.subjectType(), payload.language(), word.get(),
				GameNames.nativeName(payload.subjectType(), payload.subject()), GameNames.icon(payload.subjectType(), payload.subject()),
				segments(word.get(), payload.language()), payload.durationTicks(), payload.streak(), payload.streakGoal());
		current = view;
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
		Predicate<String> accepts = text -> AnswerMatcher.matchesSpoken(word, text, language, LinguaCraft.config.voiceSimilarity);
		VoiceRecognizer voice = new VoiceRecognizer(language, () -> VoskGrammar.build(word, names, language, new Random()),
				() -> VoskGrammar.whisperPrompt(word, names, new Random()), accepts, text -> {
			boolean correct = accepts.test(text);
			LinguaCraft.LOGGER.info("[voz] esperado '{}', ouvido '{}', {}", word, text, correct ? "aceito" : "recusado");
			String shown = inlineWord(text, language);
			Minecraft.getInstance().execute(() -> onResult.accept(shown, correct));
		});
		voice.start();
		return voice;
	}

	public static List<Furigana.Segment> segments(String text, String language) {
		return LinguaCraft.config.showFurigana ? Furigana.segments(text, language) : List.of(new Furigana.Segment(text, null));
	}

	private static String inlineWord(String text, String language) {
		return LinguaCraft.config.showFurigana ? Furigana.inline(text, language) : text;
	}

	/** Carrega o dicionário japonês em segundo plano, para o primeiro desafio não travar o jogo. */
	public static void preloadDictionary() {
		CompletableFuture.runAsync(KanaNormalizer::preload);
	}

	private static void onHeard(ChallengeView view, String text, boolean correct) {
		if (view == current && !view.finished) {
			view.heard = text;
			if (correct) {
				submitCorrect(view);
			} else {
				flashWrong(view);
			}
		}
	}

	/** Chamado pela caixa de digitação. Retorna verdadeiro se a resposta estiver certa. */
	public static boolean submitTyped(ChallengeView view, String text) {
		if (view != current || view.finished) {
			return false;
		}
		if (AnswerMatcher.matchesTyped(view.word, text, view.language)) {
			submitCorrect(view);
			return true;
		}
		flashWrong(view);
		return false;
	}

	/** Chamado pelo popup quando uma alternativa é escolhida. */
	public static void submitOption(ChallengeView view, boolean correct) {
		if (view == current && !view.finished) {
			if (correct) {
				submitCorrect(view);
			} else {
				flashWrong(view);
			}
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
			}
			case LOST -> {
				current = null;
				showAnswer(minecraft, view);
				play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6f);
				if (view.type == SubjectType.MOB) {
					minecraft.gui.setTitle(Component.translatable("linguacraft.message.mob_title"));
					minecraft.gui.setSubtitle(Component.translatable("linguacraft.message.mob_subtitle", inlineWord(view.word, view.language) + " · " + view.nativeName));
				}
			}
			case CANCELLED -> current = null;
		}
	}

	private static void showAnswer(Minecraft minecraft, ChallengeView view) {
		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.translatable("linguacraft.message.answer", inlineWord(view.word, view.language), view.nativeName), false);
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
		if (view.minigame == Minigame.HOLD) {
			tickHold(minecraft, view);
		}
	}

	/** O servidor decide o acerto. Aqui só mostramos o erro de quem segura outro item. */
	private static void tickHold(Minecraft minecraft, ChallengeView view) {
		ItemStack held = minecraft.player.getMainHandItem();
		Item item = held.isEmpty() ? null : held.getItem();
		if (item != view.lastHeldItem) {
			view.lastHeldItem = item;
			view.wrongHold.reset();
			view.wrongItem = false;
		}
		if (view.wrongHold.tick(item != null && !view.icon.is(item))) {
			view.wrongItem = true;
			flashWrong(view);
		}
	}

	public static void clear() {
		endCurrent();
		GameNames.clear();
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
		ClientPlayNetworking.send(new ChallengeResultPayload(id, result));
	}

	public static void play(SoundEvent sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}
}
