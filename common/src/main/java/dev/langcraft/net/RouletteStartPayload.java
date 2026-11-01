package dev.langcraft.net;

import dev.langcraft.LangCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Nas roletas de itens, {@code amounts} tem a quantidade de cada fatia e {@code effects} fica vazio.
 * Nas de efeito, {@code effects} diz o que cada fatia faz e {@code stackCount} é a sequência que abriu a roleta.
 */
public record RouletteStartPayload(int id, Kind kind, String item, int stackCount, List<Integer> amounts, List<Integer> weights,
		int autoSpinTicks, List<String> effects) implements CustomPacketPayload {
	public enum Kind {
		PUNISHMENT,
		REWARD,
		DAILY,
		DISASTER,
		JACKPOT;

		public boolean isBad() {
			return this == PUNISHMENT || this == DAILY || this == DISASTER;
		}
	}

	public static final Type<RouletteStartPayload> TYPE = new Type<>(LangCraft.id("roulette_start"));
	public static final StreamCodec<FriendlyByteBuf, RouletteStartPayload> CODEC = CustomPacketPayload.codec(RouletteStartPayload::write, RouletteStartPayload::read);

	private static RouletteStartPayload read(FriendlyByteBuf buf) {
		int id = buf.readVarInt();
		Kind kind = buf.readEnum(Kind.class);
		String item = buf.readUtf();
		int stackCount = buf.readVarInt();
		int size = buf.readVarInt();
		List<Integer> amounts = new ArrayList<>(size);
		List<Integer> weights = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			amounts.add(buf.readVarInt());
			weights.add(buf.readVarInt());
		}
		int autoSpinTicks = buf.readVarInt();
		int effectCount = buf.readVarInt();
		List<String> effects = new ArrayList<>(effectCount);
		for (int i = 0; i < effectCount; i++) {
			effects.add(buf.readUtf());
		}
		return new RouletteStartPayload(id, kind, item, stackCount, amounts, weights, autoSpinTicks, effects);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeEnum(kind);
		buf.writeUtf(item);
		buf.writeVarInt(stackCount);
		buf.writeVarInt(amounts.size());
		for (int i = 0; i < amounts.size(); i++) {
			buf.writeVarInt(amounts.get(i));
			buf.writeVarInt(weights.get(i));
		}
		buf.writeVarInt(autoSpinTicks);
		buf.writeVarInt(effects.size());
		effects.forEach(buf::writeUtf);
	}

	@Override
	public Type<RouletteStartPayload> type() {
		return TYPE;
	}
}
