package dev.linguacraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.data.PlayerProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class LinguaCommand {
	private enum Target {
		ALL,
		ITEMS,
		MOBS
	}

	private LinguaCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lingua")
				.then(Commands.literal("desafio")
						.executes(context -> challenge(context.getSource(), SubjectType.ITEM, null))
						.then(minigame("fala", SubjectType.ITEM, Minigame.SPEAK))
						.then(minigame("quiz", SubjectType.ITEM, Minigame.QUIZ))
						.then(minigame("segurar", SubjectType.ITEM, Minigame.HOLD))
						.then(Commands.literal("item")
								.executes(context -> challenge(context.getSource(), SubjectType.ITEM, null))
								.then(minigame("fala", SubjectType.ITEM, Minigame.SPEAK))
								.then(minigame("quiz", SubjectType.ITEM, Minigame.QUIZ))
								.then(minigame("segurar", SubjectType.ITEM, Minigame.HOLD)))
						.then(Commands.literal("mob")
								.executes(context -> challenge(context.getSource(), SubjectType.MOB, null))
								.then(minigame("fala", SubjectType.MOB, Minigame.SPEAK))
								.then(minigame("quiz", SubjectType.MOB, Minigame.QUIZ))
								.then(Commands.literal("segurar").executes(context -> {
									context.getSource().sendFailure(Component.translatable("linguacraft.command.hold_only_items"));
									return 0;
								}))))
				.then(Commands.literal("idioma")
						.then(language("coreano", PlayerProgress.KOREAN))
						.then(language("japones", PlayerProgress.JAPANESE))
						.then(language("ambos", PlayerProgress.BOTH)))
				.then(toggle("ligar", true))
				.then(toggle("desligar", false)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> minigame(String name, SubjectType type, Minigame minigame) {
		return Commands.literal(name).executes(context -> challenge(context.getSource(), type, minigame));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> language(String name, String language) {
		return Commands.literal(name).executes(context -> {
			ServerPlayer player = context.getSource().getPlayerOrException();
			LinguaCraft.setProgress(player, LinguaCraft.progress(player).withLanguage(language));
			LinguaCraft.challenges.onLanguageChanged(player);
			context.getSource().sendSuccess(() -> Component.translatable("linguacraft.command.language",
					Component.translatable("linguacraft.language." + language)), false);
			return 1;
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean enabled) {
		return Commands.literal(name)
				.executes(context -> setEnabled(context.getSource(), Target.ALL, enabled))
				.then(Commands.literal("itens").executes(context -> setEnabled(context.getSource(), Target.ITEMS, enabled)))
				.then(Commands.literal("mobs").executes(context -> setEnabled(context.getSource(), Target.MOBS, enabled)));
	}

	private static int challenge(CommandSourceStack source, SubjectType type, @Nullable Minigame minigame) throws CommandSyntaxException {
		String error = LinguaCraft.challenges.startManual(source.getPlayerOrException(), type, minigame);
		if (error != null) {
			source.sendFailure(Component.translatable(error));
			return 0;
		}
		return 1;
	}

	private static int setEnabled(CommandSourceStack source, Target target, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerProgress progress = LinguaCraft.progress(player);
		boolean items = target == Target.MOBS ? progress.itemsEnabled() : enabled;
		boolean mobs = target == Target.ITEMS ? progress.mobsEnabled() : enabled;
		LinguaCraft.setProgress(player, progress.withEnabled(items, mobs));
		if (enabled) {
			LinguaCraft.challenges.restartTimers(player);
		}
		source.sendSuccess(() -> Component.translatable("linguacraft.command.status",
				Component.translatable(items ? "linguacraft.command.on" : "linguacraft.command.off"),
				Component.translatable(mobs ? "linguacraft.command.on" : "linguacraft.command.off")), false);
		return 1;
	}
}
