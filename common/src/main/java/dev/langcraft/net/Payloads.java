package dev.langcraft.net;

import dev.langcraft.LangCraft;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Todos os pacotes do mod. Cada loader registra da sua maneira. */
public final class Payloads {
	public interface Registrar {
		<T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec);

		<T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler);
	}

	private Payloads() {
	}

	public static void register(Registrar registrar) {
		registrar.toClient(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		registrar.toClient(ChallengeEndedPayload.TYPE, ChallengeEndedPayload.CODEC);
		registrar.toClient(RouletteStartPayload.TYPE, RouletteStartPayload.CODEC);
		registrar.toClient(RouletteResultPayload.TYPE, RouletteResultPayload.CODEC);
		registrar.toClient(VoiceSetupPayload.TYPE, VoiceSetupPayload.CODEC);
		registrar.toClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC);
		registrar.toClient(ScorePayload.TYPE, ScorePayload.CODEC);
		registrar.toClient(PreferencesPayload.TYPE, PreferencesPayload.CODEC);
		registrar.toClient(TreasurePayload.TYPE, TreasurePayload.CODEC);
		registrar.toServer(ChallengeResultPayload.TYPE, ChallengeResultPayload.CODEC, (player, payload) -> LangCraft.challenges.onResult(player, payload));
		registrar.toServer(RouletteSpinPayload.TYPE, RouletteSpinPayload.CODEC, (player, payload) -> LangCraft.challenges.onSpin(player, payload));
		registrar.toServer(LanguageChoicePayload.TYPE, LanguageChoicePayload.CODEC, (player, payload) -> LangCraft.challenges.onLanguageChoice(player, payload));
		registrar.toServer(ChallengeTogglePayload.TYPE, ChallengeTogglePayload.CODEC, (player, payload) -> LangCraft.challenges.onToggle(player, payload));
		registrar.toServer(ClientReadyPayload.TYPE, ClientReadyPayload.CODEC, (player, payload) -> LangCraft.challenges.onClientReady(player));
	}
}
