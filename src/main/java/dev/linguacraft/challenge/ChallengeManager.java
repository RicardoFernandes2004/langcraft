package dev.linguacraft.challenge;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.config.LinguaConfig;
import dev.linguacraft.data.PlayerProgress;
import dev.linguacraft.net.ChallengeEndedPayload;
import dev.linguacraft.net.ChallengeResultPayload;
import dev.linguacraft.net.ChallengeStartPayload;
import dev.linguacraft.net.ClientReadyPayload;
import dev.linguacraft.net.LanguageChoicePayload;
import dev.linguacraft.net.RouletteResultPayload;
import dev.linguacraft.net.RouletteSpinPayload;
import dev.linguacraft.net.RouletteStartPayload;
import dev.linguacraft.net.VoiceSetupPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Controla, no servidor, o momento dos desafios, os prazos, as roletas e as consequências. */
public final class ChallengeManager {
	private static final int TICKS_PER_SECOND = 20;
	private static final int RETRY_DELAY_TICKS = 30 * TICKS_PER_SECOND;
	/** Duração da animação da roleta. Os itens só mudam quando ela termina. */
	private static final int SPIN_TICKS = 80;

	private final LinguaConfig config;
	private final Map<UUID, PlayerState> states = new HashMap<>();
	private final Random random = new Random();
	private int nextId = 1;
	private int now;

