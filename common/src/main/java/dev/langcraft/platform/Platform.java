package dev.langcraft.platform;

import dev.langcraft.data.PlayerProgress;
import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** O que muda de um loader para outro. */
public interface Platform {
	Path configDir();

	/** O progresso salvo do jogador, que sobrevive à morte. */
	PlayerProgress progress(ServerPlayer player);

	void setProgress(ServerPlayer player, PlayerProgress progress);

	/** Falso quando o jogador não tem o mod, para que ele possa entrar no servidor mesmo assim. */
	boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

	void send(ServerPlayer player, CustomPacketPayload payload);

	boolean canSendToServer(CustomPacketPayload.Type<?> type);

	void sendToServer(CustomPacketPayload payload);
}
