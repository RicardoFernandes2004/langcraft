package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** O jogador terminou de escolher o idioma e passou da tela de download. Os desafios podem começar. */
public record ClientReadyPayload() implements CustomPacketPayload {
	public static final Type<ClientReadyPayload> TYPE = new Type<>(LinguaCraft.id("client_ready"));
	public static final StreamCodec<FriendlyByteBuf, ClientReadyPayload> CODEC = CustomPacketPayload.codec((payload, buf) -> {
	}, buf -> new ClientReadyPayload());

	@Override
	public Type<ClientReadyPayload> type() {
		return TYPE;
	}
}
