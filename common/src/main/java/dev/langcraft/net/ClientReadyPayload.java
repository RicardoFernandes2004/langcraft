package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;

/** O jogador terminou de escolher o idioma e passou da tela de download. Os desafios podem começar. */
public record ClientReadyPayload() implements CustomPacketPayload {
	public static final Type<ClientReadyPayload> TYPE = new Type<>(LangCraft.id("client_ready"));
	public static final StreamCodec<FriendlyByteBuf, ClientReadyPayload> CODEC = CustomPacketPayload.codec((payload, buf) -> {
	}, buf -> new ClientReadyPayload());

	@Override
	public Type<ClientReadyPayload> type() {
		return TYPE;
	}
}
