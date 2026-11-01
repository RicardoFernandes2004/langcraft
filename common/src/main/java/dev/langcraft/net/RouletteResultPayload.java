package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;

public record RouletteResultPayload(int id, int sliceIndex, int spinTicks) implements CustomPacketPayload {
	public static final Type<RouletteResultPayload> TYPE = new Type<>(LangCraft.id("roulette_result"));
	public static final StreamCodec<FriendlyByteBuf, RouletteResultPayload> CODEC = CustomPacketPayload.codec(RouletteResultPayload::write, RouletteResultPayload::read);

	private static RouletteResultPayload read(FriendlyByteBuf buf) {
		return new RouletteResultPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeVarInt(sliceIndex);
		buf.writeVarInt(spinTicks);
	}

	@Override
	public Type<RouletteResultPayload> type() {
		return TYPE;
	}
}
