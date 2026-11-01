package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pede ao cliente que prepare o idioma: pergunta qual é, se preciso, e garante o modelo de voz. */
public record VoiceSetupPayload(boolean chooseLanguage, String language) implements CustomPacketPayload {
	public static final Type<VoiceSetupPayload> TYPE = new Type<>(LangCraft.id("voice_setup"));
	public static final StreamCodec<FriendlyByteBuf, VoiceSetupPayload> CODEC = CustomPacketPayload.codec(VoiceSetupPayload::write, VoiceSetupPayload::read);

	private static VoiceSetupPayload read(FriendlyByteBuf buf) {
		return new VoiceSetupPayload(buf.readBoolean(), buf.readUtf());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(chooseLanguage);
		buf.writeUtf(language);
	}

	@Override
	public Type<VoiceSetupPayload> type() {
		return TYPE;
	}
}
