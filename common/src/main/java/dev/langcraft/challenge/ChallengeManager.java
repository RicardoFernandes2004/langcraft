package dev.langcraft.challenge;

import dev.langcraft.LangCraft;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.ChallengeEndedPayload;
import dev.langcraft.net.ChallengeResultPayload;
import dev.langcraft.net.ChallengeStartPayload;
import dev.langcraft.net.LanguageChoicePayload;
import dev.langcraft.net.RouletteResultPayload;
import dev.langcraft.net.RouletteSpinPayload;
import dev.langcraft.net.RouletteStartPayload;
import dev.langcraft.net.ScorePayload;
import dev.langcraft.net.VoiceSetupPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
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

	private final LangConfig config;
	private final Map<UUID, PlayerState> states = new HashMap<>();
	private final Random random = new Random();
	private @Nullable Map<String, String> installedModMobs;
	private int nextId = 1;
	private int now;

	public ChallengeManager(LangConfig config) {
		this.config = config;
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
		if (minigame == Minigame.HOLD && holdableItems(player, state).isEmpty() && !InventoryPicker.itemTypes(player, excludedItems(state)).isEmpty()) {
			return "langcraft.command.hold_needs_other";
		}
		if (!start(player, state, type, minigame)) {
			return type == SubjectType.ITEM ? "langcraft.command.no_items" : "langcraft.command.no_mobs";
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
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (LangCraft.platform.canSend(player, ChallengeStartPayload.TYPE)) {
				PlayerState state = state(player);
				if (!state.setupSent) {
					state.setupSent = true;
					PlayerProgress progress = progress(player);
					LangCraft.platform.send(player, new VoiceSetupPayload(!progress.languageChosen(), progress.language()));
					syncScore(player);
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
		PlayerProgress progress = progress(player);
		ChallengeTimers.Due due = state.timers.tick(progress.itemsEnabled(), progress.mobsEnabled(),
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
		return type == SubjectType.ITEM ? startItem(player, state, requested) : startMob(player, state, requested);
	}

	private boolean startItem(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		List<Item> candidates = InventoryPicker.itemTypes(player, excludedItems(state));
		if (candidates.isEmpty()) {
			return false;
		}
		// No meio do combate, nada que peça clique ou troca de item: só a fala.
		Minigame minigame = requested != null ? requested : inCombat(state) ? Minigame.SPEAK : Minigame.values()[random.nextInt(Minigame.values().length)];
		if (minigame == Minigame.HOLD) {
			List<Item> holdable = holdableItems(player, state);
			// Só o sorteio automático troca de minigame. Quem pediu pelo comando recebe o que pediu.
			if (requested == null && (InventoryPicker.itemTypes(player, Set.of()).size() < 2 || holdable.isEmpty())) {
				minigame = Minigame.QUIZ;
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
		return InventoryPicker.itemTypes(player, excludedItems(state)).stream().filter(item -> !hand.is(item)).toList();
	}

	/** Os itens sem nome no idioma, que só valem nesta sessão, e os bloqueados na configuração. */
	private Set<String> excludedItems(PlayerState state) {
		Set<String> excluded = new HashSet<>(state.unavailableItems);
		excluded.addAll(config.blockedItems);
		return excluded;
	}

	/**
	 * Bloqueia um item para todos os jogadores e salva a configuração. Se ele for a palavra do desafio em
	 * andamento, o desafio é cancelado sem castigo, porque o item pode ser justamente o que trava a tela.
	 */
	public boolean blockItem(ServerPlayer player, String id) {
		if (config.blockedItems.contains(id)) {
			return false;
		}
		config.blockedItems.add(id);
		config.save();
		PlayerState state = states.get(player.getUUID());
		if (state != null && state.active != null && state.active.type == SubjectType.ITEM && state.active.subject.equals(id)) {
			cancel(player);
		}
		return true;
	}

	public boolean unblockItem(String id) {
		if (!config.blockedItems.remove(id)) {
			return false;
		}
		config.save();
		return true;
	}

	private boolean startMob(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		Optional<String> mob = MobPunishment.draw(mobTiers(),
				id -> !state.unavailableMobs.contains(id) && MobPunishment.canSpawn(id, player.level()), random);
		if (mob.isEmpty()) {
			return false;
		}
		Minigame minigame = requested != null ? requested : (inCombat(state) || random.nextBoolean() ? Minigame.SPEAK : Minigame.QUIZ);
		begin(player, state, minigame, SubjectType.MOB, mob.get(), requested);
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
		int seconds = switch (minigame) {
			case SPEAK -> config.speakSeconds;
			case QUIZ -> config.quizSeconds;
			case HOLD -> config.holdSeconds;
		};
		int duration = seconds * TICKS_PER_SECOND;
		PlayerProgress progress = progress(player);
		Challenge challenge = new Challenge(nextId++, minigame, type, subject, requested, now + duration);
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
			case NOT_SPEAKABLE -> {
				if (challenge.minigame == Minigame.SPEAK && challenge.requested == null) {
					state.active = null;
					if (inCombat(state)) {
						retry(state, challenge.type, NOTHING_TO_ASK_RETRY_TICKS);
					} else {
						begin(player, state, Minigame.QUIZ, challenge.type, challenge.subject, null);
					}
				}
			}
			case UNAVAILABLE -> {
				(challenge.type == SubjectType.ITEM ? state.unavailableItems : state.unavailableMobs).add(challenge.subject);
				state.active = null;
				start(player, state, challenge.type, challenge.requested);
			}
			case BUSY -> {
				state.active = null;
				retry(state, challenge.type, RETRY_DELAY_TICKS);
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
		PlayerTitle titleBefore = PlayerTitle.of(progress(player).totalCorrect());
		PlayerProgress progress = progress(player).afterWin();
		setProgress(player, progress);
		PlayerTitle title = PlayerTitle.of(progress.totalCorrect());
		if (title != titleBefore && config.showTitles) {
			announceTitle(player, title);
		}
		player.giveExperiencePoints(Consequences.winExperience(config.winExperience, progress.streak(), challenge.minigame));
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
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, debuff.amplifier()));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, debuff.amplifier()));
		}
	}

	private void startPunishment(ServerPlayer player, PlayerState state, Item item, int lossStreak) {
		ItemStack stack = InventoryPicker.firstStack(player, item);
		if (stack.isEmpty()) {
			player.sendSystemMessage(Component.translatable("langcraft.message.escaped", item.getName()));
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
		RouletteRun run = new RouletteRun(nextId++, kind, item, stack.copy(), slices, List.of(), now + autoSpinTicks);
		state.roulette = run;
		LangCraft.platform.send(player, new RouletteStartPayload(run.id, kind, InventoryPicker.idOf(item), stack.getCount(),
				slices.stream().map(Roulette.Slice::amount).toList(), slices.stream().map(Roulette.Slice::weight).toList(), autoSpinTicks, List.of()));
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
		RouletteRun run = new RouletteRun(nextId++, kind, icon, new ItemStack(icon), slices, effects, now + autoSpinTicks);
		state.roulette = run;
		LangCraft.platform.send(player, new RouletteStartPayload(run.id, kind, InventoryPicker.idOf(icon),
				kind == RouletteStartPayload.Kind.JACKPOT ? progress.streak() : progress.lossStreak(),
				slices.stream().map(Roulette.Slice::amount).toList(), slices.stream().map(Roulette.Slice::weight).toList(), autoSpinTicks,
				effects.stream().map(RouletteEffect::id).toList()));
		return true;
	}

	public void onSpin(ServerPlayer player, RouletteSpinPayload payload) {
		PlayerState state = state(player);
		if (state.roulette != null && state.roulette.id == payload.id()) {
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
			player.sendSystemMessage(Component.translatable("langcraft.message.lost", removed, item.getName()));
		}
	}

	private void giveItems(ServerPlayer player, ItemStack template, int amount) {
		player.getInventory().placeItemBackInInventory(template.copyWithCount(amount));
		player.sendSystemMessage(Component.translatable("langcraft.message.gained", amount, template.getItem().getName()));
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

	/** Sair durante um desafio cancela, mas sair durante a roleta não escapa do resultado. */
	public void onPlayerLeft(ServerPlayer player) {
		PlayerState state = states.remove(player.getUUID());
		if (state == null || state.roulette == null) {
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

		PlayerState(ChallengeTimers timers) {
			this.timers = timers;
		}
	}

	private static final class Challenge {
		final int id;
		final Minigame minigame;
		final SubjectType type;
		final String subject;
		@Nullable final Minigame requested;
		final int deadline;
		@Nullable HoldRound<Item> holdRound;

		Challenge(int id, Minigame minigame, SubjectType type, String subject, @Nullable Minigame requested, int deadline) {
			this.id = id;
			this.minigame = minigame;
			this.type = type;
			this.subject = subject;
			this.requested = requested;
			this.deadline = deadline;
		}

		Item item() {
			return BuiltInRegistries.ITEM.getValue(Identifier.parse(subject));
		}
	}

	private static final class RouletteRun {
		final int id;
		final RouletteStartPayload.Kind kind;
		final Item item;
		final ItemStack stack;
		final List<Roulette.Slice> slices;
		final List<RouletteEffect> effects;
		final int autoSpinAt;
		int sliceIndex = -1;
		int applyAt;

		RouletteRun(int id, RouletteStartPayload.Kind kind, Item item, ItemStack stack, List<Roulette.Slice> slices, List<RouletteEffect> effects, int autoSpinAt) {
			this.id = id;
			this.kind = kind;
			this.item = item;
			this.stack = stack;
			this.slices = slices;
			this.effects = effects;
			this.autoSpinAt = autoSpinAt;
		}

		boolean spun() {
			return sliceIndex >= 0;
		}
	}
}
