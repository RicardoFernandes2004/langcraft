package dev.langcraft.net;

import dev.langcraft.LangCraft;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Um tesouro aberto. O item já está no inventário quando a tela abre: ela só revela. {@code chances} tem o
 * percentual de cada faixa, de comum a mítico, já com a sorte acumulada, e zero na faixa sem itens.
 */
public record TreasurePayload(String item, int count, String tier, List<Integer> chances, int luckPercent, String language) implements CustomPacketPayload {
	public static final Type<TreasurePayload> TYPE = new Type<>(LangCraft.id("treasure"));
	public static final StreamCodec<FriendlyByteBuf, TreasurePayload> CODEC = CustomPacketPayload.codec(TreasurePayload::write, TreasurePayload::read);

	private static TreasurePayload read(FriendlyByteBuf buf) {
		return new TreasurePayload(buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readList(FriendlyByteBuf::readVarInt), buf.readVarInt(), buf.readUtf());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(item);
		buf.writeVarInt(count);
		buf.writeUtf(tier);
		buf.writeCollection(chances, FriendlyByteBuf::writeVarInt);
		buf.writeVarInt(luckPercent);
		buf.writeUtf(language);
	}

	@Override
	public Type<TreasurePayload> type() {
		return TYPE;
	}
}
