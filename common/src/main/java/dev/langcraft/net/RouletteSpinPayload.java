package dev.langcraft.net;

import dev.langcraft.LangCraft;
import net.minecraft.network.FriendlyByteBuf;

public record RouletteSpinPayload(int id) implements CustomPacketPayload {
	public static final Type<RouletteSpinPayload> TYPE = new Type<>(LangCraft.id("roulette_spin"));
	public static final StreamCodec<FriendlyByteBuf, RouletteSpinPayload> CODEC = CustomPacketPayload.codec(RouletteSpinPayload::write, RouletteSpinPayload::read);

	private static RouletteSpinPayload read(FriendlyByteBuf buf) {
		return new RouletteSpinPayload(buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(id);
	}

	@Override
	public Type<RouletteSpinPayload> type() {
		return TYPE;
	}
}
