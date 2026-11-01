package dev.langcraft.platform;

import java.nio.file.Path;
import dev.langcraft.net.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** O que muda de um loader para outro. */
public interface Platform {
	Path configDir();

	/** Falso quando o jogador não tem o mod, para que ele possa entrar no servidor mesmo assim. */
	boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

	void send(ServerPlayer player, CustomPacketPayload payload);

	boolean canSendToServer(CustomPacketPayload.Type<?> type);

	void sendToServer(CustomPacketPayload payload);
}
