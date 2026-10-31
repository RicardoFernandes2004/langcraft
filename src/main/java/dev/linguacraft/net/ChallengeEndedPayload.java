package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChallengeEndedPayload(int id, Outcome outcome, int streak) implements CustomPacketPayload {
	public enum Outcome {
		WON,
		LOST,
		CANCELLED
	}

	public static final Type<ChallengeEndedPayload> TYPE = new Type<>(LinguaCraft.id("challenge_ended"));
	public static final StreamCodec<FriendlyByteBuf, ChallengeEndedPayload> CODEC = CustomPacketPayload.codec(ChallengeEndedPayload::write, ChallengeEndedPayload::read);

	private static ChallengeEndedPayload read(FriendlyByteBuf buf) {
		return new ChallengeEndedPayload(buf.readVarInt(), buf.readEnum(Outcome.class), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeEnum(outcome);
		buf.writeVarInt(streak);
	}

	@Override
	public Type<ChallengeEndedPayload> type() {
		return TYPE;
	}
}
