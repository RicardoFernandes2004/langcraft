package dev.langcraft.net;

import dev.langcraft.LangCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Todos os pacotes do mod, que passam por um único canal: primeiro o tipo, depois os dados. */
public final class Payloads {
	public static final ResourceLocation CHANNEL = LangCraft.id("main");

	private record Entry<T extends CustomPacketPayload>(StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> serverHandler) {
	}

	private static final Map<ResourceLocation, Entry<?>> ENTRIES = new HashMap<>();
	private static final Map<ResourceLocation, Consumer<?>> CLIENT_HANDLERS = new HashMap<>();

	private Payloads() {
	}

	private static <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
		ENTRIES.put(type.id(), new Entry<>(codec, null));
	}

	private static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
		ENTRIES.put(type.id(), new Entry<>(codec, handler));
	}

	public static void register() {
		toClient(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		toClient(ChallengeEndedPayload.TYPE, ChallengeEndedPayload.CODEC);
		toClient(RouletteStartPayload.TYPE, RouletteStartPayload.CODEC);
		toClient(RouletteResultPayload.TYPE, RouletteResultPayload.CODEC);
		toClient(VoiceSetupPayload.TYPE, VoiceSetupPayload.CODEC);
		toClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC);
		toClient(ScorePayload.TYPE, ScorePayload.CODEC);
		toClient(PreferencesPayload.TYPE, PreferencesPayload.CODEC);
		toServer(ChallengeResultPayload.TYPE, ChallengeResultPayload.CODEC, (player, payload) -> LangCraft.challenges.onResult(player, payload));
		toServer(RouletteSpinPayload.TYPE, RouletteSpinPayload.CODEC, (player, payload) -> LangCraft.challenges.onSpin(player, payload));
		toServer(LanguageChoicePayload.TYPE, LanguageChoicePayload.CODEC, (player, payload) -> LangCraft.challenges.onLanguageChoice(player, payload));
		toServer(ChallengeTogglePayload.TYPE, ChallengeTogglePayload.CODEC, (player, payload) -> LangCraft.challenges.onToggle(player, payload));
		toServer(ClientReadyPayload.TYPE, ClientReadyPayload.CODEC, (player, payload) -> LangCraft.challenges.onClientReady(player));
	}

	public static <T extends CustomPacketPayload> void setClientHandler(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
		CLIENT_HANDLERS.put(type.id(), handler);
	}

	@SuppressWarnings("unchecked")
	public static void encode(CustomPacketPayload payload, FriendlyByteBuf buf) {
		ResourceLocation id = payload.type().id();
		buf.writeResourceLocation(id);
		((StreamCodec<FriendlyByteBuf, CustomPacketPayload>) ENTRIES.get(id).codec()).encode(buf, payload);
	}

	/** Lê o tipo e os dados. O servidor usa o nome do pacote só para achar quem trata dele, então um nome desconhecido é ignorado. */
	public static CustomPacketPayload decode(FriendlyByteBuf buf) {
		Entry<?> entry = ENTRIES.get(buf.readResourceLocation());
		return entry == null ? null : entry.codec().decode(buf);
	}

	@SuppressWarnings("unchecked")
	public static void handleOnServer(ServerPlayer player, CustomPacketPayload payload) {
		if (payload != null) {
			BiConsumer<ServerPlayer, CustomPacketPayload> handler = (BiConsumer<ServerPlayer, CustomPacketPayload>) ENTRIES.get(payload.type().id()).serverHandler();
			if (handler != null) {
				handler.accept(player, payload);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static void handleOnClient(CustomPacketPayload payload) {
		if (payload != null) {
			Consumer<CustomPacketPayload> handler = (Consumer<CustomPacketPayload>) CLIENT_HANDLERS.get(payload.type().id());
			if (handler != null) {
				handler.accept(payload);
			}
		}
	}
}
