package dev.langcraft.forge;

import net.minecraft.client.Minecraft;

/** Fora de {@link ForgePlatform}, porque um servidor dedicado não tem estas classes. */
final class ForgeClientNetwork {
	private ForgeClientNetwork() {
	}

	static boolean canSend() {
		return Minecraft.getInstance().getConnection() != null
				&& ForgeNetwork.channel().isRemotePresent(Minecraft.getInstance().getConnection().getConnection());
	}
}
