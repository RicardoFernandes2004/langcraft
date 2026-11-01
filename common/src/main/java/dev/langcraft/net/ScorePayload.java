package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;

/** O placar que fica sempre na tela. {@code streakGoal} e {@code lossGoal} são 0 com as roletas de cada um desligadas. */
public record ScorePayload(int streak, int streakGoal, int bestStreak, int totalCorrect, int totalWrong, int lossStreak, int lossGoal, int shields) implements CustomPacketPayload {
	public static final Type<ScorePayload> TYPE = new Type<>(LangCraft.id("score"));
	public static final StreamCodec<FriendlyByteBuf, ScorePayload> CODEC = CustomPacketPayload.codec(ScorePayload::write, ScorePayload::read);

	private static ScorePayload read(FriendlyByteBuf buf) {
		return new ScorePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(streak);
		buf.writeVarInt(streakGoal);
		buf.writeVarInt(bestStreak);
		buf.writeVarInt(totalCorrect);
		buf.writeVarInt(totalWrong);
		buf.writeVarInt(lossStreak);
		buf.writeVarInt(lossGoal);
		buf.writeVarInt(shields);
	}

	@Override
	public Type<ScorePayload> type() {
		return TYPE;
	}
}
