package dev.langcraft.net;

import dev.langcraft.LangCraft;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Os desafios que o jogador desligou, para a tela de configuração mostrar cada um como está no servidor. */
public record PreferencesPayload(List<String> disabledChallenges) implements CustomPacketPayload {
	public static final Type<PreferencesPayload> TYPE = new Type<>(LangCraft.id("preferences"));
	public static final StreamCodec<FriendlyByteBuf, PreferencesPayload> CODEC = CustomPacketPayload.codec(PreferencesPayload::write, PreferencesPayload::read);

	private static PreferencesPayload read(FriendlyByteBuf buf) {
		return new PreferencesPayload(buf.readList(FriendlyByteBuf::readUtf));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeCollection(disabledChallenges, FriendlyByteBuf::writeUtf);
	}

	@Override
	public Type<PreferencesPayload> type() {
		return TYPE;
	}
}
