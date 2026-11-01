package dev.langcraft.net;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Um pacote do mod. A 1.20.1 ainda não tem isto no jogo, e cada loader só carrega os bytes pelo canal do mod. */
public interface CustomPacketPayload {
	record Type<T extends CustomPacketPayload>(ResourceLocation id) {
	}

	Type<? extends CustomPacketPayload> type();

	static <T> StreamCodec<FriendlyByteBuf, T> codec(BiConsumer<T, FriendlyByteBuf> writer, Function<FriendlyByteBuf, T> reader) {
		return new StreamCodec<>() {
			@Override
			public void encode(FriendlyByteBuf buf, T value) {
				writer.accept(value, buf);
			}

			@Override
			public T decode(FriendlyByteBuf buf) {
				return reader.apply(buf);
			}
		};
	}
}
