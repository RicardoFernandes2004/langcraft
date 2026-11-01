package dev.langcraft.test;

import dev.langcraft.LangCraft;
import dev.langcraft.challenge.Minigame;
import dev.langcraft.challenge.MobPunishment;
import dev.langcraft.challenge.SubjectType;
import dev.langcraft.client.ChallengeClient;
import dev.langcraft.client.ChallengeView;
import dev.langcraft.client.LangCraftClient;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.ui.LanguageScreen;
import dev.langcraft.client.ui.LangConfigScreen;
import dev.langcraft.client.ui.MicTestScreen;
import dev.langcraft.client.ui.MicrophoneScreen;
import dev.langcraft.client.ui.QuizScreen;
import dev.langcraft.client.study.WorldFlashcards;
import dev.langcraft.client.ui.AlphabetScreen;
import dev.langcraft.client.ui.RouletteScreen;
import dev.langcraft.client.ui.StudyScreen;
import dev.langcraft.client.ui.ThemeScreen;
import dev.langcraft.client.ui.TypeAnswerScreen;
import dev.langcraft.client.ui.UiTheme;
import dev.langcraft.client.ui.WordListScreen;
import dev.langcraft.client.ui.VoiceSetupScreen;
import dev.langcraft.client.match.AnswerMatcher;
import dev.langcraft.client.voice.Microphones;
import dev.langcraft.client.voice.ModelManager;
import dev.langcraft.client.voice.VoiceRecognizer;
import dev.langcraft.client.voice.VoskGrammar;
import dev.langcraft.client.voice.SpeechPipeline;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.vosk.Model;

/** Percorre os cenários do plano no jogo de verdade. Falhas são reunidas e relatadas no fim. */
public class LangCraftClientTest implements FabricClientGameTest {
	private static final int SPIN_WAIT_TICKS = 90;
	private static final int FAR_FUTURE_SECONDS = 100_000;

	private final List<String> failures = new ArrayList<>();
	private ClientGameTestContext context;
	private TestSingleplayerContext world;

	@FunctionalInterface
	private interface Scenario {
		void run() throws Exception;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		LangConfig config = LangCraft.config;
		config.itemIntervalMinSeconds = FAR_FUTURE_SECONDS;
		config.itemIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		config.mobIntervalMinSeconds = FAR_FUTURE_SECONDS;
		config.mobIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		config.rewardRouletteEnabled = false;
		config.quizSeconds = 3;
		config.speakSeconds = 3;
		config.holdSeconds = 4;
		config.rouletteAutoSpinSeconds = 2;

		setupStep("engrenagem do Mod Menu abre a configuracao, que salva", this::modMenuConfig);
		deleteVoiceModels();
		// Não lê do arquivo: uma rodada interrompida pode ter salvo o endereço inválido abaixo.
		String modelUrl = new LangConfig().voiceModelBaseUrl;
		config.microphone = "";
		// Endereço inválido, para o cenário de falha do download.
		config.voiceModelBaseUrl = "http://127.0.0.1:9/";
		String whisperUrl = new LangConfig().whisperModelUrl;
		config.whisperModelUrl = "http://127.0.0.1:9/ggml-tiny.bin";

