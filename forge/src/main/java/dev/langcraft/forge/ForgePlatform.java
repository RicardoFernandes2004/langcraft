package dev.langcraft.forge;

import dev.langcraft.LangCraft;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.platform.Platform;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

final class ForgePlatform implements Platform {
	private static final String KEY = LangCraft.MOD_ID;

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public PlayerProgress progress(ServerPlayer player) {
		return player.getPersistentData().getCompound(KEY)
				.flatMap(tag -> PlayerProgress.CODEC.parse(NbtOps.INSTANCE, tag).result())
				.orElseGet(LangCraft::newProgress);
	}

	@Override
	public void setProgress(ServerPlayer player, PlayerProgress progress) {
		CompoundTag data = player.getPersistentData();
		data.put(KEY, PlayerProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow());
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ForgeNetwork.channel().isRemotePresent(player.connection.getConnection());
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ForgeNetwork.channel().send(payload, PacketDistributor.PLAYER.with(player));
	}

	@Override
	public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
		return ForgeClientNetwork.canSend();
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ForgeNetwork.channel().send(payload, PacketDistributor.SERVER.noArg());
	}
}
