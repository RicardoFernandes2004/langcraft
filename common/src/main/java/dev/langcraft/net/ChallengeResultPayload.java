package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;

public record ChallengeResultPayload(int id, Result result) implements CustomPacketPayload {
	public enum Result {
		CORRECT,
		WRONG,
		UNAVAILABLE,
		BUSY,
		NOT_SPEAKABLE
	}

	public static final Type<ChallengeResultPayload> TYPE = new Type<>(LangCraft.id("challenge_result"));
	public static final StreamCodec<FriendlyByteBuf, ChallengeResultPayload> CODEC = CustomPacketPayload.codec(ChallengeResultPayload::write, ChallengeResultPayload::read);

	private static ChallengeResultPayload read(FriendlyByteBuf buf) {
		return new ChallengeResultPayload(buf.readVarInt(), buf.readEnum(Result.class));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeEnum(result);
	}

	@Override
	public Type<ChallengeResultPayload> type() {
		return TYPE;
	}
}
