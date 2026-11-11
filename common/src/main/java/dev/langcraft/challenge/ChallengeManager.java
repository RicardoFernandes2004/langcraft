package dev.langcraft.challenge;

import dev.langcraft.LangCraft;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeResultPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.ChallengeTogglePayload;
import dev.langcraft.net.LanguageChoicePayload;
import dev.langcraft.net.PreferencesPayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteSpinPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.TreasurePayload;
import dev.langcraft.net.VoiceSetupPayload;
import dev.langcraft.treasure.TreasureIndex;
import dev.langcraft.treasure.TreasureService;
import dev.langcraft.treasure.TreasureTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Controla, no servidor, o momento dos desafios, os prazos, as roletas e as consequências. */
public final class ChallengeManager {
	private static final int TICKS_PER_SECOND = 20;
	private static final int RETRY_DELAY_TICKS = 30 * TICKS_PER_SECOND;
	private static final int NOTHING_TO_ASK_RETRY_TICKS = 10 * TICKS_PER_SECOND;
	/** Duração da animação da roleta. Os itens só mudam quando ela termina. */
	private static final int SPIN_TICKS = 80;
	private static final int TICKS_PER_DAY = 24000;
	private static final int NEVER = Integer.MIN_VALUE;
	/** De quanto em quanto tempo o bioma e o inventário são conferidos atrás de descobertas. */
	private static final int DISCOVERY_CHECK_TICKS = TICKS_PER_SECOND;

	private final LangConfig config;
	private final TreasureService treasures;
	private final Map<UUID, PlayerState> states = new HashMap<>();
	private final Random random = new Random();
	private @Nullable Map<String, String> installedModMobs;
	private int nextId = 1;
	private int now;

	public ChallengeManager(LangConfig config) {
		this.config = config;
		this.treasures = new TreasureService(config);
	}

	public TreasureService treasures() {
		return treasures;
	}

	/** Pedido por comando: abre um tesouro assim que o jogador estiver livre. */
	public void grantTreasure(ServerPlayer player) {
		state(player).pendingTreasures++;
	}

	/** Inicia um desafio pedido por comando. Retorna a chave da mensagem de erro, se não puder começar. */
	public @Nullable String startManual(ServerPlayer player, SubjectType type, @Nullable Minigame minigame) {
		if (!LangCraft.platform.canSend(player, ChallengeStartPayload.TYPE)) {
			return "langcraft.command.no_mod";
		}
		PlayerState state = state(player);
		if (!state.ready) {
			return "langcraft.command.not_ready";
		}
		if (state.active != null || state.roulette != null) {
			return "langcraft.command.busy";
		}
		if (minigame == Minigame.HOLD && holdableItems(player, state).isEmpty() && !InventoryPicker.itemTypes(player, excludedItems(player, state)).isEmpty()) {
			return "langcraft.command.hold_needs_other";
		}
		if (!start(player, state, type, minigame)) {
			return switch (type) {
				case ITEM -> "langcraft.command.no_items";
				case MOB -> "langcraft.command.no_mobs";
				case BIOME -> "langcraft.command.no_biome";
			};
		}
		return null;
	}

	/** Abre uma roleta de efeito pedida por comando. Retorna a chave da mensagem de erro, se não puder abrir. */
	public @Nullable String startRoulette(ServerPlayer player, RouletteStartPayload.Kind kind) {
		if (!LangCraft.platform.canSend(player, RouletteStartPayload.TYPE)) {
			return "langcraft.command.no_mod";
		}
		PlayerState state = state(player);
		if (!state.ready) {
			return "langcraft.command.not_ready";
		}
		if (state.active != null || state.roulette != null) {
			return "langcraft.command.busy";
		}
		return startEffectRoulette(player, state, kind) ? null : "langcraft.command.no_slices";
	}

	/** Volta a contar o intervalo do zero, para que um desafio não apareça assim que o jogador religar. */
	public void restartTimers(ServerPlayer player) {
		state(player).timers.restart(itemInterval(), mobInterval());
	}

	/** Depois de mudar de idioma, os desafios esperam o cliente garantir o modelo de voz. */
	public void onLanguageChanged(ServerPlayer player) {
		if (LangCraft.platform.canSend(player, VoiceSetupPayload.TYPE)) {
			state(player).ready = false;
			LangCraft.platform.send(player, new VoiceSetupPayload(false, progress(player).language()));
		}
	}

	public boolean isReady(ServerPlayer player) {
		PlayerState state = states.get(player.getUUID());
		return state != null && state.ready;
	}

	public void onLanguageChoice(ServerPlayer player, LanguageChoicePayload payload) {
		if (PlayerProgress.isValidLanguage(payload.language())) {
			boolean changing = progress(player).languageChosen();
			setProgress(player, progress(player).withLanguage(payload.language()));
			// Na primeira escolha, o cliente já está no meio da configuração.
			if (changing) {
				onLanguageChanged(player);
			}
		}
	}

	public void onClientReady(ServerPlayer player) {
		PlayerState state = state(player);
		state.ready = true;
		if (!state.timersStarted) {
			state.timersStarted = true;
			restartTimers(player);
		}
	}