		world = context.worldBuilder().create();
		try {
			setupStep("primeira entrada: idioma, falha do download, continuar sem voz", this::firstJoinWithFailedDownload);
			config.voiceModelBaseUrl = modelUrl;
			config.whisperModelUrl = whisperUrl;
			setupStep("trocar de idioma abre o download e fecha sozinho", this::downloadAfterLanguageCommand);
			setupStep("idioma ja baixado nao abre a tela de download", this::noDownloadScreenWhenReady);
			world.getClientWorld().waitForChunksRender();
			command("difficulty peaceful");
			command("time set day");
			command("lang off");

			scenario("comandos no chat abrem as telas", this::chatCommands);
			scenario("capturas de todas as telas em todos os temas", this::themeScreenshots);
			scenario("nomes em coreano e japones carregam", this::namesLoad);
			scenario("furigana sobre os kanjis", this::furiganaShown);
			scenario("popup: acertar nao exclui", this::quizWrongThenRight);
			scenario("popup: errar uma vez abre a roleta; placar e flashcards", this::quizWrongPunishes);
			scenario("popup: tempo acaba, roleta gira com espaco e exclui a fatia", this::quizTimeoutSpace);
			scenario("roleta: gira com o botao", this::rouletteButton);
			scenario("roleta: gira sozinha", this::rouletteAutoSpin);
			scenario("pilha de 1 item e excluida sem roleta", this::singleItemNoRoulette);
			scenario("fala por digitacao: errar, acertar", this::speakTyped);
			scenario("fala: tempo acaba abre a roleta", this::speakTimeout);
			scenario("item movido durante o desafio", this::movedStack);
			scenario("item jogado fora nao tem roleta", this::droppedItem);
			scenario("morrer cancela o desafio", this::deathCancels);
			scenario("espectador cancela o desafio", this::spectatorCancels);
			scenario("popup com inventario aberto e adiado", this::busyWhenInventoryOpen);
			scenario("inventario vazio nao gera desafio", this::emptyInventory);
			scenario("pegue na mao: erro e acerto", this::holdWrongThenRight);
			scenario("pegue na mao: tempo acaba", this::holdTimeout);
			scenario("pegue na mao pelo comando nunca vira popup", this::holdCommandNeverBecomesQuiz);
			scenario("mob: acertar nao gera mob", this::mobWinNoSpawn);
			scenario("mob: warden nasce acima, sem perder itens", this::mobLossOutdoors);
			scenario("mob: warden em caverna de teto baixo", this::mobLossLowCeiling);
			scenario("mob: pacifico nao sorteia hostis", this::mobPeacefulFilter);
			scenario("mob: comando segurar e recusado", this::mobHoldRefused);
			scenario("roleta invertida apos a sequencia", this::rewardRoulette);
			scenario("roleta invertida com inventario cheio", this::rewardFullInventory);
			scenario("roleta invertida so com nao empilhaveis da niveis", this::rewardOnlyUnstackable);
			scenario("sequencia zera ao perder", this::streakResetsOnLoss);
			scenario("intervalos simultaneos esperam um ao outro", this::simultaneousTimers);
			scenario("voz: modelo baixa e carrega, microfone abre", this::voicePipeline);
			scenario("voz: amostras gravadas", this::voiceSamples);
			scenario("sair durante a roleta ainda exclui; sequencia persiste", this::disconnectDuringRoulette);
			setupStep("mundo novo pergunta o idioma, sem baixar de novo", this::newWorldAsksLanguage);
			setupStep("palavra de teste, troca de microfone e microfone que sumiu", this::microphoneTestFlow);
		} finally {
			world.close();
		}
		for (String failure : failures) {
			LangCraft.LOGGER.error("[LangCraftTest] FALHOU: {}", failure);
		}
		LangCraft.LOGGER.info("[LangCraftTest] Falhas: {}", failures.size());
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " cenario(s) falharam: " + failures);
		}
	}

	/** Como {@link #scenario}, mas sem preparar o mundo, porque ele ainda está sendo configurado. */
	private void setupStep(String name, Scenario scenario) {
		try {
			scenario.run();
			LangCraft.LOGGER.info("[LangCraftTest] PASSOU: {}", name);
		} catch (Exception | AssertionError e) {
			failures.add(name + " -> " + e);
			LangCraft.LOGGER.error("[LangCraftTest] FALHOU: {}", name, e);
			context.takeScreenshot("FALHA-" + failures.size());
		}
	}

	private void deleteVoiceModels() {
		java.nio.file.Path models = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("langcraft/models");
		if (java.nio.file.Files.exists(models)) {
			try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(models)) {
				for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
					java.nio.file.Files.delete(path);
				}
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
	}

	private boolean serverReady() {
		return world.getServer().computeOnServer(server -> LangCraft.challenges.isReady(player(server)));
	}

	private void firstJoinWithFailedDownload() throws Exception {
		context.waitForScreen(ThemeScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("tema-escolha");
		check(!serverReady(), "escolhendo o tema, os desafios nao comecam");
		check(UiTheme.current() == UiTheme.DEFAULT, "o tema recomendado vem marcado");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> mc.screen instanceof ThemeScreen), "Esc nao fecha a primeira escolha de tema");
		clickButtonContaining("langcraft.theme.cidade");
		context.waitTicks(2);
		check(UiTheme.current() == UiTheme.ANCIENT_CITY, "o tema marcado vale na hora");
		check(context.computeOnClient(mc -> WorldFlashcards.themeId()) == null, "o tema so fica salvo ao confirmar");
		clickButtonContaining("langcraft.theme.cerejeira");
		context.waitTicks(2);
		clickButtonContaining("langcraft.theme.confirm");
		context.waitForScreen(LanguageScreen.class);
		check(UiTheme.CHERRY.id().equals(context.computeOnClient(mc -> WorldFlashcards.themeId())), "o tema escolhido fica salvo");
		context.waitTicks(2);
		context.takeScreenshot("idioma-escolha");
		check(!serverReady(), "sem escolher, os desafios nao comecam");
		check(view() == null, "nenhum desafio antes de escolher");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> mc.screen instanceof LanguageScreen), "Esc nao fecha a tela de idioma");

		chooseLanguages("korean", "japanese");
		context.waitForScreen(MicrophoneScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("microfone-escolha");
		check(!serverReady(), "escolhendo o microfone, os desafios nao comecam");
		LangCraft.LOGGER.info("[LangCraftTest] Microfones: {}", Microphones.devices());
		check(Microphones.devices().stream().noneMatch(device -> device.label().contains("Primary") || device.label().contains("prim")), "o padrao do Windows nao pode aparecer duas vezes");
		clickButtonContaining(Microphones.defaultLabelKey());
		context.waitForScreen(VoiceSetupScreen.class);
		context.waitTicks(1);
		check(!serverReady(), "durante o download, os desafios nao comecam");
		waitForPhases(ModelManager.Phase.FAILED);
		context.waitTicks(2);
		context.takeScreenshot("download-falhou");

		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.download.retry"))::equals);
		context.waitTicks(1);
		waitForPhases(ModelManager.Phase.FAILED);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> mc.screen instanceof VoiceSetupScreen), "tentar de novo mantem a tela aberta");

		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.download.no_voice"))::equals);
		context.waitForScreen(null);
		context.waitTicks(5);
		check(serverReady(), "continuar sem voz libera os desafios");
		PlayerProgress progress = world.getServer().computeOnServer(server -> LangCraft.progress(player(server)));
		check(progress.languageChosen() && progress.language().equals(PlayerProgress.KOREAN + "," + PlayerProgress.JAPANESE), "a escolha deve ser salva: " + progress);

		command("clear @p");
		command("give @p minecraft:stone 8");
		LangCraft.config.speakSeconds = 60;
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		ChallengeView speak = view();
		waitUntil(() -> speak.voice.status() == VoiceRecognizer.Status.FAILED);
		context.waitTicks(2);
		context.takeScreenshot("fala-sem-voz");
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 40);
		command("gamemode survival @p");
		LangCraft.config.speakSeconds = 3;
	}

	private void downloadAfterLanguageCommand() throws Exception {
		command("execute as @p run lang language both");
		context.waitForScreen(VoiceSetupScreen.class);
		check(!serverReady(), "durante o download, os desafios esperam");
		command("execute as @p run lang challenge item speak");
		context.waitTicks(2);
		check(view() == null, "o quiz nao aparece durante o download");
		waitUntil(() -> ModelManager.phase(PlayerProgress.KOREAN) == ModelManager.Phase.DOWNLOADING || ModelManager.phase(PlayerProgress.JAPANESE) == ModelManager.Phase.DOWNLOADING);
		context.waitTicks(2);
		context.takeScreenshot("download-progresso");
		waitUntil(() -> context.computeOnClient(mc -> mc.screen == null));
		context.waitTicks(5);
		check(ModelManager.phase(PlayerProgress.KOREAN) == ModelManager.Phase.READY, "modelo coreano pronto");
		check(ModelManager.phase(PlayerProgress.JAPANESE) == ModelManager.Phase.READY, "modelo japones pronto");
		check(serverReady(), "depois do download, os desafios comecam");

		command("give @p minecraft:stone 8");
		LangCraft.config.speakSeconds = 30;
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		ChallengeView speak = view();
		long startedAt = System.currentTimeMillis();
		waitUntil(() -> speak.voice.status() == expectedVoiceStatus());
		long elapsed = System.currentTimeMillis() - startedAt;
		LangCraft.LOGGER.info("[LangCraftTest] Voz pronta {} ms depois do inicio do desafio", elapsed);
		check(!hasMicrophone() || elapsed < 3000, "com o modelo pronto, a voz deve abrir logo: " + elapsed + " ms");
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 40);
		command("gamemode survival @p");
		LangCraft.config.speakSeconds = 3;
	}

	private void noDownloadScreenWhenReady() {
		command("execute as @p run lang language korean");
		context.waitTicks(5);
		check(context.computeOnClient(mc -> mc.screen == null), "idioma baixado nao abre tela");
		check(serverReady(), "idioma baixado nao segura os desafios");
		command("execute as @p run lang language both");
		context.waitTicks(5);
	}

	private void newWorldAsksLanguage() throws Exception {
		world.close();
		world = context.worldBuilder().create();
		context.waitForScreen(ThemeScreen.class);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> WorldFlashcards.themeId()) == null, "um mundo novo pergunta o tema de novo");
		clickButtonContaining("langcraft.theme.confirm");
		context.waitForScreen(LanguageScreen.class);
		check(context.computeOnClient(mc -> WorldFlashcards.current().size(null)) == 0, "um mundo novo comeca sem flashcards");
		chooseLanguages("korean");
		context.waitForScreen(MicrophoneScreen.class);
		context.waitTicks(1);
		clickButtonContaining(Microphones.defaultLabelKey());
		context.waitForScreen(MicTestScreen.class);
		check(!serverReady(), "durante o teste do microfone, os desafios nao comecam");
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.skip"))::equals);
		context.waitFor(mc -> mc.screen == null, 40);
		context.waitTicks(5);
		check(serverReady(), "pular o teste libera os desafios");
		check(world.getServer().computeOnServer(server -> LangCraft.progress(player(server)).language()).equals(PlayerProgress.KOREAN), "coreano salvo");
	}

	/** Palavra de teste com os dois idiomas: erro, acerto e passagem para a palavra seguinte. */
	private void microphoneTestFlow() throws Exception {
		world.close();
		world = context.worldBuilder().create();
		context.waitForScreen(ThemeScreen.class);
		context.waitTicks(2);
		clickButtonContaining("langcraft.theme.confirm");
		context.waitForScreen(LanguageScreen.class);
		chooseLanguages("korean", "japanese");
		context.waitForScreen(MicrophoneScreen.class);
		context.waitTicks(1);
		clickButtonContaining(Microphones.defaultLabelKey());
		context.waitForScreen(MicTestScreen.class);
		MicTestScreen screen = context.computeOnClient(mc -> (MicTestScreen) mc.screen);
		waitUntil(() -> screen.voice() != null && screen.voice().status() == expectedVoiceStatus());
		context.waitTicks(10);
		context.takeScreenshot("teste-coreano");
		LangCraft.LOGGER.info("[LangCraftTest] Volume lido do microfone: {}", screen.voice().level());
		check(!screen.word().isEmpty(), "a palavra de teste deve existir");

		String korean = screen.word();
		context.runOnClient(mc -> screen.onHeard("errado", false));
		context.waitTicks(2);
		context.takeScreenshot("teste-erro");
		context.runOnClient(mc -> screen.onHeard("가나다라마바사".repeat(30), false));
		context.waitTicks(2);
		context.takeScreenshot("teste-texto-longo");
		context.runOnClient(mc -> screen.onHeard(korean, true));
		context.waitTicks(2);
		context.takeScreenshot("teste-acerto");
		check(screen.voice() == null, "ao acertar, o microfone fecha");

		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.next"))::equals);
		context.waitTicks(2);
		check(!screen.word().equals(korean), "a segunda palavra deve ser em japones");
		waitUntil(() -> screen.voice() != null && screen.voice().status() == expectedVoiceStatus());
		context.takeScreenshot("teste-japones");

		// Trocar de microfone volta para a mesma palavra.
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.change_microphone"))::equals);
		context.waitForScreen(MicrophoneScreen.class);
		context.waitTicks(1);
		List<Microphones.Device> microphones = Microphones.devices();
		Microphones.Device chosen = microphones.isEmpty() ? null : microphones.getFirst();
		clickButton(label -> chosen == null || label.contains(chosen.label()));
		context.waitForScreen(MicTestScreen.class);
		check(context.computeOnClient(mc -> mc.screen) == screen, "volta para o mesmo teste");
		waitUntil(() -> screen.voice() != null && screen.voice().status() == expectedVoiceStatus());
		check(chosen == null || chosen.id().equals(LangCraft.config.microphone), "o microfone escolhido fica salvo");
		check(!screen.voice().usingFallback(), "o microfone escolhido deve abrir, sem cair no padrao");
		LangCraft.LOGGER.info("[LangCraftTest] Ouvindo no microfone escolhido: {}", chosen);

		String japanese = screen.word();
		context.runOnClient(mc -> screen.onHeard(japanese, true));
		context.waitTicks(1);
		check(!serverReady(), "antes de continuar, os desafios nao comecam");
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.continue"))::equals);
		context.waitFor(mc -> mc.screen == null, 40);
		context.waitTicks(5);
		check(serverReady(), "terminar o teste libera os desafios");

		// Um microfone que sumiu nao pode impedir a voz.
		LangCraft.config.microphone = "Microfone que nao existe";
		command("give @p minecraft:stone 8");
		LangCraft.config.speakSeconds = 30;
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		ChallengeView speak = view();
		waitUntil(() -> speak.voice.status() == expectedVoiceStatus());
		check(!hasMicrophone() || speak.voice.usingFallback(), "microfone que sumiu deve cair no padrao");
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 40);
		command("gamemode survival @p");
		LangCraft.config.microphone = "";
		LangCraft.config.speakSeconds = 3;

		// A partir da configuracao, o teste volta para ela.
		context.setScreen(() -> new LangConfigScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("config-com-modelos");
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.config.test"))::equals);
		context.waitForScreen(MicTestScreen.class);
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.skip_word"))::equals);
		context.waitTicks(2);
		clickButton(context.computeOnClient(mc -> I18n.get("langcraft.test.skip"))::equals);
		context.waitForScreen(LangConfigScreen.class);
		context.setScreen(() -> null);
	}

	private void chooseLanguages(String... commandNames) {
		for (String name : commandNames) {
			clickButtonContaining("langcraft.setup." + name);
			context.waitTicks(2);
		}
		clickButtonContaining("langcraft.setup.confirm");
	}

	/** Digita no chat, como o jogador, porque o chat se fecha depois de enviar o comando e poderia fechar a tela. */
	private void chat(String text) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		context.getInput().typeChars(text);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(4);
	}

	private void chatCommands() throws Exception {
		chat("/lang theme");
		check(context.computeOnClient(mc -> mc.screen instanceof ThemeScreen), "/lang theme abre a tela de theme");
		UiTheme before = context.computeOnClient(mc -> UiTheme.current());
		clickButtonContaining("langcraft.theme.hanji");
		context.waitTicks(2);
		check(UiTheme.current() == UiTheme.HANJI, "o tema marcado vale na hora");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
		check(UiTheme.current() == before, "cancelar volta ao tema anterior");

		chat("/lang theme");
		clickButtonContaining("langcraft.theme.hanji");
		context.waitTicks(2);
		clickButtonContaining("langcraft.theme.confirm");
		context.waitForScreen(null);
		check(UiTheme.HANJI.id().equals(context.computeOnClient(mc -> WorldFlashcards.themeId())), "confirmar salva o tema");

		chat("/lang language");
		check(context.computeOnClient(mc -> mc.screen instanceof LanguageScreen), "/lang language abre a tela de language");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
		check(currentLanguage().equals(PlayerProgress.BOTH), "cancelar nao muda o idioma");

		chat("/lang config");
		check(context.computeOnClient(mc -> mc.screen instanceof LangConfigScreen), "/lang config abre a configuracao");
		context.waitTicks(2);
		context.takeScreenshot("config-no-jogo");
		clickButtonContaining("langcraft.config.theme");
		context.waitForScreen(ThemeScreen.class);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(LangConfigScreen.class);
		context.waitTicks(2);
		clickButtonContaining("langcraft.config.language");
		context.waitForScreen(LanguageScreen.class);
		clickButtonContaining("langcraft.setup.japanese");
		context.waitTicks(2);
		clickButtonContaining("langcraft.setup.confirm");
		waitUntil(() -> context.computeOnClient(mc -> mc.screen == null) && serverReady());
		check(currentLanguage().equals(PlayerProgress.KOREAN), "a tela de idioma troca o idioma");

		chat("/lang language japanese");
		waitUntil(() -> context.computeOnClient(mc -> mc.screen == null) && serverReady());
		check(currentLanguage().equals(PlayerProgress.JAPANESE), "/lang language japanese escolhe direto");
		command("clear @p");
		command("give @p minecraft:stone 8");
		chat("/lang challenge item quiz");
		check(context.computeOnClient(mc -> mc.screen instanceof QuizScreen), "/lang challenge continua funcionando pelo chat");
		context.runOnClient(mc -> WorldFlashcards.saveThemeId(before.id()));
	}

	private String currentLanguage() {
		return world.getServer().computeOnServer(server -> LangCraft.progress(player(server)).language());
	}

	/** Registra cada tela em cada tema, para conferir as capturas. */
	private void themeScreenshots() throws Exception {
		String original = context.computeOnClient(mc -> WorldFlashcards.themeId());
		RouletteStartPayload.Kind[] kinds = RouletteStartPayload.Kind.values();
		for (UiTheme theme : UiTheme.selectable()) {
			String name = "tema-" + theme.id();
			context.runOnClient(mc -> WorldFlashcards.saveThemeId(theme.id()));
			LangCraft.config.quizSeconds = 60;
			LangCraft.config.speakSeconds = 60;
			// Com a roleta invertida ligada, a sequência aparece. Zerada, ela não abre no meio das capturas.
			LangCraft.config.rewardRouletteEnabled = true;
			onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).afterLoss()));
			command("clear @p");
			command("give @p minecraft:cobblestone 64");
			command("give @p minecraft:apple 8");
			command("time set day");

			context.setScreen(() -> new ThemeScreen((Screen) null));
			context.waitTicks(2);
			context.takeScreenshot(name + "-escolha");
			context.setScreen(() -> new LanguageScreen(null, List.of(PlayerProgress.KOREAN), language -> { }));
			context.waitTicks(2);
			context.takeScreenshot(name + "-idioma");
			context.setScreen(() -> new MicrophoneScreen(null, microphone -> { }));
			context.waitTicks(2);
			context.takeScreenshot(name + "-microfone");
			context.setScreen(() -> new VoiceSetupScreen(PlayerProgress.languagesOf(PlayerProgress.BOTH), () -> { }, () -> { }));
			context.waitTicks(1);
			context.takeScreenshot(name + "-download");
			context.setScreen(() -> null);

			language(PlayerProgress.JAPANESE);
			start("item quiz");
			context.waitForScreen(QuizScreen.class);
			context.waitTicks(2);
			context.takeScreenshot(name + "-popup-ja");
			ChallengeView quiz = view();
			clickButton(label -> !label.equals(quiz.word));
			context.waitTicks(3);
			context.takeScreenshot(name + "-popup-erro");
			command("gamemode spectator @p");
			context.waitFor(mc -> ChallengeClient.current() == null, 40);
			command("gamemode survival @p");
			context.waitTicks(2);
			context.setScreen(() -> new StudyScreen(null));
			context.waitTicks(2);
			context.takeScreenshot(name + "-estudo");
			context.setScreen(() -> null);

			language(PlayerProgress.KOREAN);
			start("item fala");
			context.waitFor(mc -> ChallengeClient.current() != null, 20);
			context.waitTicks(3);
			context.takeScreenshot(name + "-fala");
			context.getInput().pressKey(LangCraftClient.typeAnswerKey);
			context.waitForScreen(TypeAnswerScreen.class);
			context.waitTicks(2);
			context.takeScreenshot(name + "-digitar");
			context.setScreen(() -> null);
			command("gamemode spectator @p");
			context.waitFor(mc -> ChallengeClient.current() == null, 40);
			command("gamemode survival @p");
			context.waitTicks(2);

			start("item segurar");
			context.waitFor(mc -> ChallengeClient.current() != null, 20);
			context.waitTicks(3);
			context.takeScreenshot(name + "-segurar");
			command("gamemode spectator @p");
			context.waitFor(mc -> ChallengeClient.current() == null, 40);
			command("gamemode survival @p");
			context.waitTicks(2);

			for (RouletteStartPayload.Kind kind : kinds) {
				context.setScreen(() -> new RouletteScreen(new RouletteStartPayload(1, kind, "minecraft:cobblestone", 64, List.of(1, 16, 32, 48, 64), List.of(25, 25, 20, 15, 15), 200)));
				context.waitTicks(2);
				context.takeScreenshot(name + "-roleta-" + kind.name().toLowerCase(java.util.Locale.ROOT));
			}
			context.setScreen(() -> null);
			context.waitTicks(2);
		}
		context.runOnClient(mc -> WorldFlashcards.saveThemeId(original));
	}

	private void modMenuConfig() throws Exception {
		context.waitForScreen(net.minecraft.client.gui.screens.TitleScreen.class);
		context.clickScreenButton("modmenu.title");
		context.waitForScreen(com.terraformersmc.modmenu.gui.ModsScreen.class);
		context.waitTicks(3);
		context.takeScreenshot("modmenu-lista");
		Screen settings = context.computeOnClient(mc -> com.terraformersmc.modmenu.ModMenu.getConfigScreen("langcraft", mc.screen));
		check(settings instanceof LangConfigScreen, "a engrenagem deve abrir a configuracao: " + settings);
		context.setScreen(() -> settings);
		context.waitTicks(3);
		context.takeScreenshot("config");

		boolean furigana = LangCraft.config.showFurigana;
		clickButtonContaining("langcraft.config.furigana");
		context.waitTicks(1);
		check(LangCraft.config.showFurigana != furigana, "o botao de furigana deve alternar a opcao");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(com.terraformersmc.modmenu.gui.ModsScreen.class);
		String saved = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("langcraft.json"));
		check(saved.contains("\"showFurigana\": " + !furigana), "fechar deve salvar a configuracao");
		LangCraft.config.showFurigana = furigana;
		LangCraft.config.save();
		context.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());
	}

	/** Em uma máquina sem microfone, como um teste no Linux em contêiner, a voz termina em NO_MIC. */
	private static boolean hasMicrophone() {
		return !Microphones.devices().isEmpty();
	}

	private static VoiceRecognizer.Status expectedVoiceStatus() {
		return hasMicrophone() ? VoiceRecognizer.Status.LISTENING : VoiceRecognizer.Status.NO_MIC;
	}

	private void waitForPhases(ModelManager.Phase phase) throws InterruptedException {
		waitUntil(() -> ModelManager.phase(PlayerProgress.KOREAN) == phase && ModelManager.phase(PlayerProgress.JAPANESE) == phase);
		if (phase == ModelManager.Phase.FAILED) {
			check(!ModelManager.failureReason(PlayerProgress.KOREAN).isEmpty(), "a falha precisa de um motivo para mostrar");
			check(!ModelManager.failedAfterDownload(PlayerProgress.KOREAN), "falha de conexao nao e falha ao carregar");
		}
	}

	private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 6 * 60_000;
		while (!condition.getAsBoolean()) {
			check(System.currentTimeMillis() < deadline, "tempo esgotado");
			Thread.sleep(100);
			context.waitTick();
		}
	}

	private void scenario(String name, Scenario scenario) {
		try {
			reset();
			scenario.run();
			LangCraft.LOGGER.info("[LangCraftTest] PASSOU: {}", name);
		} catch (Exception | AssertionError e) {
			failures.add(name + " -> " + e);
			LangCraft.LOGGER.error("[LangCraftTest] FALHOU: {}", name, e);
			context.takeScreenshot("FALHA-" + failures.size());
		}
	}

	/** Volta a um estado limpo: sem desafio, sem roleta, sem tela aberta e com inventário vazio. */
	private void reset() {
		command("gamemode spectator @p");
		context.waitTicks(3);
		command("gamemode survival @p");
		command("clear @p");
		command("kill @e[type=warden]");
		command("kill @e[type=item]");
		command("difficulty peaceful");
		context.setScreen(() -> null);
		LangCraft.config.mobTiers = List.of(new MobPunishment.Tier("teste", 1, List.of("minecraft:chicken")));
		LangCraft.config.rewardRouletteEnabled = false;
		LangCraft.config.quizSeconds = 3;
		LangCraft.config.speakSeconds = 3;
		LangCraft.config.holdSeconds = 4;
		LangCraft.config.rouletteAutoSpinSeconds = 2;
		onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).afterLoss().withLanguage(PlayerProgress.BOTH)));
		context.waitFor(mc -> ChallengeClient.current() == null, 100);
		context.waitTicks(2);
	}

	private void namesLoad() {
		context.runOnClient(mc -> {
			check("검".length() > 0 && GameNames.name(PlayerProgress.KOREAN, SubjectType.ITEM, "minecraft:diamond_sword").orElse("").contains("검"), "coreano sem 검");
			check(GameNames.name(PlayerProgress.JAPANESE, SubjectType.ITEM, "minecraft:diamond_sword").orElse("").contains("剣"), "japones sem 剣");
			check(GameNames.name(PlayerProgress.KOREAN, SubjectType.MOB, "minecraft:warden").isPresent(), "warden sem nome em coreano");
			check(GameNames.name(PlayerProgress.JAPANESE, SubjectType.MOB, "minecraft:axolotl").isPresent(), "axolote sem nome em japones");
			check(GameNames.allNames(PlayerProgress.KOREAN, SubjectType.ITEM).size() > 1000, "poucos nomes de itens");
		});
	}

	private void furiganaShown() throws java.io.IOException {
		// Grava a leitura de todos os nomes japoneses, para conferir os erros do dicionario.
		java.util.List<String> lines = context.computeOnClient(mc -> {
			java.util.List<String> result = new ArrayList<>();
			for (SubjectType type : SubjectType.values()) {
				for (String name : GameNames.allNames(PlayerProgress.JAPANESE, type)) {
					result.add(type + "	" + Furigana.inline(name, PlayerProgress.JAPANESE));
				}
			}
			return result;
		});
		java.nio.file.Files.write(java.nio.file.Path.of("furigana-check.txt"), lines);

		command("give @p minecraft:diamond_sword 1");
		language(PlayerProgress.JAPANESE);
		LangCraft.config.speakSeconds = 30;
		LangCraft.config.quizSeconds = 15;
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		context.waitTicks(3);
		context.takeScreenshot("furigana-alerta");
		check(Furigana.hasReadings(view().segments), "espada de diamante deve ter leitura");
		ChallengeView speak = view();
		context.getInput().pressKey(LangCraftClient.typeAnswerKey);
		context.waitForScreen(TypeAnswerScreen.class);
		context.getInput().typeChars(speak.word);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(40);

		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("furigana-popup");
		ChallengeView quiz = view();
		clickButton(label -> label.equals(quiz.word));
		context.waitTicks(3);

		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(3);
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("furigana-popup-1080p");
		ChallengeView large = view();
		clickButton(label -> label.equals(large.word));
		context.waitTicks(40);
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		context.waitTicks(3);
		context.takeScreenshot("furigana-alerta-1080p");
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(3);
	}

	private void quizWrongPunishes() {
		command("give @p minecraft:stone 64");
		language(PlayerProgress.KOREAN);
		LangCraft.config.quizSeconds = 60;
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		ChallengeView view = view();
		context.waitTicks(2);
		clickButton(label -> !label.equals(view.word));
		context.waitTicks(3);
		context.takeScreenshot("quiz-erro");
		check(context.computeOnClient(mc -> mc.screen instanceof QuizScreen), "mostra a alternativa certa antes da punicao");
		check(count(Items.STONE) == 64, "a roleta ainda nao rodou");
		context.waitForScreen(RouletteScreen.class);
		check(streak() == 0, "errar zera a sequencia");
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 64 - amount, "errar uma vez exclui a fatia sorteada");
		context.waitForScreen(null);

		check(context.computeOnClient(mc -> WorldFlashcards.current().size(List.of(PlayerProgress.KOREAN))) > 0, "a palavra entra nos flashcards");
		ScorePayload score = context.computeOnClient(mc -> ChallengeClient.score());
		check(score != null && score.totalWrong() > 0 && score.streak() == 0, "o placar recebe o erro: " + score);
		chat("/lang study");
		check(context.computeOnClient(mc -> mc.screen instanceof StudyScreen), "/lang study abre os flashcards");
		context.waitTicks(2);
		context.takeScreenshot("estudo-frente");
		clickButtonContaining("langcraft.study.show");
		context.waitTicks(2);
		context.takeScreenshot("estudo-verso");
		clickButtonContaining("langcraft.study.right");
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);

		chat("/lang words");
		check(context.computeOnClient(mc -> mc.screen instanceof WordListScreen list && list.wordCount() > 0), "/lang words mostra as words");
		context.waitTicks(2);
		context.takeScreenshot("lista-de-palavras");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);

		chat("/lang alphabet");
		check(context.computeOnClient(mc -> mc.screen instanceof AlphabetScreen), "/lang alphabet abre o treino");
		context.waitTicks(2);
		context.takeScreenshot("alfabeto-tabela");
		clickButtonContaining("langcraft.alphabet.practice");
		context.waitTicks(2);
		context.takeScreenshot("alfabeto-treino");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private void quizWrongThenRight() {
		command("give @p minecraft:stone 64");
		language(PlayerProgress.KOREAN);
		LangCraft.config.quizSeconds = 15;
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		ChallengeView view = view();
		check(view != null && view.minigame == Minigame.QUIZ, "sem desafio de popup");
		context.waitTicks(2);
		context.takeScreenshot("quiz-coreano");

		clickButton(label -> label.equals(view.word));
		context.waitTicks(3);
		context.takeScreenshot("quiz-acerto");
		check(context.computeOnClient(mc -> mc.screen == null), "popup deve fechar ao acertar");
		check(count(Items.STONE) == 64, "acerto nao deve excluir");
		check(streak() == 1, "acerto soma sequencia");
		check(world.getServer().computeOnServer(server -> server.getScoreboard().getPlayersTeam(player(server).getScoreboardName()) != null), "o jogador recebe o titulo");
		check(world.getServer().computeOnServer(server -> server.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST) != null), "a lista do Tab mostra os acertos");

		language(PlayerProgress.JAPANESE);
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("quiz-japones");
		ChallengeView japanese = view();
		clickButton(label -> label.equals(japanese.word));
		context.waitTicks(3);
		LangCraft.config.quizSeconds = 3;
	}

	private void quizTimeoutSpace() {
		command("give @p minecraft:stone 64");
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitForScreen(RouletteScreen.class);
		check(view() == null, "desafio deveria ter terminado");
		context.waitTicks(2);
		context.takeScreenshot("roleta-punicao");
		check(count(Items.STONE) == 64, "antes de girar nada e excluido");
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 20);
		int amount = rouletteAmount();
		context.waitTicks(30);
		context.takeScreenshot("roleta-girando");
		check(count(Items.STONE) == 64, "durante o giro nada e excluido");
		context.waitTicks(SPIN_WAIT_TICKS - 30);
		context.takeScreenshot("roleta-resultado");
		check(count(Items.STONE) == 64 - amount, "exclui " + amount + ", restaram " + count(Items.STONE));
		context.waitForScreen(null);
	}

	private void rouletteButton() {
		command("give @p minecraft:stone 32");
		start("item quiz");
		context.waitForScreen(RouletteScreen.class);
		context.waitTicks(1);
		clickButton(label -> true);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 20);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 32 - amount, "botao: exclui " + amount + ", restaram " + count(Items.STONE));
		context.waitForScreen(null);
	}

	private void rouletteAutoSpin() {
		command("give @p minecraft:stone 16");
		start("item quiz");
		context.waitForScreen(RouletteScreen.class);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 16 - amount, "auto: exclui " + amount + ", restaram " + count(Items.STONE));
		context.waitForScreen(null);
	}

	private void singleItemNoRoulette() {
		command("give @p minecraft:diamond_sword 1");
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitFor(mc -> mc.screen == null, 80);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "pilha de 1 nao abre roleta");
		check(count(Items.DIAMOND_SWORD) == 0, "espada deveria ter sido excluida");
	}

	private void speakTyped() {
		command("give @p minecraft:diamond_sword 1");
		language(PlayerProgress.JAPANESE);
		LangCraft.config.speakSeconds = 30;
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		context.waitTicks(4);
		context.takeScreenshot("fala-alerta");
		ChallengeView view = view();
		check(view.minigame == Minigame.SPEAK, "deveria ser fala");

		context.getInput().pressKey(LangCraftClient.typeAnswerKey);
		context.waitForScreen(TypeAnswerScreen.class);
		context.getInput().typeChars("errado");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		context.takeScreenshot("fala-erro");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(null);
		view.heard = "ダイヤモンドの剣".repeat(30);
		context.waitTicks(2);
		context.takeScreenshot("fala-texto-longo");
		context.getInput().pressKey(LangCraftClient.typeAnswerKey);
		context.waitForScreen(TypeAnswerScreen.class);
		context.getInput().typeChars("errado");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		check(view.wrongFlashTicks > 0, "erro deve piscar");
		check(count(Items.DIAMOND_SWORD) == 1, "erro nao exclui");
		for (int i = 0; i < "errado".length(); i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
		}
		context.getInput().typeChars(view.word);
		context.takeScreenshot("fala-digitando");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(3);
		context.takeScreenshot("fala-acerto");
		check(context.computeOnClient(mc -> mc.screen == null), "caixa fecha ao acertar");
		check(streak() == 1, "acerto soma sequencia");
		check(count(Items.DIAMOND_SWORD) == 1, "acerto nao exclui");
		LangCraft.config.speakSeconds = 3;
		context.waitFor(mc -> ChallengeClient.current() == null, 60);
	}

	private void speakTimeout() {
		command("give @p minecraft:stone 8");
		start("item fala");
		context.waitForScreen(RouletteScreen.class);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 8 - amount, "fala: exclui " + amount);
		context.waitForScreen(null);
	}

	private void movedStack() {
		command("give @p minecraft:stone 64");
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		onServer(server -> {
			var inventory = player(server).getInventory();
			inventory.setItem(20, inventory.getItem(0));
			inventory.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
		});
		context.waitForScreen(RouletteScreen.class);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 64 - amount, "pilha movida: exclui " + amount);
		context.waitForScreen(null);
	}

	private void droppedItem() {
		command("give @p minecraft:stone 64");
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		command("clear @p");
		context.waitTicks(LangCraft.config.quizSeconds * 20 + 5);
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "sem o item nao ha roleta");
		check(view() == null, "desafio deveria ter terminado");
	}

	private void deathCancels() {
		command("give @p minecraft:stone 64");
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		command("kill @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 20);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitTicks(40);
		context.setScreen(() -> null);
		check(view() == null, "morte deve cancelar");
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "morte nao abre roleta");
	}

	private void spectatorCancels() {
		command("give @p minecraft:stone 64");
		start("item fala");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 20);
		command("gamemode survival @p");
		check(count(Items.STONE) == 64, "espectador nao perde itens");
	}

	private void busyWhenInventoryOpen() {
		command("give @p minecraft:stone 64");
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(2);
		start("item quiz");
		context.waitTicks(5);
		check(context.computeOnClient(mc -> mc.screen instanceof InventoryScreen), "inventario deve continuar aberto");
		check(view() == null, "desafio adiado nao aparece");
		context.setScreen(() -> null);
		context.waitTicks(LangCraft.config.quizSeconds * 20 + 5);
		check(count(Items.STONE) == 64, "adiado nao pune");
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "adiado nao abre roleta");
	}

	private void emptyInventory() {
		start("item quiz");
		context.waitTicks(5);
		check(view() == null, "sem itens nao ha desafio");
		LangCraft.config.itemIntervalMinSeconds = 1;
		LangCraft.config.itemIntervalMaxSeconds = 1;
		command("execute as @p run lang on items");
		context.waitTicks(40);
		check(view() == null, "automatico com inventario vazio nao gera desafio");
		command("execute as @p run lang off");
		LangCraft.config.itemIntervalMinSeconds = FAR_FUTURE_SECONDS;
		LangCraft.config.itemIntervalMaxSeconds = FAR_FUTURE_SECONDS;
	}

	private void holdWrongThenRight() {
		LangCraft.config.holdSeconds = 20;
		command("item replace entity @p hotbar.0 with minecraft:stone 8");
		command("item replace entity @p hotbar.1 with minecraft:dirt 8");
		command("item replace entity @p hotbar.2 with minecraft:cobblestone 8");
		selectHotbar(2);
		start("item segurar");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		ChallengeView view = view();
		check(view.minigame == Minigame.HOLD, "deveria ser pegue na mao");
		check(!view.icon.is(Items.COBBLESTONE), "item sorteado nao pode ser o da mao");
		context.waitTicks(3);
		context.takeScreenshot("segurar-alerta");

		// O item que já estava na mão no início não conta como erro.
		context.waitTicks(24);
		context.takeScreenshot("segurar-erro");
		check(ChallengeClient.current() == view, "o item que ja estava na mao nao encerra");
		check(streak() == 0, "item errado nao conta");

		selectHotbar(view.icon.is(Items.STONE) ? 0 : 1);
		context.waitTicks(25);
		context.takeScreenshot("segurar-acerto");
		check(streak() == 1, "segurar o certo vence");
		check(count(Items.STONE) == 8 && count(Items.DIRT) == 8, "acerto nao exclui");
		context.waitFor(mc -> ChallengeClient.current() == null, 60);

		// Trocar para outro item e ficar nele por 1 segundo já é a derrota.
		context.waitTicks(1);
		selectHotbar(2);
		start("item segurar");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		ChallengeView second = view();
		selectHotbar(second.icon.is(Items.STONE) ? 1 : 0);
		context.waitForScreen(RouletteScreen.class);
		check(streak() == 0, "segurar outro item perde a rodada");
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 200);
		context.waitTicks(SPIN_WAIT_TICKS);
		context.waitForScreen(null);
		LangCraft.config.holdSeconds = 4;
	}

	private void holdTimeout() {
		command("item replace entity @p hotbar.0 with minecraft:stone 8");
		command("item replace entity @p hotbar.1 with minecraft:dirt 8");
		command("item replace entity @p hotbar.8 with minecraft:cobblestone 8");
		selectHotbar(4);
		start("item segurar");
		context.waitForScreen(RouletteScreen.class);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		int total = count(Items.STONE) + count(Items.DIRT) + count(Items.COBBLESTONE);
		check(total == 24 - amount, "segurar: exclui " + amount + ", total " + total);
		context.waitForScreen(null);
		selectHotbar(0);
	}

	private void holdCommandNeverBecomesQuiz() {
		// O único tipo de item está na mão: não há o que pedir, então o comando recusa.
		command("item replace entity @p hotbar.0 with minecraft:stone 8");
		selectHotbar(0);
		start("item segurar");
		context.waitTicks(3);
		check(view() == null, "item unico na mao deve ser recusado, sem virar popup");
		check(context.computeOnClient(mc -> !(mc.screen instanceof QuizScreen)), "o comando segurar nao abre popup");

		// O único tipo de item está fora da mão: dá para jogar.
		LangCraft.config.holdSeconds = 20;
		selectHotbar(4);
		start("hold");
		context.waitFor(mc -> ChallengeClient.current() != null, 20);
		check(view().minigame == Minigame.HOLD, "o comando segurar deve iniciar o pegue na mao");
		context.waitTicks(3);
		context.takeScreenshot("segurar-pelo-comando");
		selectHotbar(0);
		context.waitTicks(25);
		check(streak() == 1, "segurar o item vence");
		context.waitFor(mc -> ChallengeClient.current() == null, 60);
	}

	private void mobWinNoSpawn() {
		wardenOnly();
		command("difficulty normal");
		command("gamemode creative @p");
		command("give @p minecraft:stone 8");
		LangCraft.config.quizSeconds = 15;
		start("mob quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("mob-quiz-warden");
		ChallengeView view = view();
		check(view.type == SubjectType.MOB, "deveria ser desafio de mob");
		check(view.icon.is(Items.WARDEN_SPAWN_EGG), "icone deve ser o ovo do warden");
		clickButton(label -> label.equals(view.word));
		context.waitTicks(3);
		check(wardens() == 0, "acerto nao gera mob");
		check(streak() == 1, "acerto de mob conta sequencia");
		LangCraft.config.quizSeconds = 3;
	}

	private void mobLossOutdoors() {
		wardenOnly();
		command("difficulty normal");
		command("gamemode creative @p");
		command("give @p minecraft:stone 8");
		start("mob quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitFor(mc -> mc.screen == null, 100);
		context.waitTicks(2);
		context.takeScreenshot("mob-warden-nasceu");
		check(wardens() == 1, "warden deveria nascer, ha " + wardens());
		double[] heights = context.getInput() == null ? null : world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			Warden warden = player.level().getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(16)).getFirst();
			return new double[] {warden.getY(), player.getBoundingBox().maxY, warden.getX() - player.getX(), warden.getZ() - player.getZ()};
		});
		check(heights[0] > heights[1], "warden deve nascer acima da cabeca: " + heights[0] + " vs " + heights[1]);
		check(Math.abs(heights[2]) < 1.5 && Math.abs(heights[3]) < 1.5, "warden deve nascer em cima do jogador");
		check(count(Items.STONE) == 8, "perder desafio de mob nao exclui itens");
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "mob nao abre roleta");
		command("kill @e[type=warden]");
	}

	private void mobLossLowCeiling() {
		wardenOnly();
		command("difficulty normal");
		command("gamemode creative @p");
		command("execute at @p run fill ~-6 ~-1 ~-6 ~6 ~4 ~6 minecraft:air");
		command("execute at @p run fill ~-6 ~2 ~-6 ~6 ~2 ~6 minecraft:stone");
		context.waitTicks(2);
		start("mob quiz");
		context.waitForScreen(QuizScreen.class);
		context.waitFor(mc -> mc.screen == null, 100);
		context.waitTicks(2);
		context.takeScreenshot("mob-warden-caverna");
		check(wardens() == 1, "warden deve nascer mesmo em teto baixo, ha " + wardens());
		command("kill @e[type=warden]");
		command("execute at @p run fill ~-6 ~2 ~-6 ~6 ~2 ~6 minecraft:air");
	}

	private void mobPeacefulFilter() {
		wardenOnly();
		command("difficulty peaceful");
		start("mob quiz");
		context.waitTicks(5);
		check(view() == null, "pacifico nao deve sortear warden");
		LangCraft.config.mobTiers = List.of(
				new MobPunishment.Tier("hostil", 1000, List.of("minecraft:warden")),
				new MobPunishment.Tier("calmo", 1, List.of("minecraft:axolotl")));
		start("mob quiz");
		context.waitTicks(5);
		ChallengeView view = view();
		check(view != null && view.icon.is(Items.AXOLOTL_SPAWN_EGG), "pacifico deve cair no axolote");
		context.waitFor(mc -> mc.screen == null && ChallengeClient.current() == null, 100);
		command("kill @e[type=axolotl]");
	}

	private void mobHoldRefused() {
		command("give @p minecraft:stone 8");
		command("execute as @p run lang challenge mob hold");
		context.waitTicks(5);
		check(view() == null, "mob com segurar deve ser recusado");
	}

	private void rewardRoulette() {
		LangCraft.config.rewardRouletteEnabled = true;
		LangCraft.config.rewardStreak = 2;
		LangCraft.config.quizSeconds = 15;
		command("give @p minecraft:stone 64");
		winQuiz();
		check(streak() == 1, "primeiro acerto");
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "ainda nao ha premio");
		winQuiz();
		context.waitForScreen(RouletteScreen.class);
		check(streak() == 0, "sequencia zera apos o premio");
		check(context.computeOnClient(mc -> ((RouletteScreen) mc.screen).kind() == RouletteStartPayload.Kind.REWARD), "deve ser a roleta de premio");
		context.waitTicks(2);
		context.takeScreenshot("roleta-premio");
		context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 20);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		context.takeScreenshot("roleta-premio-resultado");
		check(count(Items.STONE) == 64 + amount, "premio: +" + amount + ", total " + count(Items.STONE));
		context.waitForScreen(null);
		LangCraft.config.quizSeconds = 3;
	}

	private void rewardFullInventory() {
		LangCraft.config.rewardRouletteEnabled = true;
		LangCraft.config.rewardStreak = 1;
		LangCraft.config.quizSeconds = 15;
		for (int i = 0; i < 36; i++) {
			String slot = i < 9 ? "hotbar." + i : "inventory." + (i - 9);
			command("item replace entity @p " + slot + " with minecraft:stone 64");
		}
		command("item replace entity @p weapon.offhand with minecraft:stone 64");
		winQuiz();
		context.waitForScreen(RouletteScreen.class);
		context.waitFor(mc -> mc.screen instanceof RouletteScreen roulette && roulette.resultAmount() > 0, 60);
		int amount = rouletteAmount();
		context.waitTicks(SPIN_WAIT_TICKS);
		check(count(Items.STONE) == 37 * 64, "inventario cheio nao muda, total " + count(Items.STONE));
		int dropped = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
					.mapToInt(entity -> entity.getItem().getCount()).sum();
		});
		check(dropped == amount, "excesso cai no chao: " + dropped + " de " + amount);
		context.waitForScreen(null);
		LangCraft.config.quizSeconds = 3;
	}

	private void rewardOnlyUnstackable() {
		LangCraft.config.rewardRouletteEnabled = true;
		LangCraft.config.rewardStreak = 1;
		LangCraft.config.quizSeconds = 15;
		command("give @p minecraft:diamond_sword 1");
		int levels = world.getServer().computeOnServer(server -> player(server).experienceLevel);
		winQuiz();
		context.waitTicks(3);
		check(context.computeOnClient(mc -> !(mc.screen instanceof RouletteScreen)), "sem empilhavel nao ha roleta");
		int after = world.getServer().computeOnServer(server -> player(server).experienceLevel);
		check(after == levels + 5, "deve ganhar 5 niveis: " + levels + " -> " + after);
		LangCraft.config.quizSeconds = 3;
	}

	private void streakResetsOnLoss() {
		command("give @p minecraft:stone 64");
		LangCraft.config.quizSeconds = 15;
		winQuiz();
		winQuiz();
		check(streak() == 2, "duas vitorias");
		LangCraft.config.quizSeconds = 2;
		start("item quiz");
		context.waitForScreen(RouletteScreen.class);
		check(streak() == 0, "perder zera a sequencia");
		context.waitFor(mc -> mc.screen == null, 200);
		LangCraft.config.quizSeconds = 3;
	}

	private void simultaneousTimers() {
		command("give @p minecraft:stone 64");
		LangCraft.config.itemIntervalMinSeconds = 1;
		LangCraft.config.itemIntervalMaxSeconds = 1;
		LangCraft.config.mobIntervalMinSeconds = 1;
		LangCraft.config.mobIntervalMaxSeconds = 1;
		LangCraft.config.speakSeconds = 3;
		LangCraft.config.holdSeconds = 3;
		LangCraft.config.rouletteAutoSpinSeconds = 1;
		command("gamemode creative @p");
		command("execute as @p run lang on");
		context.waitFor(mc -> ChallengeClient.current() != null, 800);
		ChallengeView first = view();
		check(first != null, "um desafio automatico deve aparecer");
		context.waitTicks(30);
		check(view() == first, "o outro intervalo nao interrompe o desafio atual");
		context.waitFor(mc -> ChallengeClient.current() != first && !(mc.screen instanceof QuizScreen), 200);
		context.waitFor(mc -> !(mc.screen instanceof RouletteScreen), 300);
		// Depois do fim, o próximo desafio só pode começar após os 30 segundos de espera.
		context.waitTicks(200);
		check(view() == null, "deve esperar 30 s entre desafios");
		context.waitFor(mc -> ChallengeClient.current() != null, 600);
		check(view() != null, "o desafio seguinte deve aparecer depois da espera");
		command("execute as @p run lang off");
		LangCraft.config.itemIntervalMinSeconds = FAR_FUTURE_SECONDS;
		LangCraft.config.itemIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		LangCraft.config.mobIntervalMinSeconds = FAR_FUTURE_SECONDS;
		LangCraft.config.mobIntervalMaxSeconds = FAR_FUTURE_SECONDS;
		LangCraft.config.rouletteAutoSpinSeconds = 2;
		command("gamemode spectator @p");
		context.waitFor(mc -> ChallengeClient.current() == null, 40);
		command("kill @e[type=chicken]");
	}

	private void voicePipeline() throws InterruptedException {
		for (String language : List.of(PlayerProgress.KOREAN, PlayerProgress.JAPANESE)) {
			command("clear @p");
			command("give @p minecraft:stone 8");
			language(language);
			LangCraft.config.speakSeconds = 3600;
			start("item fala");
			context.waitFor(mc -> ChallengeClient.current() != null, 20);
			ChallengeView view = view();
			VoiceRecognizer voice = view.voice;
			check(voice != null, "fala deve iniciar a voz");
			long deadline = System.currentTimeMillis() + 15 * 60_000;
			boolean sawDownload = false;
			VoiceRecognizer.Status status = voice.status();
			while (System.currentTimeMillis() < deadline
					&& (status == VoiceRecognizer.Status.PREPARING || status == VoiceRecognizer.Status.DOWNLOADING)) {
				if (status == VoiceRecognizer.Status.DOWNLOADING && !sawDownload) {
					sawDownload = true;
					context.waitTicks(2);
					context.takeScreenshot("voz-baixando-" + language);
				}
				Thread.sleep(500);
				context.waitTick();
				status = voice.status();
			}
			context.waitTicks(2);
			context.takeScreenshot("voz-pronta-" + language);
			LangCraft.LOGGER.info("[LangCraftTest] Voz {}: status {}, baixou agora: {}", language, status, sawDownload);
			check(status == expectedVoiceStatus(), "voz de " + language + " terminou em " + status + ", microfone presente: " + hasMicrophone());
			LangCraft.config.speakSeconds = 3;
			command("gamemode spectator @p");
			context.waitFor(mc -> ChallengeClient.current() == null, 40);
			command("gamemode survival @p");
		}
	}

	/** Alimenta o Vosk com áudio gravado em voice-samples/<idioma>/, se existir. Só relata a taxa de acerto. */
	private void voiceSamples() throws Exception {
		for (String language : List.of(PlayerProgress.KOREAN, PlayerProgress.JAPANESE)) {
			java.nio.file.Path dir = java.nio.file.Path.of("voice-samples", language);
			if (!java.nio.file.Files.exists(dir.resolve("expected.txt"))) {
				LangCraft.LOGGER.info("[LangCraftTest] Sem amostras de voz para {}", language);
				continue;
			}
			Model model = ModelManager.model(language).get();
			List<String> expected = java.nio.file.Files.readAllLines(dir.resolve("expected.txt"));
			List<String> names = context.computeOnClient(mc -> GameNames.allNames(language, SubjectType.ITEM));
			int accepted = 0;
			int falsePositives = 0;
			for (int i = 0; i < expected.size(); i++) {
				java.nio.file.Path wav = dir.resolve(i + ".wav");
				String target = expected.get(i);
				String wrongTarget = expected.get((i + 7) % expected.size());
				double limit = LangCraft.config.voiceSimilarity;
				boolean ok = heardCorrectly(model, wav, target, names, language, limit);
				boolean falsePositive = heardCorrectly(model, wav, wrongTarget, names, language, limit);
				accepted += ok ? 1 : 0;
				falsePositives += falsePositive ? 1 : 0;
				LangCraft.LOGGER.info("[LangCraftTest] Amostra {} {}: esperado '{}', {}", language, i, target, ok ? "ACEITO" : "recusado");
			}
			LangCraft.LOGGER.info("[LangCraftTest] Amostras {}: {} de {} aceitas, {} falsos positivos", language, accepted, expected.size(), falsePositives);
			check(accepted * 2 > expected.size(), language + ": so " + accepted + " amostras aceitas");
			check(falsePositives * 10 <= expected.size(), language + ": " + falsePositives + " falsos positivos");

			// O microfone precisa continuar ouvindo depois da primeira palavra, em uma captura só.
			int phrases = phrasesHeardInARow(model, dir, Math.min(6, expected.size()), names, language);
			LangCraft.LOGGER.info("[LangCraftTest] Frases seguidas em {}: {}", language, phrases);
			check(phrases >= 3, language + ": so ouviu " + phrases + " frases seguidas");
		}
		check(ModelManager.whisper() != null, "o Whisper deve carregar neste sistema");
	}

	private static int phrasesHeardInARow(Model model, java.nio.file.Path dir, int count, List<String> names, String language) throws Exception {
		java.util.concurrent.atomic.AtomicInteger heard = new java.util.concurrent.atomic.AtomicInteger();
		try (SpeechPipeline pipeline = new SpeechPipeline(model, 16000f, VoskGrammar.build("-", names, language, new java.util.Random(1)), null, null, "",
				text -> false, text -> heard.incrementAndGet())) {
			byte[] buffer = new byte[4096];
			byte[] silence = new byte[4096];
			for (int i = 0; i < count; i++) {
				try (javax.sound.sampled.AudioInputStream audio = javax.sound.sampled.AudioSystem.getAudioInputStream(dir.resolve(i + ".wav").toFile())) {
					int read;
					while ((read = audio.read(buffer)) > 0) {
						pipeline.feed(buffer, read);
					}
				}
				for (int j = 0; j < 16; j++) {
					pipeline.feed(silence, silence.length);
				}
			}
			pipeline.finish();
		}
		return heard.get();
	}

	private static boolean heardCorrectly(Model model, java.nio.file.Path wav, String target, List<String> names, String language, double limit) throws Exception {
		java.util.concurrent.atomic.AtomicBoolean accepted = new java.util.concurrent.atomic.AtomicBoolean();
		java.util.function.Predicate<String> accepts = text -> AnswerMatcher.matchesSpoken(target, text, language, limit);
		boolean korean = language.equals(PlayerProgress.KOREAN);
		try (javax.sound.sampled.AudioInputStream audio = javax.sound.sampled.AudioSystem.getAudioInputStream(wav.toFile());
				SpeechPipeline pipeline = new SpeechPipeline(model, audio.getFormat().getSampleRate(), VoskGrammar.build(target, names, language, new java.util.Random(1)),
						korean ? ModelManager.whisper() : null, korean ? "ko" : null, VoskGrammar.whisperPrompt(target, names, new java.util.Random(1)), accepts, text -> {
							if (accepts.test(text)) {
								accepted.set(true);
							}
						})) {
			byte[] buffer = new byte[4096];
			int read;
			while ((read = audio.read(buffer)) > 0) {
				pipeline.feed(buffer, read);
			}
			// O microfone continua aberto depois da fala: o silêncio é o que encerra a frase.
			byte[] silence = new byte[4096];
			for (int i = 0; i < 16; i++) {
				pipeline.feed(silence, silence.length);
			}
			pipeline.finish();
		}
		return accepted.get();
	}

	private void disconnectDuringRoulette() throws InterruptedException {
		LangCraft.config.rewardRouletteEnabled = false;
		LangCraft.config.quizSeconds = 15;
		command("give @p minecraft:stone 64");
		winQuiz();
		winQuiz();
		winQuiz();
		check(streak() == 3, "tres vitorias antes de sair");

		// A roleta só abre ao perder, o que zera a sequência. Por isso, a persistência é testada antes.
		TestWorldSave save = world.getWorldSave();
		world.close();
		world = save.open();
		world.getClientWorld().waitForChunksRender();
		waitUntil(this::serverReady);
		check(context.computeOnClient(mc -> !(mc.screen instanceof LanguageScreen)), "a escolha do idioma so aparece na primeira entrada");
		check(streak() == 3, "sequencia deve persistir apos voltar ao mundo");

		LangCraft.config.quizSeconds = 1;
		LangCraft.config.rouletteAutoSpinSeconds = 3600;
		start("item quiz");
		context.waitForScreen(RouletteScreen.class);
		save = world.getWorldSave();
		world.close();
		world = save.open();
		world.getClientWorld().waitForChunksRender();
		waitUntil(this::serverReady);
		int remaining = count(Items.STONE);
		LangCraft.LOGGER.info("[LangCraftTest] Depois de sair durante a roleta: {} de 64", remaining);
		check(remaining < 64, "sair durante a roleta ainda deve excluir, restaram " + remaining);
		LangCraft.config.rouletteAutoSpinSeconds = 2;
		LangCraft.config.quizSeconds = 3;
	}

	private void winQuiz() {
		start("item quiz");
		context.waitForScreen(QuizScreen.class);
		ChallengeView view = view();
		context.waitTicks(1);
		clickButton(label -> label.equals(view.word));
		context.waitTicks(3);
	}

	private void wardenOnly() {
		LangCraft.config.mobTiers = List.of(new MobPunishment.Tier("teste", 1, List.of("minecraft:warden")));
	}

	private int wardens() {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			return player.level().getEntities(EntityType.WARDEN, entity -> true).size();
		});
	}

	private void start(String arguments) {
		command("execute as @p run lang challenge " + arguments);
		context.waitTicks(2);
	}

	private void language(String language) {
		onServer(server -> LangCraft.setProgress(player(server), LangCraft.progress(player(server)).withLanguage(language)));
	}

	private void selectHotbar(int slot) {
		context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
		context.waitTicks(5);
		check(world.getServer().computeOnServer(server -> player(server).getInventory().getSelectedSlot()) == slot, "servidor nao recebeu o slot " + slot);
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

	/** Procura também dentro de listas, como a da tela de configuração. */
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

	private int rouletteAmount() {
		return context.computeOnClient(mc -> mc.screen instanceof RouletteScreen roulette ? roulette.resultAmount() : -1);
	}

	private ChallengeView view() {
		return context.computeOnClient(mc -> ChallengeClient.current());
	}

	private int streak() {
		return world.getServer().computeOnServer(server -> LangCraft.progress(player(server)).streak());
	}

	private int count(Item item) {
		return world.getServer().computeOnServer(server -> player(server).getInventory().countItem(item));
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
