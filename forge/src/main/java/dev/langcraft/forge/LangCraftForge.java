package dev.langcraft.forge;

import dev.langcraft.LangCraft;
import dev.langcraft.command.LangCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(LangCraft.MOD_ID)
public final class LangCraftForge {
	public LangCraftForge(FMLJavaModLoadingContext context) {
		ForgePlatform platform = new ForgePlatform();
		LangCraft.init(platform);
		ForgeNetwork.init();
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> LangCraft.challenges.onServerTick(event.server()));
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.onPlayerLeft(player);
			}
		});
		LivingDeathEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.cancel(player);
			}
		});
		// Os dados do Forge ficam na entidade, e morrer cria uma entidade nova.
		PlayerEvent.Clone.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player && event.getOriginal() instanceof ServerPlayer original) {
				platform.setProgress(player, platform.progress(original));
			}
		});
		RegisterCommandsEvent.BUS.addListener(event -> LangCommand.register(event.getDispatcher()));
		if (FMLEnvironment.dist == Dist.CLIENT) {
			LangCraftForgeClient.init(context);
		}
	}
}
