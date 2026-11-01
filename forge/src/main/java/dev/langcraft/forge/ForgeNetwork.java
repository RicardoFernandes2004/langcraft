package dev.langcraft.forge;

import dev.langcraft.LangCraft;
import dev.langcraft.net.Payloads;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadProtocol;

/** Um canal só, opcional, para que quem não tem o mod consiga entrar no servidor. */
final class ForgeNetwork {
	private static final int PROTOCOL_VERSION = 1;
	/** O Forge pede o destino de cada pacote já na criação do canal. No servidor este mapa fica vazio e nunca é consultado. */
	private static final Map<CustomPacketPayload.Type<?>, Consumer<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();
	private static Channel<CustomPacketPayload> channel;

	private ForgeNetwork() {
	}

	static Channel<CustomPacketPayload> channel() {
		return channel;
	}

	static <T extends CustomPacketPayload> void setClientHandler(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
		CLIENT_HANDLERS.put(type, handler);
	}

	@SuppressWarnings("unchecked")
	private static <T extends CustomPacketPayload> void handleOnClient(CustomPacketPayload.Type<T> type, T payload) {
		Consumer<T> handler = (Consumer<T>) CLIENT_HANDLERS.get(type);
		if (handler != null) {
			handler.accept(payload);
		}
	}

	static void init() {
		PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play = ChannelBuilder.named(LangCraft.id("main"))
				.networkProtocolVersion(PROTOCOL_VERSION)
				.optional()
				.payloadChannel()
				.play();
		Payloads.register(new Payloads.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
				play.clientbound().addMain(type, codec.cast(), (payload, context) -> handleOnClient(type, payload));
			}

			@Override
			public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
				play.serverbound().addMain(type, codec.cast(), (payload, context) -> handler.accept(context.getSender(), payload));
			}
		});
		channel = play.clientbound().build();
	}
}
