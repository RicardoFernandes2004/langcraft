package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LanguageChoicePayload(String language) implements CustomPacketPayload {
	public static final Type<LanguageChoicePayload> TYPE = new Type<>(LangCraft.id("language_choice"));
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
