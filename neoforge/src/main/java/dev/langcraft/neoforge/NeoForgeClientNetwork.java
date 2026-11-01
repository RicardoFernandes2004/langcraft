package dev.langcraft.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Fora de {@link NeoForgePlatform}, porque um servidor dedicado não tem estas classes. */
final class NeoForgeClientNetwork {
	private NeoForgeClientNetwork() {
	}

	static boolean canSend(CustomPacketPayload.Type<?> type) {
		return Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(type);
	}

	static void send(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}
}
