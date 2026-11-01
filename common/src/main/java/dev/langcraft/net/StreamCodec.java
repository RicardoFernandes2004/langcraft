package dev.langcraft.net;

public interface StreamCodec<B, V> {
	void encode(B buf, V value);

	V decode(B buf);
}
