package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.SubjectType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChallengeStartPayload(int id, Minigame minigame, SubjectType subjectType, String subject, String language,
		int durationTicks, int streak, int streakGoal) implements CustomPacketPayload {
	public static final Type<ChallengeStartPayload> TYPE = new Type<>(LinguaCraft.id("challenge_start"));
	public static final StreamCodec<FriendlyByteBuf, ChallengeStartPayload> CODEC = CustomPacketPayload.codec(ChallengeStartPayload::write, ChallengeStartPayload::read);

	private static ChallengeStartPayload read(FriendlyByteBuf buf) {
		return new ChallengeStartPayload(buf.readVarInt(), buf.readEnum(Minigame.class), buf.readEnum(SubjectType.class),
				buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeEnum(minigame);
		buf.writeEnum(subjectType);
		buf.writeUtf(subject);
		buf.writeUtf(language);
		buf.writeVarInt(durationTicks);
		buf.writeVarInt(streak);
		buf.writeVarInt(streakGoal);
	}

	@Override
	public Type<ChallengeStartPayload> type() {
		return TYPE;
	}
}
