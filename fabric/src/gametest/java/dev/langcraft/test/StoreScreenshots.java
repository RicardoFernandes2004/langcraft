package dev.langcraft.test;

import dev.langcraft.LangCraft;
import dev.langcraft.challenge.MobPunishment;
import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.ChallengeView;
import dev.langcraft.client.study.WorldFlashcards;
import dev.langcraft.client.ui.AlphabetScreen;
import dev.langcraft.client.ui.LanguageScreen;
import dev.langcraft.client.ui.MicTestScreen;
import dev.langcraft.client.ui.MicrophoneScreen;
import dev.langcraft.client.ui.QuizScreen;
import dev.langcraft.client.ui.RouletteScreen;
import dev.langcraft.client.ui.StudyScreen;
import dev.langcraft.client.ui.ThemeScreen;
import dev.langcraft.client.ui.WordListScreen;
import dev.langcraft.client.voice.Microphones;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/** Tira as capturas das páginas do mod: gradlew :fabric:runClientGameTest -Pshots. Sem essa opção, não faz nada. */
public class StoreScreenshots implements FabricClientGameTest {
	private static final int FAR_FUTURE_SECONDS = 100_000;

	private ClientGameTestContext context;
	private TestSingleplayerContext world;

	@FunctionalInterface
	private interface Shot {
		void run() throws Exception;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("langcraft.shots")) {
			return;
		}
		this.context = context;
		LangConfig config = LangCraft.config;
		config.itemIntervalMinSeconds = FAR_FUTURE_SECONDS;
		config.itemIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		config.mobIntervalMinSeconds = FAR_FUTURE_SECONDS;
		config.mobIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		config.quizSeconds = 60;
		config.speakSeconds = 60;
		config.holdSeconds = 60;
		config.rouletteAutoSpinSeconds = 60;
		config.rewardRouletteEnabled = true;
		config.disasterStreak = 0;
		config.dailyRouletteEnabled = false;

