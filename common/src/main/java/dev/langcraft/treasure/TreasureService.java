package dev.langcraft.treasure;

import dev.langcraft.LangCraft;
import dev.langcraft.config.LangConfig;
import dev.langcraft.data.PlayerProgress;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/** Guarda a classificação do servidor atual e faz o sorteio de cada tesouro. */
public final class TreasureService {
	public record Prize(String item, int count, TreasureTier tier, Map<TreasureTier, Double> weights, double luck) {
	}

	private final LangConfig config;
	private @Nullable MinecraftServer server;
	private @Nullable CompletableFuture<TreasureIndex> index;

	public TreasureService(LangConfig config) {
		this.config = config;
	}

	/** Começa a classificação na primeira vez que um servidor roda. Ela é feita fora da thread do jogo. */
	public void prepare(MinecraftServer current) {
		if (server != current) {
			reload(current);
		}
	}

	/** Refaz a classificação, relendo os ajustes do admin no arquivo. */
	public void reload(MinecraftServer current) {
		server = current;
		List<String> blocked = List.copyOf(config.blockedItems);
		index = CompletableFuture.supplyAsync(() -> TreasureIndex.build(current, TreasureFile.load(), blocked));
		index.exceptionally(error -> {
			LangCraft.LOGGER.error("[tesouro] A classificação dos itens falhou", error);
			return null;
		});
	}

	/** A classificação, ou nada enquanto ela ainda está sendo feita ou se falhou. */
	public @Nullable TreasureIndex ready() {
		return index != null && index.isDone() && !index.isCompletedExceptionally() ? index.join() : null;
	}

	public boolean failed() {
		return index != null && index.isCompletedExceptionally();
	}

	public Map<TreasureTier, Integer> baseWeights() {
		Map<TreasureTier, Integer> weights = new EnumMap<>(TreasureTier.class);
		for (TreasureTier tier : TreasureTier.values()) {
			weights.put(tier, Math.max(0, config.treasureChances.getOrDefault(tier.id(), 0)));
		}
		return weights;
	}

	/** Os pesos que valem agora para o jogador, já com a sorte acumulada. */
	public Map<TreasureTier, Double> weights(TreasureIndex index, PlayerProgress.Treasures state) {
		return TreasureDraw.weights(baseWeights(), index.available(), state.sinceTop(), config.treasurePity);
	}

	/** Nada se a classificação não tiver nenhum item. */
	public @Nullable Prize draw(TreasureIndex index, PlayerProgress.Treasures state, Random random) {
		Map<TreasureTier, Double> weights = weights(index, state);
		if (weights.isEmpty() || weights.values().stream().mapToDouble(Double::doubleValue).sum() <= 0) {
			return null;
		}
		TreasureTier tier = TreasureDraw.drawTier(weights, random);
		String item = TreasureDraw.pickItem(index.items(tier), config.treasureByMod, state.recentMods(), random);
		Item found = TreasureItems.item(item);
		int maxStack = found == null ? 1 : found.getDefaultInstance().getMaxStackSize();
		return new Prize(item, TreasureDraw.count(tier, maxStack, random), tier, weights, TreasureDraw.luck(state.sinceTop(), config.treasurePity));
	}

	public PlayerProgress.Treasures after(PlayerProgress.Treasures state, Prize prize) {
		int sinceTop = prize.tier().isTop() ? 0 : state.sinceTop() + 1;
		return new PlayerProgress.Treasures(sinceTop, TreasureDraw.remember(state.recentMods(), TreasureDraw.modOf(prize.item()), config.treasureModWindow));
	}
}
