package dev.linguacraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.linguacraft.LinguaCraft;
import dev.linguacraft.challenge.Minigame;
import dev.linguacraft.challenge.PlayerTitle;
import dev.linguacraft.challenge.SubjectType;
import dev.linguacraft.data.PlayerProgress;
import dev.linguacraft.data.StudyLanguage;
import dev.linguacraft.net.OpenScreenPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class LinguaCommand {
	private enum Target {
		ALL,
		ITEMS,
		MOBS
	}

	private record HelpEntry(String command, String key, boolean ready, String usage) {
		static HelpEntry opens(String command, String key) {
			return new HelpEntry(command, key, true, command);
		}

		static HelpEntry needsMore(String command, String key, String usage) {
			return new HelpEntry(command, key, false, usage);
		}
	}

	private record HelpGroup(String key, List<HelpEntry> entries) {
	}

	private static final List<HelpGroup> HELP = List.of(
			new HelpGroup("study", List.of(
					HelpEntry.opens("/lingua estudar", "study"),
					HelpEntry.opens("/lingua palavras", "words"),
					HelpEntry.opens("/lingua alfabeto", "alphabet"),
					HelpEntry.opens("/lingua titulos", "titles"))),
			new HelpGroup("challenges", List.of(
					HelpEntry.needsMore("/lingua desafio", "challenge", "/lingua desafio [item|mob] [fala|quiz|segurar]"),
					HelpEntry.needsMore("/lingua ligar", "on", "/lingua ligar [itens|mobs]"),
					HelpEntry.needsMore("/lingua desligar", "off", "/lingua desligar [itens|mobs]"))),
			new HelpGroup("settings", List.of(
					HelpEntry.opens("/lingua idioma", "language"),
					HelpEntry.opens("/lingua tema", "theme"),
					HelpEntry.opens("/lingua config", "config"))));

	private LinguaCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lingua")
				.executes(context -> help(context.getSource()))
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
						.executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.LANGUAGE))
						.then(language("coreano", StudyLanguage.KOREAN))
						.then(language("japones", StudyLanguage.JAPANESE))
						.then(language("ingles", StudyLanguage.ENGLISH))
						.then(language("alemao", StudyLanguage.GERMAN))
						.then(language("espanhol", StudyLanguage.SPANISH))
						.then(languageChoice("ambos", PlayerProgress.BOTH, "linguacraft.language.both"))
						.then(languageChoice("todos", PlayerProgress.choiceOf(StudyLanguage.codes()), "linguacraft.language.all")))
				.then(Commands.literal("tema").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.THEME)))
				.then(Commands.literal("config").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.CONFIG)))
				.then(Commands.literal("estudar").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.STUDY)))
				.then(Commands.literal("palavras").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.WORDS)))
				.then(Commands.literal("alfabeto").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.ALPHABET)))
				.then(Commands.literal("titulos").executes(context -> titles(context.getSource())))
				.then(toggle("ligar", true))
				.then(toggle("desligar", false)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> minigame(String name, SubjectType type, Minigame minigame) {
		return Commands.literal(name).executes(context -> challenge(context.getSource(), type, minigame));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> language(String name, StudyLanguage language) {
		return languageChoice(name, language.code(), "linguacraft.language." + language.code());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> languageChoice(String name, String choice, String displayKey) {
		return Commands.literal(name).executes(context -> {
			ServerPlayer player = context.getSource().getPlayerOrException();
			LinguaCraft.setProgress(player, LinguaCraft.progress(player).withLanguage(choice));
			LinguaCraft.challenges.onLanguageChanged(player);
			context.getSource().sendSuccess(() -> Component.translatable("linguacraft.command.language", Component.translatable(displayKey)), false);
			return 1;
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean enabled) {
		return Commands.literal(name)
				.executes(context -> setEnabled(context.getSource(), Target.ALL, enabled))
				.then(Commands.literal("itens").executes(context -> setEnabled(context.getSource(), Target.ITEMS, enabled)))
				.then(Commands.literal("mobs").executes(context -> setEnabled(context.getSource(), Target.MOBS, enabled)));
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("✦ LinguaCraft ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (HelpGroup group : HELP) {
			source.sendSuccess(() -> Component.translatable("linguacraft.help.group." + group.key()).withStyle(ChatFormatting.AQUA), false);
			for (HelpEntry entry : group.entries()) {
				source.sendSuccess(() -> helpLine(entry), false);
			}
		}
		source.sendSuccess(() -> Component.translatable("linguacraft.help.tip").withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	/** Lista os títulos, marca o atual e diz quanto falta para o próximo. */
	private static int titles(CommandSourceStack source) throws CommandSyntaxException {
		int correct = LinguaCraft.progress(source.getPlayerOrException()).totalCorrect();
		PlayerTitle current = PlayerTitle.of(correct);
		source.sendSuccess(() -> Component.translatable("linguacraft.titles.heading", correct).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (PlayerTitle title : PlayerTitle.values()) {
			String marker = title == current ? " » " : "   ";
			source.sendSuccess(() -> Component.literal(marker).withStyle(ChatFormatting.GOLD)
					.append(title.tag())
					.append(Component.translatable("linguacraft.titles.required", title.requiredCorrect()).withStyle(ChatFormatting.GRAY)), false);
		}
		PlayerTitle next = current.next();
		if (next != null) {
			source.sendSuccess(() -> Component.translatable("linguacraft.titles.next", next.requiredCorrect() - correct, next.tag()).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/** Clicar abre o que não pede mais nada, e nos outros preenche o chat para o jogador completar. */
	private static Component helpLine(HelpEntry entry) {
		ClickEvent click = entry.ready() ? new ClickEvent.RunCommand(entry.command()) : new ClickEvent.SuggestCommand(entry.command() + " ");
		Component hover = entry.ready() ? Component.translatable("linguacraft.help.click_run") : Component.translatable("linguacraft.help.usage", entry.usage());
		Component command = Component.literal(entry.command()).withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
				.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(hover)));
		return Component.literal(" ").append(command)
				.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("linguacraft.help." + entry.key()).withStyle(ChatFormatting.GRAY));
	}

	private static int openScreen(CommandSourceStack source, OpenScreenPayload.Target target) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!ServerPlayNetworking.canSend(player, OpenScreenPayload.TYPE)) {
			source.sendFailure(Component.translatable("linguacraft.command.no_mod"));
			return 0;
		}
		ServerPlayNetworking.send(player, new OpenScreenPayload(target));
		return 1;
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