	public void onServerTick(MinecraftServer server) {
		now = server.getTickCount();
		treasures.prepare(server);
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (LangCraft.platform.canSend(player, ChallengeStartPayload.TYPE)) {
				PlayerState state = state(player);
				if (!state.setupSent) {
					state.setupSent = true;
					PlayerProgress progress = progress(player);
					LangCraft.platform.send(player, new VoiceSetupPayload(!progress.languageChosen(), progress.language()));
					syncScore(player);
					syncPreferences(player);
				}
				tickPlayer(player, state);
			}
		}
	}

	private void tickPlayer(ServerPlayer player, PlayerState state) {
		if (player.isSpectator()) {
			cancel(player);
			return;
		}
		trackCombat(player, state);
		if (!player.isAlive()) {
			return;
		}
		if (state.roulette != null) {
			tickRoulette(player, state);
			return;
		}
		if (state.active != null) {
			tickChallenge(player, state);
			return;
		}
		if (!state.ready) {
			return;
		}
		if (state.pendingTreasures > 0 && !inCombat(state) && openTreasure(player, state, true)) {
			return;
		}
		if (now % DISCOVERY_CHECK_TICKS == 0 && checkDiscoveries(player, state)) {
			return;
		}
		PlayerProgress progress = progress(player);
		boolean items = progress.itemsEnabled() && anyTimedAllowed(progress, SubjectType.ITEM);
		boolean mobs = progress.mobsEnabled() && anyTimedAllowed(progress, SubjectType.MOB);
		ChallengeTimers.Due due = state.timers.tick(items, mobs,
				maxTicks(config.itemIntervalMinSeconds, config.itemIntervalMaxSeconds), maxTicks(config.mobIntervalMinSeconds, config.mobIntervalMaxSeconds));
		switch (due) {
			case ITEM -> {
				if (start(player, state, SubjectType.ITEM, null)) {
					state.timers.itemStarted(itemInterval(), minTicks(config.mobIntervalMinSeconds) / 2);
				} else {
					// Sem item no inventário agora. Um item pego daqui a pouco não pode esperar um intervalo inteiro.
					state.timers.retryItem(NOTHING_TO_ASK_RETRY_TICKS);
				}
			}
			case MOB -> {
				if (start(player, state, SubjectType.MOB, null)) {
					state.timers.mobStarted(mobInterval(), minTicks(config.itemIntervalMinSeconds) / 2);
				} else {
					state.timers.retryMob(NOTHING_TO_ASK_RETRY_TICKS);
				}
			}
			case NONE -> {
			}
		}
	}

	private static int currentDay(ServerPlayer player) {
		return (int) (player.level().getServer().overworld().getDayTime() / TICKS_PER_DAY);
	}

	private void tickChallenge(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		if (challenge.holdRound != null) {
			ItemStack hand = player.getMainHandItem();
			switch (challenge.holdRound.tick(hand.isEmpty() ? null : hand.getItem())) {
				case WON -> {
					win(player, state);
					return;
				}
				case LOST -> {
					expire(player, state);
					return;
				}
				case NONE -> {
				}
			}
		}
		if (now >= challenge.deadline) {
			expire(player, state);
		}
	}

	private boolean start(ServerPlayer player, PlayerState state, SubjectType type, @Nullable Minigame requested) {
		return switch (type) {
			case ITEM -> startItem(player, state, requested);
			case MOB -> startMob(player, state, requested);
			case BIOME -> startBiome(player, state);
		};
	}

	/** Pedido por comando: o bioma onde o jogador está, mesmo que já tenha sido visitado. */
	private boolean startBiome(ServerPlayer player, PlayerState state) {
		String biome = currentBiome(player);
		if (biome == null) {
			return false;
		}
		begin(player, state, ChallengeKind.BIOME, biome, Minigame.SPEAK);
		return true;
	}

	/** Os minigames de intervalo que o jogador deixou ligados para o tipo. No combate, só a fala, que não pede clique nem troca de item. */
	private List<Minigame> allowedMinigames(ServerPlayer player, PlayerState state, SubjectType type) {
		PlayerProgress progress = progress(player);
		List<Minigame> allowed = new ArrayList<>();
		for (Minigame minigame : Minigame.values()) {
			ChallengeKind kind = ChallengeKind.timed(type, minigame);
			if (kind != null && progress.allows(kind.id()) && (minigame == Minigame.SPEAK || !inCombat(state))) {
				allowed.add(minigame);
			}
		}
		return allowed;
	}

	private static boolean anyTimedAllowed(PlayerProgress progress, SubjectType type) {
		for (ChallengeKind kind : ChallengeKind.values()) {
			if (kind.timed() && kind.type() == type && progress.allows(kind.id())) {
				return true;
			}
		}
		return false;
	}

	private boolean startItem(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		List<Item> candidates = InventoryPicker.itemTypes(player, excludedItems(player, state));
		if (candidates.isEmpty()) {
			return false;
		}
		List<Minigame> allowed = requested != null ? List.of(requested) : allowedMinigames(player, state, SubjectType.ITEM);
		if (allowed.isEmpty()) {
			return false;
		}
		Minigame minigame = allowed.get(random.nextInt(allowed.size()));
		if (minigame == Minigame.HOLD) {
			List<Item> holdable = holdableItems(player, state);
			// Só o sorteio automático troca de minigame. Quem pediu pelo comando recebe o que pediu.
			if (requested == null && (InventoryPicker.itemTypes(player, Set.of()).size() < 2 || holdable.isEmpty())) {
				List<Minigame> others = allowed.stream().filter(other -> other != Minigame.HOLD).toList();
				if (others.isEmpty()) {
					return false;
				}
				minigame = others.get(random.nextInt(others.size()));
			} else {
				candidates = holdable;
			}
		}
		String subject = InventoryPicker.idOf(candidates.get(random.nextInt(candidates.size())));
		begin(player, state, minigame, SubjectType.ITEM, subject, requested);
		return true;
	}

	/** Itens que podem ser pedidos no Pegue na mão: nunca o que já está na mão. */
	private List<Item> holdableItems(ServerPlayer player, PlayerState state) {
		ItemStack hand = player.getMainHandItem();
		return InventoryPicker.itemTypes(player, excludedItems(player, state)).stream().filter(item -> !hand.is(item)).toList();
	}

	/** Os itens sem nome no idioma, que só valem nesta sessão, os bloqueados pelo jogador e os bloqueados para todos. */
	private Set<String> excludedItems(ServerPlayer player, PlayerState state) {
		Set<String> excluded = new HashSet<>(state.unavailableItems);
		excluded.addAll(config.blockedItems);
		excluded.addAll(progress(player).preferences().blockedItems());
		return excluded;
	}

	/** Bloqueia um item só para o jogador. */
	public boolean blockItem(ServerPlayer player, String id) {
		PlayerProgress progress = progress(player);
		if (progress.blocks(id)) {
			return false;
		}
		setProgress(player, progress.withBlocked(id, true));
		cancelIfAbout(player, id);
		return true;
	}

	public boolean unblockItem(ServerPlayer player, String id) {
		PlayerProgress progress = progress(player);
		if (!progress.blocks(id)) {
			return false;
		}
		setProgress(player, progress.withBlocked(id, false));
		return true;
	}

	/** Bloqueia um item para todos os jogadores e salva a configuração. Só para administradores. */
	public boolean blockItemForAll(ServerPlayer player, String id) {
		if (config.blockedItems.contains(id)) {
			return false;
		}
		config.blockedItems.add(id);
		config.save();
		for (ServerPlayer online : player.level().getServer().getPlayerList().getPlayers()) {
			cancelIfAbout(online, id);
		}
		return true;
	}

	public boolean unblockItemForAll(String id) {
		if (!config.blockedItems.remove(id)) {
			return false;
		}
		config.save();
		return true;
	}

	/** Se o item for a palavra do desafio em andamento, cancela sem castigo, porque ele pode ser justamente o que trava a tela. */
	private void cancelIfAbout(ServerPlayer player, String id) {
		PlayerState state = states.get(player.getUUID());
		if (state != null && state.active != null && state.active.type == SubjectType.ITEM && state.active.subject.equals(id)) {
			cancel(player);
		}
	}

	public void onToggle(ServerPlayer player, ChallengeTogglePayload payload) {
		if (ChallengeKind.byId(payload.challenge()) != null) {
			setProgress(player, progress(player).withChallenge(payload.challenge(), payload.enabled()));
			syncPreferences(player);
		}
	}

	private void syncPreferences(ServerPlayer player) {
		if (LangCraft.platform.canSend(player, PreferencesPayload.TYPE)) {
			LangCraft.platform.send(player, new PreferencesPayload(List.copyOf(progress(player).preferences().disabledChallenges())));
		}
	}

	/**
	 * Bioma novo e item novo no inventário, um por vez e com um intervalo entre eles. Na primeira conferência,
	 * o que o jogador já tem e o bioma onde ele está entram sem desafio. Retorna verdadeiro se começou um desafio.
	 */
	private boolean checkDiscoveries(ServerPlayer player, PlayerState state) {
		PlayerProgress progress = progress(player);
		String biome = currentBiome(player);
		if (!progress.discoveries().started()) {
			progress = progress.withDiscoveriesStarted(inventoryIds(player));
			setProgress(player, biome == null ? progress : progress.withVisitedBiome(biome));
			return false;
		}
		if (now < state.nextDiscoveryAt) {
			return false;
		}
		// O /lang off do administrador também vale para as descobertas. O que passa desligado entra sem desafio.
		boolean biomes = progress.allows(ChallengeKind.BIOME.id()) && (progress.itemsEnabled() || progress.mobsEnabled());
		boolean items = progress.allows(ChallengeKind.NEW_ITEM.id()) && progress.itemsEnabled();
		if (biome != null && !progress.discoveries().biomes().contains(biome)) {
			if (biomes) {
				return discover(player, state, ChallengeKind.BIOME, biome);
			}
			setProgress(player, progress.withVisitedBiome(biome));
		}
		Set<String> excluded = excludedItems(player, state);
		for (String item : inventoryIds(player)) {
			if (progress(player).discoveries().items().contains(item)) {
				continue;
			}
			if (items && !excluded.contains(item)) {
				return discover(player, state, ChallengeKind.NEW_ITEM, item);
			}
			setProgress(player, progress(player).withSeenItem(item));
		}
		return false;
	}

	private boolean discover(ServerPlayer player, PlayerState state, ChallengeKind kind, String subject) {
		begin(player, state, kind, subject, null);
		state.nextDiscoveryAt = now + Math.max(0, config.discoveryCooldownSeconds) * TICKS_PER_SECOND;
		return true;
	}

	/** Guarda a descoberta só quando o desafio dela termina, para que um desafio recusado volte mais tarde. */
	private void markDiscovered(ServerPlayer player, Challenge challenge) {
		if (challenge.kind == ChallengeKind.BIOME) {
			setProgress(player, progress(player).withVisitedBiome(challenge.subject));
		} else if (challenge.kind == ChallengeKind.NEW_ITEM) {
			setProgress(player, progress(player).withSeenItem(challenge.subject));
		}
	}

	private static @Nullable String currentBiome(ServerPlayer player) {
		return player.level().getBiome(player.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse(null);
	}

	private static Set<String> inventoryIds(ServerPlayer player) {
		Set<String> ids = new LinkedHashSet<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				ids.add(InventoryPicker.idOf(stack.getItem()));
			}
		}
		return ids;
	}

	private boolean startMob(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		Optional<String> mob = MobPunishment.draw(mobTiers(),
				id -> !state.unavailableMobs.contains(id) && MobPunishment.canSpawn(id, player.serverLevel()), random);
		if (mob.isEmpty()) {
			return false;
		}
		List<Minigame> allowed = requested != null ? List.of(requested) : allowedMinigames(player, state, SubjectType.MOB);
		if (allowed.isEmpty()) {
			return false;
		}
		begin(player, state, allowed.get(random.nextInt(allowed.size())), SubjectType.MOB, mob.get(), requested);
		return true;
	}

	/** Os mods instalados não mudam com o jogo aberto, então a busca só acontece uma vez. */
	private List<MobPunishment.Tier> mobTiers() {
		if (!config.includeModMobs) {
			return config.mobTiers;
		}
		if (installedModMobs == null) {
			installedModMobs = ModMobs.installed();
		}
		return ModMobs.merge(config.mobTiers, installedModMobs, config.blockedMobs);
	}

	private void begin(ServerPlayer player, PlayerState state, Minigame minigame, SubjectType type, String subject, @Nullable Minigame requested) {
		begin(player, state, ChallengeKind.timed(type, minigame), subject, requested, minigame);
	}

	private void begin(ServerPlayer player, PlayerState state, ChallengeKind kind, String subject, @Nullable Minigame requested) {
		begin(player, state, kind, subject, requested, kind.minigame());
	}

	private void begin(ServerPlayer player, PlayerState state, ChallengeKind kind, String subject, @Nullable Minigame requested, Minigame minigame) {
		SubjectType type = kind.type();
		int seconds = switch (minigame) {
			case SPEAK -> config.speakSeconds;
			case QUIZ -> config.quizSeconds;
			case HOLD -> config.holdSeconds;
		};
		int duration = seconds * TICKS_PER_SECOND;
		PlayerProgress progress = progress(player);
		Challenge challenge = new Challenge(nextId++, kind, minigame, type, subject, requested, now + duration);
		if (minigame == Minigame.HOLD) {
			ItemStack hand = player.getMainHandItem();
			challenge.holdRound = new HoldRound<>(challenge.item(), hand.isEmpty() ? null : hand.getItem());
		}
		state.active = challenge;
		LangCraft.platform.send(player, new ChallengeStartPayload(challenge.id, minigame, type, subject, resolveLanguage(progress),
				duration, progress.streak(), streakGoal(), requested == null));
	}

	private String resolveLanguage(PlayerProgress progress) {
		List<String> languages = PlayerProgress.languagesOf(progress.language());
		return languages.get(random.nextInt(languages.size()));
	}

	private int streakGoal() {
		return config.rewardRouletteEnabled ? config.rewardStreak : 0;
	}

	public void onResult(ServerPlayer player, ChallengeResultPayload payload) {
		PlayerState state = state(player);
		Challenge challenge = state.active;
		if (challenge == null || challenge.id != payload.id()) {
			return;
		}
		switch (payload.result()) {
			case CORRECT -> {
				if (challenge.minigame != Minigame.HOLD) {
					win(player, state);
				}
			}
			// Na fala, dá para tentar até o tempo acabar. Nas alternativas, a primeira resposta errada já é a derrota.
			case WRONG -> {
				if (challenge.minigame == Minigame.QUIZ) {
					expire(player, state);
				}
			}
			// O modelo de voz não conhece a palavra. Mantém a palavra sorteada e pergunta de outro jeito.
			// No combate, o popup não pode aparecer, então tenta de novo daqui a pouco.
			// As descobertas são só de fala, então uma palavra que não pode ser falada simplesmente passa.
			case NOT_SPEAKABLE -> {
				if (challenge.minigame == Minigame.SPEAK && challenge.requested == null) {
					state.active = null;
					ChallengeKind quiz = ChallengeKind.timed(challenge.type, Minigame.QUIZ);
					if (!challenge.kind.timed()) {
						markDiscovered(player, challenge);
					} else if (quiz != null && progress(player).allows(quiz.id()) && !inCombat(state)) {
						begin(player, state, Minigame.QUIZ, challenge.type, challenge.subject, null);
					} else {
						retry(state, challenge.type, NOTHING_TO_ASK_RETRY_TICKS);
					}
				}
			}
			case UNAVAILABLE -> {
				switch (challenge.type) {
					case ITEM -> state.unavailableItems.add(challenge.subject);
					case MOB -> state.unavailableMobs.add(challenge.subject);
					case BIOME -> {
					}
				}
				state.active = null;
				if (challenge.kind.timed()) {
					start(player, state, challenge.type, challenge.requested);
				} else {
					markDiscovered(player, challenge);
				}
			}
			case BUSY -> {
				state.active = null;
				if (challenge.kind.timed()) {
					retry(state, challenge.type, RETRY_DELAY_TICKS);
				}
			}
		}
	}

	private static void retry(PlayerState state, SubjectType type, int ticks) {
		if (type == SubjectType.ITEM) {
			state.timers.retryItem(ticks);
		} else {
			state.timers.retryMob(ticks);
		}
	}

	/**
	 * O jogo não tem um estado de combate, então conta como combate ter levado dano de um mob nos últimos
	 * segundos. O dano fica guardado por 2 segundos, e o tick passa por ele antes disso.
	 */
	private void trackCombat(ServerPlayer player, PlayerState state) {
		DamageSource source = player.getLastDamageSource();
		if (source != null && source.getEntity() instanceof Mob) {
			state.lastMobHit = now;
		}
	}

	private boolean inCombat(PlayerState state) {
		return state.lastMobHit != NEVER && now - state.lastMobHit < config.combatSeconds * TICKS_PER_SECOND;
	}

	private void syncScore(ServerPlayer player) {
		PlayerProgress progress = progress(player);
		LangCraft.platform.send(player, new ScorePayload(progress.streak(), streakGoal(), progress.bestStreak(), progress.totalCorrect(), progress.totalWrong(),
				progress.lossStreak(), Math.max(0, config.disasterStreak), progress.shields()));
		TitleTeams.apply(player.level().getServer(), player, progress.totalCorrect(), config.showTitles);
	}

	private void announceTitle(ServerPlayer player, PlayerTitle title) {
		player.level().getServer().getPlayerList().broadcastSystemMessage(
				Component.translatableWithFallback("langcraft.message.new_title", "%s is now %s!", player.getName(), title.tag()).withStyle(ChatFormatting.YELLOW), false);
	}

	private void win(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		markDiscovered(player, challenge);
		PlayerTitle titleBefore = PlayerTitle.of(progress(player).totalCorrect());
		PlayerProgress progress = progress(player).afterWin();
		setProgress(player, progress);
		PlayerTitle title = PlayerTitle.of(progress.totalCorrect());
		if (title != titleBefore && config.showTitles) {
			announceTitle(player, title);
		}
		player.giveExperiencePoints(Consequences.winExperience(config.winExperience, progress.streak(), challenge.minigame));
		if (PlayerProgress.milestone(progress.totalCorrect(), config.treasureEvery)) {
			state.pendingTreasures++;
		}
		LangCraft.platform.send(player, new ChallengeEndedPayload(challenge.id, ChallengeEndedPayload.Outcome.WON, progress.streak()));
		finish(state);
		if (progress.rewardDue(streakGoal())) {
			grantShields(player, progress);
		}
		boolean jackpot = config.rewardRouletteEnabled && PlayerProgress.milestone(progress.streak(), config.jackpotStreak)
				&& startEffectRoulette(player, state, RouletteStartPayload.Kind.JACKPOT);
		if (!jackpot && progress.rewardDue(streakGoal())) {
			startReward(player, state);
		}
		syncScore(player);
	}

	private void grantShields(ServerPlayer player, PlayerProgress progress) {
		int shields = Math.min(config.maxShields, progress.shields() + config.rewardShields);
		if (shields > progress.shields()) {
			setProgress(player, progress.withShields(shields));
			player.sendSystemMessage(Component.translatable("langcraft.message.shield_gained", shields).withStyle(ChatFormatting.AQUA));
		}
	}

	/** A diária é um dos castigos possíveis de um erro, e sai no máximo uma vez por dia do jogo. O desastre tem prioridade. */
	private @Nullable RouletteStartPayload.Kind specialPunishment(ServerPlayer player, PlayerProgress progress) {
		if (progress.disasterDue(config.disasterStreak) && !Roulette.usableEffects(config.disasterSlices).isEmpty()) {
			return RouletteStartPayload.Kind.DISASTER;
		}
		boolean daily = config.dailyRouletteEnabled && progress.dailyAvailable(currentDay(player))
				&& !Roulette.usableEffects(config.dailySlices).isEmpty() && random.nextInt(100) < config.dailyChancePercent;
		return daily ? RouletteStartPayload.Kind.DAILY : null;
	}

	/** O escudo só segura o castigo comum. As roletas de desastre e diária não têm como ser canceladas. */
	private void expire(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		markDiscovered(player, challenge);
		PlayerProgress progress = progress(player).afterLoss();
		setProgress(player, progress);
		RouletteStartPayload.Kind special = specialPunishment(player, progress);
		boolean shielded = special == null && progress.shields() > 0;
		Consequences.LossPenalty penalty = Consequences.lossPenalty(progress.lossStreak(), config.lossLevelsPerError);
		if (shielded) {
			setProgress(player, progress.withShields(progress.shields() - 1));
			player.sendSystemMessage(Component.translatable("langcraft.message.shielded").withStyle(ChatFormatting.AQUA));
		} else if (challenge.type == SubjectType.MOB) {
			for (int i = 0; i < (special != null ? 1 : penalty.mobs()); i++) {
				MobPunishment.spawnAbove(player, challenge.subject);
			}
		}
		LangCraft.platform.send(player, new ChallengeEndedPayload(challenge.id, shielded ? ChallengeEndedPayload.Outcome.SHIELDED : ChallengeEndedPayload.Outcome.LOST, 0));
		finish(state);
		if (special != null) {
			if (special == RouletteStartPayload.Kind.DISASTER) {
				applyDebuff(player, Consequences.disasterDebuff(progress.lossStreak(), config.disasterStreak, config.disasterDebuffSeconds));
			}
			startEffectRoulette(player, state, special);
		} else if (!shielded) {
			loseLevels(player, penalty.levels());
			if (challenge.type == SubjectType.ITEM) {
				startPunishment(player, state, challenge.item(), progress.lossStreak());
			}
		}
		syncScore(player);
	}

	private void loseLevels(ServerPlayer player, int amount) {
		int levels = Math.min(amount, player.experienceLevel);
		if (levels > 0) {
			player.giveExperienceLevels(-levels);
			player.sendSystemMessage(Component.translatable("langcraft.message.levels_lost", levels).withStyle(ChatFormatting.RED));
		}
	}

	private void applyDebuff(ServerPlayer player, Consequences.Debuff debuff) {
		if (debuff.seconds() > 0) {
			int ticks = debuff.seconds() * TICKS_PER_SECOND;
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, debuff.amplifier()));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, debuff.amplifier()));
		}
	}

	private void startPunishment(ServerPlayer player, PlayerState state, Item item, int lossStreak) {
		ItemStack stack = InventoryPicker.firstStack(player, item);
		if (stack.isEmpty()) {
			player.sendSystemMessage(Component.translatable("langcraft.message.escaped", item.getDescription()));
			return;
		}
		List<Roulette.Slice> slices = Roulette.buildSlices(stack.getCount(), Roulette.harsher(config.rouletteSlices, lossStreak));
		if (slices.size() <= 1) {
			removeItems(player, item, slices.isEmpty() ? stack.getCount() : slices.get(0).amount());
			return;
		}
		openRoulette(player, state, RouletteStartPayload.Kind.PUNISHMENT, item, stack, slices);
	}

	private void startReward(ServerPlayer player, PlayerState state) {
		List<ItemStack> stacks = InventoryPicker.stacks(player, stack ->
				stack.isStackable() && !config.rewardBlockedItems.contains(InventoryPicker.idOf(stack.getItem())));
		if (stacks.isEmpty()) {
			player.giveExperienceLevels(config.rewardFallbackLevels);
			player.sendSystemMessage(Component.translatable("langcraft.message.levels", config.rewardFallbackLevels));
			return;
		}
		ItemStack stack = stacks.get(random.nextInt(stacks.size()));
		List<Roulette.Slice> slices = Roulette.buildSlices(stack.getCount(), config.rouletteSlices);
		if (slices.size() <= 1) {
			giveItems(player, stack.copy(), slices.isEmpty() ? stack.getCount() : slices.get(0).amount());
			return;
		}
		openRoulette(player, state, RouletteStartPayload.Kind.REWARD, stack.getItem(), stack, slices);
	}

	private void openRoulette(ServerPlayer player, PlayerState state, RouletteStartPayload.Kind kind, Item item, ItemStack stack, List<Roulette.Slice> slices) {
		int autoSpinTicks = config.rouletteAutoSpinSeconds * TICKS_PER_SECOND;
		RouletteRun run = new RouletteRun(nextId++, kind, item, stack.copy(), slices, List.of());
		present(player, state, run, new RouletteStartPayload(run.id, kind, InventoryPicker.idOf(item), stack.getCount(),
				slices.stream().map(Roulette.Slice::amount).toList(), slices.stream().map(Roulette.Slice::weight).toList(), autoSpinTicks, List.of()));
	}

	/**
	 * A roleta é uma tela e prende o mouse, então no meio do combate ela espera. O resultado já está decidido
	 * pelo erro: só a tela fica para depois, e sair do jogo enquanto ela espera não escapa dela.
	 */
	private void present(ServerPlayer player, PlayerState state, RouletteRun run, RouletteStartPayload payload) {
		state.roulette = run;
		run.pending = payload;
		if (inCombat(state)) {
			player.sendSystemMessage(Component.translatable("langcraft.message.roulette_after_combat").withStyle(ChatFormatting.GRAY));
		} else {
			show(player, run);
		}
	}

	private void show(ServerPlayer player, RouletteRun run) {
		LangCraft.platform.send(player, run.pending);
		run.autoSpinAt = now + run.pending.autoSpinTicks();
		run.pending = null;
	}

	/** Abre uma roleta em que cada fatia é um efeito. Retorna falso se a configuração não deixou nenhuma fatia. */
	private boolean startEffectRoulette(ServerPlayer player, PlayerState state, RouletteStartPayload.Kind kind) {
		List<Roulette.EffectSpec> specs = Roulette.usableEffects(switch (kind) {
			case DAILY -> config.dailySlices;
			case DISASTER -> config.disasterSlices;
			default -> config.jackpotSlices;
		});
		if (specs.isEmpty()) {
			return false;
		}
		Item icon = switch (kind) {
			case DAILY -> Items.CLOCK;
			case DISASTER -> Items.WITHER_SKELETON_SKULL;
			default -> Items.NETHER_STAR;
		};
		PlayerProgress progress = progress(player);
		List<Roulette.Slice> slices = specs.stream().map(spec -> new Roulette.Slice(1, spec.weight())).toList();
		List<RouletteEffect> effects = specs.stream().map(Roulette.EffectSpec::effect).toList();
		int autoSpinTicks = config.rouletteAutoSpinSeconds * TICKS_PER_SECOND;
		RouletteRun run = new RouletteRun(nextId++, kind, icon, new ItemStack(icon), slices, effects);
		present(player, state, run, new RouletteStartPayload(run.id, kind, InventoryPicker.idOf(icon),
				kind == RouletteStartPayload.Kind.JACKPOT ? progress.streak() : progress.lossStreak(),
				slices.stream().map(Roulette.Slice::amount).toList(), slices.stream().map(Roulette.Slice::weight).toList(), autoSpinTicks,
				effects.stream().map(RouletteEffect::id).toList()));
		return true;
	}

	public void onSpin(ServerPlayer player, RouletteSpinPayload payload) {
		PlayerState state = state(player);
		if (state.roulette != null && state.roulette.id == payload.id() && state.roulette.pending == null) {
			spin(player, state.roulette);
		}
	}

	private void spin(ServerPlayer player, RouletteRun run) {
		if (run.spun()) {
			return;
		}
		run.sliceIndex = Roulette.draw(run.slices, random);
		run.applyAt = now + SPIN_TICKS;
		LangCraft.platform.send(player, new RouletteResultPayload(run.id, run.sliceIndex, SPIN_TICKS));
	}

	private void tickRoulette(ServerPlayer player, PlayerState state) {
		RouletteRun run = state.roulette;
		if (run.pending != null) {
			if (!inCombat(state)) {
				show(player, run);
			}
			return;
		}
		if (!run.spun()) {
			if (now >= run.autoSpinAt) {
				spin(player, run);
			}
		} else if (now >= run.applyAt) {
			// Antes de aplicar, porque um dos efeitos pode matar o jogador, e a morte cancela o que estiver aberto.
			state.roulette = null;
			state.timers.rest(RETRY_DELAY_TICKS);
			applyRoulette(player, run, false);
			syncScore(player);
		}
	}

	private void applyRoulette(ServerPlayer player, RouletteRun run, boolean leaving) {
		if (!run.effects.isEmpty()) {
			if (run.kind == RouletteStartPayload.Kind.DAILY) {
				setProgress(player, progress(player).withLastDailyDay(currentDay(player)));
			}
			RouletteEffect effect = run.effects.get(run.sliceIndex);
			RouletteEffects.apply(player, leaving && effect.needsPlayerOnline() ? RouletteEffect.LOSE_HALF_INVENTORY : effect, mobTiers(), random);
			return;
		}
		int amount = run.slices.get(run.sliceIndex).amount();
		if (run.kind == RouletteStartPayload.Kind.PUNISHMENT) {
			removeItems(player, run.item, amount);
		} else {
			giveItems(player, run.stack, amount);
		}
	}

	private void removeItems(ServerPlayer player, Item item, int amount) {
		ItemStack stack = InventoryPicker.firstStack(player, item);
		if (!stack.isEmpty()) {
			int removed = Math.min(amount, stack.getCount());
			stack.shrink(removed);
			player.getInventory().setChanged();
			player.sendSystemMessage(Component.translatable("langcraft.message.lost", removed, item.getDescription()));
		}
	}

	private void giveItems(ServerPlayer player, ItemStack template, int amount) {
		player.getInventory().placeItemBackInInventory(template.copyWithCount(amount));
		player.sendSystemMessage(Component.translatable("langcraft.message.gained", amount, template.getItem().getDescription()));
	}

	/** Morrer ou virar espectador encerra tudo sem punição, inclusive uma roleta em andamento. */
	public void cancel(ServerPlayer player) {
		PlayerState state = states.get(player.getUUID());
		if (state == null) {
			return;
		}
		if (state.active != null) {
			LangCraft.platform.send(player, new ChallengeEndedPayload(state.active.id, ChallengeEndedPayload.Outcome.CANCELLED, progress(player).streak()));
			finish(state);
		}
		if (state.roulette != null) {
			LangCraft.platform.send(player, new ChallengeEndedPayload(state.roulette.id, ChallengeEndedPayload.Outcome.CANCELLED, progress(player).streak()));
			state.roulette = null;
			state.timers.rest(RETRY_DELAY_TICKS);
		}
	}

	/**
	 * Abre um tesouro, se a classificação já estiver pronta. O item vai direto para o inventário, e a tela só
	 * revela. Retorna verdadeiro se a tela abriu, para nenhum desafio começar por cima dela.
	 */
	private boolean openTreasure(ServerPlayer player, PlayerState state, boolean show) {
		TreasureIndex index = treasures.ready();
		if (index == null) {
			if (treasures.failed()) {
				state.pendingTreasures = 0;
			}
			return false;
		}
		state.pendingTreasures--;
		PlayerProgress progress = progress(player);
		TreasureService.Prize prize = treasures.draw(index, progress.treasures(), random);
		if (prize == null) {
			player.giveExperienceLevels(config.rewardFallbackLevels);
			player.sendSystemMessage(Component.translatable("langcraft.message.levels", config.rewardFallbackLevels));
			return false;
		}
		setProgress(player, progress.withTreasures(treasures.after(progress.treasures(), prize)));
		Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(prize.item()));
		player.getInventory().placeItemBackInInventory(new ItemStack(item, prize.count()));
		double total = prize.weights().values().stream().mapToDouble(Double::doubleValue).sum();
		List<Integer> chances = new ArrayList<>();
		for (TreasureTier tier : TreasureTier.values()) {
			chances.add((int) Math.round(100 * prize.weights().getOrDefault(tier, 0.0) / total));
		}
		if (show && LangCraft.platform.canSend(player, TreasurePayload.TYPE)) {
			LangCraft.platform.send(player, new TreasurePayload(prize.item(), prize.count(), prize.tier().id(), chances,
					(int) Math.round(prize.luck() * 100), resolveLanguage(progress)));
		}
		player.sendSystemMessage(Component.translatable("langcraft.message.treasure", Component.translatable("langcraft.treasure.tier." + prize.tier().id()),
				prize.count(), item.getDescription()).withStyle(style -> style.withColor(prize.tier().color() & 0xFFFFFF)));
		state.timers.rest(RETRY_DELAY_TICKS);
		return show;
	}

	/** Sair durante um desafio cancela, mas sair durante a roleta não escapa do resultado. */
	public void onPlayerLeft(ServerPlayer player) {
		PlayerState state = states.remove(player.getUUID());
		if (state == null) {
			return;
		}
		// Um tesouro ganho não se perde por sair do jogo antes de ele abrir.
		while (state.pendingTreasures > 0 && treasures.ready() != null) {
			openTreasure(player, state, false);
		}
		if (state.roulette == null) {
			return;
		}
		RouletteRun run = state.roulette;
		if (!run.spun()) {
			run.sliceIndex = Roulette.draw(run.slices, random);
		}
		applyRoulette(player, run, true);
	}

	private void finish(PlayerState state) {
		state.active = null;
		state.timers.rest(RETRY_DELAY_TICKS);
	}

	private PlayerState state(ServerPlayer player) {
		return states.computeIfAbsent(player.getUUID(), uuid -> new PlayerState(new ChallengeTimers(itemInterval(), mobInterval())));
	}

	private int itemInterval() {
		return interval(config.itemIntervalMinSeconds, config.itemIntervalMaxSeconds);
	}

	private int mobInterval() {
		return interval(config.mobIntervalMinSeconds, config.mobIntervalMaxSeconds);
	}

	private int interval(int minSeconds, int maxSeconds) {
		int min = Math.max(1, minSeconds);
		int max = Math.max(min, maxSeconds);
		return (min + random.nextInt(max - min + 1)) * TICKS_PER_SECOND;
	}

	private static int minTicks(int minSeconds) {
		return Math.max(1, minSeconds) * TICKS_PER_SECOND;
	}

	private static int maxTicks(int minSeconds, int maxSeconds) {
		return Math.max(Math.max(1, minSeconds), maxSeconds) * TICKS_PER_SECOND;
	}

	private PlayerProgress progress(ServerPlayer player) {
		return LangCraft.progress(player);
	}

	private void setProgress(ServerPlayer player, PlayerProgress progress) {
		LangCraft.setProgress(player, progress);
	}

	private static final class PlayerState {
		final ChallengeTimers timers;
		boolean setupSent;
		boolean ready;
		boolean timersStarted;
		@Nullable Challenge active;
		@Nullable RouletteRun roulette;
		final Set<String> unavailableItems = new HashSet<>();
		final Set<String> unavailableMobs = new HashSet<>();
		int lastMobHit = NEVER;
		int nextDiscoveryAt;
		/** Tesouros ganhos que ainda não abriram, porque o jogador estava ocupado ou em combate. */
		int pendingTreasures;

		PlayerState(ChallengeTimers timers) {
			this.timers = timers;
		}
	}

	private static final class Challenge {
		final int id;
		final ChallengeKind kind;
		final Minigame minigame;
		final SubjectType type;
		final String subject;
		@Nullable final Minigame requested;
		final int deadline;
		@Nullable HoldRound<Item> holdRound;

		Challenge(int id, ChallengeKind kind, Minigame minigame, SubjectType type, String subject, @Nullable Minigame requested, int deadline) {
			this.id = id;
			this.kind = kind;
			this.minigame = minigame;
			this.type = type;
			this.subject = subject;
			this.requested = requested;
			this.deadline = deadline;
		}

		Item item() {
			return BuiltInRegistries.ITEM.get(new ResourceLocation(subject));
		}
	}

	private static final class RouletteRun {
		final int id;
		final RouletteStartPayload.Kind kind;
		final Item item;
		final ItemStack stack;
		final List<Roulette.Slice> slices;
		final List<RouletteEffect> effects;
		/** A tela que ainda não foi mandada, porque o jogador estava em combate. */
		@Nullable RouletteStartPayload pending;
		int autoSpinAt;
		int sliceIndex = -1;
		int applyAt;

		RouletteRun(int id, RouletteStartPayload.Kind kind, Item item, ItemStack stack, List<Roulette.Slice> slices, List<RouletteEffect> effects) {
			this.id = id;
			this.kind = kind;
			this.item = item;
			this.stack = stack;
			this.slices = slices;
			this.effects = effects;
		}

		boolean spun() {
			return sliceIndex >= 0;
		}
	}
}