	public ChallengeManager(LinguaConfig config) {
		this.config = config;
	}

	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				cancel(player);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ChallengeResultPayload.TYPE, (payload, context) -> onResult(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(RouletteSpinPayload.TYPE, (payload, context) -> onSpin(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(LanguageChoicePayload.TYPE, (payload, context) -> onLanguageChoice(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE, (payload, context) -> onClientReady(context.player()));
	}

	/** Inicia um desafio pedido por comando. Retorna a chave da mensagem de erro, se não puder começar. */
	public @Nullable String startManual(ServerPlayer player, SubjectType type, @Nullable Minigame minigame) {
		if (!ServerPlayNetworking.canSend(player, ChallengeStartPayload.TYPE)) {
			return "linguacraft.command.no_mod";
		}
		PlayerState state = state(player);
		if (!state.ready) {
			return "linguacraft.command.not_ready";
		}
		if (state.active != null || state.roulette != null) {
			return "linguacraft.command.busy";
		}
		if (minigame == Minigame.HOLD && holdableItems(player, state).isEmpty() && !InventoryPicker.itemTypes(player, state.unavailableItems).isEmpty()) {
			return "linguacraft.command.hold_needs_other";
		}
		if (!start(player, state, type, minigame)) {
			return type == SubjectType.ITEM ? "linguacraft.command.no_items" : "linguacraft.command.no_mobs";
		}
		return null;
	}

	/** Volta a contar o intervalo do zero, para que um desafio não apareça assim que o jogador religar. */
	public void restartTimers(ServerPlayer player) {
		PlayerState state = state(player);
		state.nextItemTick = now + interval(config.itemIntervalMinSeconds, config.itemIntervalMaxSeconds);
		state.nextMobTick = now + interval(config.mobIntervalMinSeconds, config.mobIntervalMaxSeconds);
	}

	/** Depois de mudar de idioma, os desafios esperam o cliente garantir o modelo de voz. */
	public void onLanguageChanged(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, VoiceSetupPayload.TYPE)) {
			state(player).ready = false;
			ServerPlayNetworking.send(player, new VoiceSetupPayload(false, progress(player).language()));
		}
	}

	public boolean isReady(ServerPlayer player) {
		PlayerState state = states.get(player.getUUID());
		return state != null && state.ready;
	}

	private void onLanguageChoice(ServerPlayer player, LanguageChoicePayload payload) {
		if (PlayerProgress.isValidLanguage(payload.language())) {
			setProgress(player, progress(player).withLanguage(payload.language()));
		}
	}

	private void onClientReady(ServerPlayer player) {
		PlayerState state = state(player);
		state.ready = true;
		if (!state.timersStarted) {
			state.timersStarted = true;
			restartTimers(player);
		}
	}

	private void tick(MinecraftServer server) {
		now = server.getTickCount();
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (ServerPlayNetworking.canSend(player, ChallengeStartPayload.TYPE)) {
				PlayerState state = state(player);
				if (!state.setupSent) {
					state.setupSent = true;
					PlayerProgress progress = progress(player);
					ServerPlayNetworking.send(player, new VoiceSetupPayload(!progress.languageChosen(), progress.language()));
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
		if (!state.ready || now < state.cooldownUntil) {
			return;
		}
		PlayerProgress progress = progress(player);
		if (progress.itemsEnabled() && now >= state.nextItemTick) {
			state.nextItemTick = now + interval(config.itemIntervalMinSeconds, config.itemIntervalMaxSeconds);
			if (start(player, state, SubjectType.ITEM, null)) {
				return;
			}
		}
		if (progress.mobsEnabled() && now >= state.nextMobTick) {
			state.nextMobTick = now + interval(config.mobIntervalMinSeconds, config.mobIntervalMaxSeconds);
			start(player, state, SubjectType.MOB, null);
		}
	}

	private void tickChallenge(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		if (challenge.minigame == Minigame.HOLD && challenge.holdTracker.tick(player.getMainHandItem().is(challenge.item()))) {
			win(player, state);
		} else if (now >= challenge.deadline) {
			expire(player, state);
		}
	}

	private boolean start(ServerPlayer player, PlayerState state, SubjectType type, @Nullable Minigame requested) {
		return type == SubjectType.ITEM ? startItem(player, state, requested) : startMob(player, state, requested);
	}

	private boolean startItem(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		List<Item> candidates = InventoryPicker.itemTypes(player, state.unavailableItems);
		if (candidates.isEmpty()) {
			return false;
		}
		Minigame minigame = requested != null ? requested : Minigame.values()[random.nextInt(Minigame.values().length)];
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
		return InventoryPicker.itemTypes(player, state.unavailableItems).stream().filter(item -> !hand.is(item)).toList();
	}

	private boolean startMob(ServerPlayer player, PlayerState state, @Nullable Minigame requested) {
		Optional<String> mob = MobPunishment.draw(config.mobTiers,
				id -> !state.unavailableMobs.contains(id) && MobPunishment.canSpawn(id, player.level()), random);
		if (mob.isEmpty()) {
			return false;
		}
		Minigame minigame = requested != null ? requested : (random.nextBoolean() ? Minigame.SPEAK : Minigame.QUIZ);
		begin(player, state, minigame, SubjectType.MOB, mob.get(), requested);
		return true;
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
		state.active = challenge;
		ServerPlayNetworking.send(player, new ChallengeStartPayload(challenge.id, minigame, type, subject, resolveLanguage(progress),
				duration, progress.streak(), streakGoal()));
	}

	private String resolveLanguage(PlayerProgress progress) {
		if (!progress.language().equals(PlayerProgress.BOTH)) {
			return progress.language();
		}
		return random.nextBoolean() ? PlayerProgress.KOREAN : PlayerProgress.JAPANESE;
	}

	private int streakGoal() {
		return config.rewardRouletteEnabled ? config.rewardStreak : 0;
	}

	private void onResult(ServerPlayer player, ChallengeResultPayload payload) {
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
			case UNAVAILABLE -> {
				(challenge.type == SubjectType.ITEM ? state.unavailableItems : state.unavailableMobs).add(challenge.subject);
				state.active = null;
				start(player, state, challenge.type, challenge.requested);
			}
			case BUSY -> {
				state.active = null;
				if (challenge.type == SubjectType.ITEM) {
					state.nextItemTick = now + RETRY_DELAY_TICKS;
				} else {
					state.nextMobTick = now + RETRY_DELAY_TICKS;
				}
			}
		}
	}

	private void win(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		PlayerProgress progress = progress(player).afterWin();
		setProgress(player, progress);
		ServerPlayNetworking.send(player, new ChallengeEndedPayload(challenge.id, ChallengeEndedPayload.Outcome.WON, progress.streak()));
		finish(state);
		if (progress.rewardDue(streakGoal())) {
			setProgress(player, progress.afterReward());
			startReward(player, state);
		}
	}

	private void expire(ServerPlayer player, PlayerState state) {
		Challenge challenge = state.active;
		setProgress(player, progress(player).afterLoss());
		if (challenge.type == SubjectType.MOB) {
			MobPunishment.spawnAbove(player, challenge.subject);
		}
		ServerPlayNetworking.send(player, new ChallengeEndedPayload(challenge.id, ChallengeEndedPayload.Outcome.LOST, 0));
		finish(state);
		if (challenge.type == SubjectType.ITEM) {
			startPunishment(player, state, challenge.item());
		}
	}

	private void startPunishment(ServerPlayer player, PlayerState state, Item item) {
		ItemStack stack = InventoryPicker.firstStack(player, item);
		if (stack.isEmpty()) {
			player.sendSystemMessage(Component.translatable("linguacraft.message.escaped", item.getName()));
			return;
		}
		List<Roulette.Slice> slices = Roulette.buildSlices(stack.getCount(), config.rouletteSlices);
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
			player.sendSystemMessage(Component.translatable("linguacraft.message.levels", config.rewardFallbackLevels));
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
		RouletteRun run = new RouletteRun(nextId++, kind, item, stack.copy(), slices, now + autoSpinTicks);
		state.roulette = run;
		ServerPlayNetworking.send(player, new RouletteStartPayload(run.id, kind, InventoryPicker.idOf(item), stack.getCount(),
				slices.stream().map(Roulette.Slice::amount).toList(), slices.stream().map(Roulette.Slice::weight).toList(), autoSpinTicks));
	}

	private void onSpin(ServerPlayer player, RouletteSpinPayload payload) {
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
		ServerPlayNetworking.send(player, new RouletteResultPayload(run.id, run.sliceIndex, SPIN_TICKS));
	}

	private void tickRoulette(ServerPlayer player, PlayerState state) {
		RouletteRun run = state.roulette;
		if (!run.spun()) {
			if (now >= run.autoSpinAt) {
				spin(player, run);
			}
		} else if (now >= run.applyAt) {
			applyRoulette(player, run);
			state.roulette = null;
			state.cooldownUntil = now + RETRY_DELAY_TICKS;
		}
	}

	private void applyRoulette(ServerPlayer player, RouletteRun run) {
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
			player.sendSystemMessage(Component.translatable("linguacraft.message.lost", removed, item.getName()));
		}
	}

	private void giveItems(ServerPlayer player, ItemStack template, int amount) {
		player.getInventory().placeItemBackInInventory(template.copyWithCount(amount));
		player.sendSystemMessage(Component.translatable("linguacraft.message.gained", amount, template.getItem().getName()));
	}

	/** Morrer ou virar espectador encerra tudo sem punição, inclusive uma roleta em andamento. */
	private void cancel(ServerPlayer player) {
		PlayerState state = states.get(player.getUUID());
		if (state == null) {
			return;
		}
		if (state.active != null) {
			ServerPlayNetworking.send(player, new ChallengeEndedPayload(state.active.id, ChallengeEndedPayload.Outcome.CANCELLED, progress(player).streak()));
			finish(state);
		}
		if (state.roulette != null) {
			ServerPlayNetworking.send(player, new ChallengeEndedPayload(state.roulette.id, ChallengeEndedPayload.Outcome.CANCELLED, progress(player).streak()));
			state.roulette = null;
			state.cooldownUntil = now + RETRY_DELAY_TICKS;
		}
	}

	/** Sair durante um desafio cancela, mas sair durante a roleta não escapa do resultado. */
	private void onDisconnect(ServerPlayer player) {
		PlayerState state = states.remove(player.getUUID());
		if (state == null || state.roulette == null) {
			return;
		}
		RouletteRun run = state.roulette;
		if (!run.spun()) {
			run.sliceIndex = Roulette.draw(run.slices, random);
		}
		applyRoulette(player, run);
	}

	private void finish(PlayerState state) {
		state.active = null;
		state.cooldownUntil = now + RETRY_DELAY_TICKS;
	}

	private PlayerState state(ServerPlayer player) {
		return states.computeIfAbsent(player.getUUID(), uuid -> new PlayerState(
				now + interval(config.itemIntervalMinSeconds, config.itemIntervalMaxSeconds),
				now + interval(config.mobIntervalMinSeconds, config.mobIntervalMaxSeconds)));
	}

	private int interval(int minSeconds, int maxSeconds) {
		int min = Math.max(1, minSeconds);
		int max = Math.max(min, maxSeconds);
		return (min + random.nextInt(max - min + 1)) * TICKS_PER_SECOND;
	}

	private PlayerProgress progress(ServerPlayer player) {
		return LinguaCraft.progress(player);
	}

	private void setProgress(ServerPlayer player, PlayerProgress progress) {
		LinguaCraft.setProgress(player, progress);
	}

	private static final class PlayerState {
		int nextItemTick;
		int nextMobTick;
		int cooldownUntil;
		boolean setupSent;
		boolean ready;
		boolean timersStarted;
		@Nullable Challenge active;
		@Nullable RouletteRun roulette;
		final Set<String> unavailableItems = new HashSet<>();
		final Set<String> unavailableMobs = new HashSet<>();

		PlayerState(int nextItemTick, int nextMobTick) {
			this.nextItemTick = nextItemTick;
			this.nextMobTick = nextMobTick;
		}
	}

	private static final class Challenge {
		final int id;
		final Minigame minigame;
		final SubjectType type;
		final String subject;
		@Nullable final Minigame requested;
		final int deadline;
		final HoldTracker holdTracker = new HoldTracker();

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
		final int autoSpinAt;
		int sliceIndex = -1;
		int applyAt;

		RouletteRun(int id, RouletteStartPayload.Kind kind, Item item, ItemStack stack, List<Roulette.Slice> slices, int autoSpinAt) {
			this.id = id;
			this.kind = kind;
			this.item = item;
			this.stack = stack;
			this.slices = slices;
			this.autoSpinAt = autoSpinAt;
		}

		boolean spun() {
			return sliceIndex >= 0;
		}
	}
}
