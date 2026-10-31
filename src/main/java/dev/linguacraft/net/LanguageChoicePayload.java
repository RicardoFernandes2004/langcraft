package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LanguageChoicePayload(String language) implements CustomPacketPayload {
	public static final Type<LanguageChoicePayload> TYPE = new Type<>(LinguaCraft.id("language_choice"));
	public static final StreamCodec<FriendlyByteBuf, LanguageChoicePayload> CODEC = CustomPacketPayload.codec(LanguageChoicePayload::write, LanguageChoicePayload::read);

	private static LanguageChoicePayload read(FriendlyByteBuf buf) {
		return new LanguageChoicePayload(buf.readUtf());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(language);
	}

	@Override
	public Type<LanguageChoicePayload> type() {
		return TYPE;
	}
}
