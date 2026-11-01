package dev.langcraft.data;

import com.mojang.serialization.Dynamic;
import dev.langcraft.LangCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** O progresso de todos os jogadores, salvo com o mundo. Ficar fora da entidade faz o progresso sobreviver à morte sozinho. */
public final class ProgressStore extends SavedData {
	private static final String NAME = LangCraft.MOD_ID + "_progress";

	private final Map<UUID, PlayerProgress> players = new HashMap<>();

	private static ProgressStore of(ServerPlayer player) {
		return player.server.overworld().getDataStorage().computeIfAbsent(ProgressStore::load, ProgressStore::new, NAME);
	}

	public static PlayerProgress get(ServerPlayer player) {
		return of(player).players.computeIfAbsent(player.getUUID(), uuid -> LangCraft.newProgress());
	}

	public static void set(ServerPlayer player, PlayerProgress progress) {
		ProgressStore store = of(player);
		store.players.put(player.getUUID(), progress);
		store.setDirty();
	}

	private static ProgressStore load(CompoundTag tag) {
		ProgressStore store = new ProgressStore();
		for (String key : tag.getAllKeys()) {
			PlayerProgress.CODEC.parse(new Dynamic<Tag>(NbtOps.INSTANCE, tag.get(key))).result()
					.ifPresent(progress -> store.players.put(UUID.fromString(key), progress));
		}
		return store;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		players.forEach((uuid, progress) -> PlayerProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).result()
				.ifPresent(encoded -> tag.put(uuid.toString(), encoded)));
		return tag;
	}
}
