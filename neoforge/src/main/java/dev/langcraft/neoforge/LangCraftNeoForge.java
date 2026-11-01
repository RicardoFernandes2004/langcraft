package dev.langcraft.neoforge;

import dev.langcraft.LangCraft;
import dev.langcraft.command.LangCommand;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.net.Payloads;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(LangCraft.MOD_ID)
public final class LangCraftNeoForge {
	private static final String NETWORK_VERSION = "1";
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LangCraft.MOD_ID);
	static final Supplier<AttachmentType<PlayerProgress>> PROGRESS = ATTACHMENTS.register("progress", () ->
			AttachmentType.builder(LangCraft::newProgress).serialize(PlayerProgress.CODEC.fieldOf("progress")).copyOnDeath().build());

	public LangCraftNeoForge(IEventBus modBus) {
		LangCraft.init(new NeoForgePlatform());
		ATTACHMENTS.register(modBus);
		modBus.addListener(LangCraftNeoForge::registerPayloads);
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> LangCraft.challenges.onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.onPlayerLeft(player);
			}
		});
		NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				LangCraft.challenges.cancel(player);
			}
		});
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LangCommand.register(event.getDispatcher()));
	}

	/** Opcional, para que quem não tem o mod consiga entrar no servidor, como no Fabric. */
	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
		Payloads.register(new Payloads.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
				registrar.playToClient(type, codec);
			}

			@Override
			public <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
				registrar.playToServer(type, codec, (payload, context) -> handler.accept((ServerPlayer) context.player(), payload));
			}
		});
	}
}
