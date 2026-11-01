package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pede ao cliente que abra uma tela ou faça algo que só ele consegue, em resposta a um comando. */
public record OpenScreenPayload(Target target) implements CustomPacketPayload {
	public enum Target {
		THEME,
		LANGUAGE,
		CONFIG,
		STUDY,
		WORDS,
		ALPHABET,
		EXPORT_MISSING
	}

	public static final Type<OpenScreenPayload> TYPE = new Type<>(LangCraft.id("open_screen"));
	public static final StreamCodec<FriendlyByteBuf, OpenScreenPayload> CODEC = CustomPacketPayload.codec(OpenScreenPayload::write, OpenScreenPayload::read);

	private static OpenScreenPayload read(FriendlyByteBuf buf) {
		return new OpenScreenPayload(buf.readEnum(Target.class));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeEnum(target);
	}

	@Override
	public Type<OpenScreenPayload> type() {
		return TYPE;
	}
}
