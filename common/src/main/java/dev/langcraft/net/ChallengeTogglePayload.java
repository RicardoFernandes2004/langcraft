package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** O jogador ligou ou desligou um desafio para si, na tela de configuração. */
public record ChallengeTogglePayload(String challenge, boolean enabled) implements CustomPacketPayload {
	public static final Type<ChallengeTogglePayload> TYPE = new Type<>(LangCraft.id("challenge_toggle"));
	public static final StreamCodec<FriendlyByteBuf, ChallengeTogglePayload> CODEC = CustomPacketPayload.codec(ChallengeTogglePayload::write, ChallengeTogglePayload::read);

	private static ChallengeTogglePayload read(FriendlyByteBuf buf) {
		return new ChallengeTogglePayload(buf.readUtf(), buf.readBoolean());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(challenge);
		buf.writeBoolean(enabled);
	}

	@Override
	public Type<ChallengeTogglePayload> type() {
		return TYPE;
	}
}
