package dev.langcraft.forge;

import dev.langcraft.net.CustomPacketPayload;
import dev.langcraft.net.Payloads;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Um canal só, opcional, para que quem não tem o mod consiga entrar no servidor. */
final class ForgeNetwork {
	private static final String VERSION = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Payloads.CHANNEL, () -> VERSION,
			NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

	/** O Forge precisa de uma classe por mensagem, e o tipo verdadeiro do pacote vai dentro dos bytes. */
	private record Envelope(CustomPacketPayload payload) {
	}

	private ForgeNetwork() {
	}

	static void init() {
		CHANNEL.messageBuilder(Envelope.class, 0)
				.encoder((envelope, buf) -> Payloads.encode(envelope.payload(), buf))
				.decoder(buf -> new Envelope(Payloads.decode(buf)))
				.consumerMainThread((envelope, context) -> handle(envelope, context.get()))
				.add();
	}

	private static void handle(Envelope envelope, NetworkEvent.Context context) {
		ServerPlayer sender = context.getSender();
		if (sender != null) {
			Payloads.handleOnServer(sender, envelope.payload());
		} else {
			Payloads.handleOnClient(envelope.payload());
		}
	}

	static boolean isPresent(ServerPlayer player) {
		return isPresent(player.connection.connection);
	}

	static boolean isPresent(Connection connection) {
		return CHANNEL.isRemotePresent(connection);
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Envelope(payload));
	}

	static void sendToServer(CustomPacketPayload payload) {
		CHANNEL.sendToServer(new Envelope(payload));
	}
}
