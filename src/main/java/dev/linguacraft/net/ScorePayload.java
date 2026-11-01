package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** O placar que fica sempre na tela. {@code streakGoal} é 0 com a roleta invertida desligada. */
public record ScorePayload(int streak, int streakGoal, int bestStreak, int totalCorrect, int totalWrong) implements CustomPacketPayload {
	public static final Type<ScorePayload> TYPE = new Type<>(LinguaCraft.id("score"));
	public static final StreamCodec<FriendlyByteBuf, ScorePayload> CODEC = CustomPacketPayload.codec(ScorePayload::write, ScorePayload::read);

	private static ScorePayload read(FriendlyByteBuf buf) {
		return new ScorePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(streak);
		buf.writeVarInt(streakGoal);
		buf.writeVarInt(bestStreak);
		buf.writeVarInt(totalCorrect);
		buf.writeVarInt(totalWrong);
	}

	@Override
	public Type<ScorePayload> type() {
		return TYPE;
	}
}
