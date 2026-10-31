package dev.linguacraft.net;

import dev.linguacraft.LinguaCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RouletteStartPayload(int id, Kind kind, String item, int stackCount, List<Integer> amounts, List<Integer> weights,
		int autoSpinTicks) implements CustomPacketPayload {
	public enum Kind {
		PUNISHMENT,
		REWARD
	}

	public static final Type<RouletteStartPayload> TYPE = new Type<>(LinguaCraft.id("roulette_start"));
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
		return new RouletteStartPayload(id, kind, item, stackCount, amounts, weights, buf.readVarInt());
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
	}

	@Override
	public Type<RouletteStartPayload> type() {
		return TYPE;
	}
}