		context.getInput().resizeWindow(1920, 1080);
		world = context.worldBuilder().setUseConsistentSettings(false).create();
		try {
			step("primeira entrada", this::firstJoin);
			world.getClientWorld().waitForChunksRender();
			command("time set 1000");
			command("weather clear");
			step("fala", this::speak);
			step("popup", this::quiz);
			step("erro e roleta de itens", this::wrongAndRoulette);
			step("pegue na mao", this::hold);
			step("mob", this::mob);
			step("roletas", this::roulettes);
			step("caderno", this::notebook);
			step("ranking", this::tabList);
			step("temas", this::themes);
		} finally {
			world.close();
		}
	}

	private void step(String name, Shot shot) {
		try {
			shot.run();
			LangCraft.LOGGER.info("[LangCraftShots] OK: {}", name);
		} catch (Exception | AssertionError e) {
			LangCraft.LOGGER.error("[LangCraftShots] FALHOU: {}", name, e);
			context.takeScreenshot("falha-" + name.replace(' ', '-'));
			context.setScreen(() -> null);
		}
	}

	private void firstJoin() {
		context.waitForScreen(ThemeScreen.class);
		clickButtonContaining("langcraft.theme.hanji");
		context.waitTicks(3);
		shot("tema");
		clickButtonContaining("langcraft.theme.confirm");
		context.waitForScreen(LanguageScreen.class);
		clickButtonContaining("langcraft.setup.korean");
		context.waitTicks(2);
		clickButtonContaining("langcraft.setup.japanese");
		context.waitTicks(2);
		shot("idiomas");
		clickButtonContaining("langcraft.setup.confirm");
		context.waitForScreen(MicrophoneScreen.class);
		context.waitTicks(2);
		clickButtonContaining(Microphones.defaultLabelKey());
		context.waitForScreen(MicTestScreen.class);
		context.waitTicks(40);
		shot("teste-do-microfone");
		// Com dois idiomas, há uma palavra de teste para cada um.
		while (context.computeOnClient(mc -> mc.screen instanceof MicTestScreen)) {
			clickButton(label -> label.startsWith("Skip"));
			context.waitTicks(10);
		}
		context.waitFor(mc -> mc.screen == null, 200);
		context.waitTicks(10);
	}

	/** Tira os avisos de receita e conquista, que só atrapalham a imagem. */
	private void shot(String name) {
		context.runOnClient(mc -> mc.getToastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	private void clearChat() {
		context.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
	}

	private void resetInventory(String item, int count) {
		clearChat();
		command("clear @p");
		command("give @p minecraft:" + item + " " + count);
		context.waitTicks(3);
	}

	private void speak() {
		resetInventory("cobblestone", 32);
		language(PlayerProgress.KOREAN);
		start("item speak");
		context.waitFor(mc -> ChallengeClient.current() != null, 40);
		context.waitTicks(60);
		shot("fala");
		cancel();
	}

	private void quiz() {
		resetInventory("diamond_sword", 1);
		language(PlayerProgress.JAPANESE);
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(3);
		shot("popup-japones");
		ChallengeView view = view();
		clickButton(label -> label.equals(view.word));
		context.waitTicks(4);
		shot("popup-acerto");
		context.waitFor(mc -> mc.screen == null, 100);
		context.waitTicks(40);
	}

	private void wrongAndRoulette() {
		resetInventory("cobblestone", 64);
		language(PlayerProgress.KOREAN);
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		ChallengeView view = view();
		context.waitTicks(3);
		clickButton(label -> !label.equals(view.word));
		context.waitTicks(4);
		shot("popup-erro");
		context.waitForScreen(RouletteScreen.class);
		context.waitTicks(4);
		shot("roleta-itens");
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitTicks(30);
		shot("roleta-girando");
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 40);
		context.waitTicks(60);
		shot("roleta-resultado");
		context.waitFor(mc -> mc.screen == null, 200);
		context.waitTicks(10);
	}

	private void hold() {
		command("clear @p");
		command("give @p minecraft:apple 16");
		command("give @p minecraft:bread 16");
		context.waitTicks(3);
		language(PlayerProgress.KOREAN);
		start("item hold");
		context.waitFor(mc -> ChallengeClient.current() != null, 40);
		context.waitTicks(10);
		shot("pegue-na-mao");
		cancel();
	}

	private void mob() {
		List<MobPunishment.Tier> tiers = LangCraft.config.mobTiers;
		LangCraft.config.mobTiers = List.of(new MobPunishment.Tier("hostile", 1, List.of("minecraft:warden")));
		language(PlayerProgress.JAPANESE);
		start("mob quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(3);
		shot("mob");
		ChallengeView view = view();
		clickButton(label -> label.equals(view.word));
		context.waitFor(mc -> mc.screen == null, 100);
		context.waitTicks(40);
		LangCraft.config.mobTiers = tiers;
	}

	private void roulettes() {
		resetInventory("cobblestone", 64);
		onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).afterLoss().afterLoss().afterLoss()));
		context.waitTicks(2);
		openRoulette("disaster");
		shot("roleta-desastre");
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitTicks(28);
		shot("roleta-desastre-girando");
		cancel();
		openRoulette("daily");
		shot("roleta-diaria");
		cancel();
		onServer(server -> {
			PlayerProgress progress = LangCraft.progress(player(server));
			for (int i = 0; i < 15; i++) {
				progress = progress.afterWin();
			}
			LangCraft.setProgress(player(server), progress);
		});
		context.waitTicks(2);
		openRoulette("jackpot");
		shot("roleta-jackpot");
		cancel();
	}

	private void openRoulette(String kind) {
		clearChat();
		command("execute as @p run lang admin roulette " + kind);
		context.waitForScreen(RouletteScreen.class);
		context.waitTicks(4);
	}

	private void notebook() {
		for (String item : List.of("cobblestone", "apple", "bread", "iron_ingot", "oak_log", "diamond", "torch")) {
			resetStreak();
			resetInventory(item, 8);
			language(item.length() % 2 == 0 ? PlayerProgress.KOREAN : PlayerProgress.JAPANESE);
			start("item quiz");
			context.waitForScreen(QuizScreen.class);
			ChallengeView view = view();
			context.waitTicks(2);
			clickButton(label -> label.equals(view.word));
			context.waitFor(mc -> mc.screen == null, 100);
			context.waitTicks(40);
		}
		language(PlayerProgress.KOREAN + "," + PlayerProgress.JAPANESE);
		context.setScreen(() -> new StudyScreen(null));
		context.waitTicks(4);
		shot("caderno-frente");
		clickButtonContaining("langcraft.study.show");
		context.waitTicks(4);
		shot("caderno-verso");
		context.setScreen(() -> null);
		openByCommand("words", WordListScreen.class);
		shot("lista-de-palavras");
		context.setScreen(() -> null);
		openByCommand("alphabet", AlphabetScreen.class);
		shot("alfabeto");
		clickButtonContaining("langcraft.alphabet.practice");
		context.waitTicks(4);
		shot("alfabeto-treino");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	private void openByCommand(String name, Class<? extends net.minecraft.client.gui.screens.Screen> screen) {
		command("execute as @p run lang " + name);
		context.waitForScreen(screen);
		context.waitTicks(4);
	}

	private void tabList() {
		onServer(server -> {
			ServerPlayer player = player(server);
			PlayerProgress progress = LangCraft.progress(player);
			for (int i = 0; i < 30; i++) {
				progress = progress.afterWin();
			}
			LangCraft.setProgress(player, progress);
		});
		// O ranking só atualiza depois de um resultado.
		resetInventory("cobblestone", 8);
		language(PlayerProgress.KOREAN);
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		ChallengeView view = view();
		context.waitTicks(2);
		clickButton(label -> label.equals(view.word));
		context.waitFor(mc -> mc.screen == null, 100);
		context.waitTicks(40);
		clearChat();
		context.getInput().holdKey(options -> options.keyPlayerList);
		context.waitTicks(4);
		shot("ranking");
		context.getInput().releaseKey(options -> options.keyPlayerList);
		context.waitTicks(2);
		shot("jogo-placar");
	}

	private void themes() {
		String original = context.computeOnClient(mc -> WorldFlashcards.themeId());
		resetStreak();
		resetInventory("cobblestone", 32);
		language(PlayerProgress.KOREAN);
		for (String theme : List.of("cidade", "dancheong", "cerejeira", "festival")) {
			context.runOnClient(mc -> WorldFlashcards.saveThemeId(theme));
			context.waitTicks(2);
			start("item quiz");
			context.waitForScreen(QuizScreen.class);
			context.waitTicks(3);
			shot("tema-" + theme);
			ChallengeView view = view();
			clickButton(label -> label.equals(view.word));
			context.waitFor(mc -> mc.screen == null, 100);
			context.waitTicks(40);
		}
		context.runOnClient(mc -> WorldFlashcards.saveThemeId(original));
	}

	/** A cada 5 acertos seguidos abre a roleta de recompensa, que atrapalharia as capturas seguintes. */
	private void resetStreak() {
		onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).afterLoss()));
	}

	/** Encerra o desafio ou a roleta sem aplicar nada: virar espectador cancela tudo. */
	private void cancel() {
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null && !(mc.screen instanceof RouletteScreen), 60);
		command("gamemode survival @p");
		context.waitTicks(4);
	}

	private void start(String arguments) {
		command("execute as @p run lang admin challenge " + arguments);
		context.waitTicks(2);
	}

	private void language(String language) {
		onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).withLanguage(language)));
	}

	private void clickButtonContaining(String translationKey) {
		String text = context.computeOnClient(mc -> I18n.get(translationKey));
		clickButton(label -> label.contains(text));
	}

	private void clickButton(Predicate<String> label) {
		double[] position = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			AbstractButton button = findButton(mc.screen.children(), label);
			if (button == null) {
				throw new AssertionError("Botao nao encontrado");
			}
			return new double[] {(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static AbstractButton findButton(List<? extends GuiEventListener> children, Predicate<String> label) {
		for (GuiEventListener child : children) {
			if (child instanceof AbstractButton button && button.active && button.visible && label.test(button.getMessage().getString())) {
				return button;
			}
			if (child instanceof ContainerEventHandler container) {
				AbstractButton nested = findButton(container.children(), label);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private ChallengeView view() {
		return context.computeOnClient(mc -> ChallengeClient.current());
	}

	private void command(String command) {
		world.getServer().runCommand(command);
	}

	private void onServer(java.util.function.Consumer<MinecraftServer> action) {
		world.getServer().runOnServer(action::accept);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
