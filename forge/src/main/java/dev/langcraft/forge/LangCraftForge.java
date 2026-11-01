package dev.langcraft.forge;

import dev.langcraft.LangCraft;
import dev.langcraft.command.LangCommand;
import dev.langcraft.net.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(LangCraft.MOD_ID)
public final class LangCraftForge {
	public LangCraftForge() {
		LangCraft.init(new ForgePlatform());
		Payloads.register();
		ForgeNetwork.init();
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				LangCraft.challenges.onServerTick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.onPlayerLeft(player);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.cancel(player);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LangCommand.register(event.getDispatcher()));
		if (FMLEnvironment.dist == Dist.CLIENT) {
			LangCraftForgeClient.init();
		}
	}
}
