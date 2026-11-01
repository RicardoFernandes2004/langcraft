package dev.langcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.langcraft.LangCraft;
import dev.langcraft.challenge.Minigame;
import dev.langcraft.challenge.PlayerTitle;
import dev.langcraft.challenge.SubjectType;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.data.StudyLanguage;
import dev.langcraft.net.OpenScreenPayload;
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

public final class LangCommand {
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

	private record HelpGroup(String key, boolean adminOnly, List<HelpEntry> entries) {
	}

	private static final List<HelpGroup> HELP = List.of(
			new HelpGroup("study", false, List.of(
					HelpEntry.opens("/lang study", "study"),
					HelpEntry.opens("/lang words", "words"),
					HelpEntry.opens("/lang alphabet", "alphabet"),
					HelpEntry.opens("/lang titles", "titles"))),
			new HelpGroup("challenges", true, List.of(
					HelpEntry.needsMore("/lang challenge", "challenge", "/lang challenge [item|mob] [speak|quiz|hold]"),
					HelpEntry.needsMore("/lang on", "on", "/lang on [items|mobs]"),
					HelpEntry.needsMore("/lang off", "off", "/lang off [items|mobs]"))),
			new HelpGroup("settings", false, List.of(
					HelpEntry.opens("/lang language", "language"),
					HelpEntry.opens("/lang theme", "theme"),
					HelpEntry.opens("/lang config", "config"),
					HelpEntry.opens("/lang missing", "missing"))));

	private LangCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lang")
				.executes(context -> help(context.getSource()))
				.then(Commands.literal("challenge")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> challenge(context.getSource(), SubjectType.ITEM, null))
						.then(minigame("speak", SubjectType.ITEM, Minigame.SPEAK))
						.then(minigame("quiz", SubjectType.ITEM, Minigame.QUIZ))
						.then(minigame("hold", SubjectType.ITEM, Minigame.HOLD))
						.then(Commands.literal("item")
								.executes(context -> challenge(context.getSource(), SubjectType.ITEM, null))
								.then(minigame("speak", SubjectType.ITEM, Minigame.SPEAK))
								.then(minigame("quiz", SubjectType.ITEM, Minigame.QUIZ))
								.then(minigame("hold", SubjectType.ITEM, Minigame.HOLD)))
						.then(Commands.literal("mob")
								.executes(context -> challenge(context.getSource(), SubjectType.MOB, null))
								.then(minigame("speak", SubjectType.MOB, Minigame.SPEAK))
								.then(minigame("quiz", SubjectType.MOB, Minigame.QUIZ))
								.then(Commands.literal("hold").executes(context -> {
									context.getSource().sendFailure(Component.translatable("langcraft.command.hold_only_items"));
									return 0;
								}))))
				.then(Commands.literal("language")
						.executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.LANGUAGE))
						.then(language("korean", StudyLanguage.KOREAN))
						.then(language("japanese", StudyLanguage.JAPANESE))
						.then(language("english", StudyLanguage.ENGLISH))
						.then(language("german", StudyLanguage.GERMAN))
						.then(language("spanish", StudyLanguage.SPANISH))
						.then(languageChoice("both", PlayerProgress.BOTH, "langcraft.language.both"))
						.then(languageChoice("all", PlayerProgress.choiceOf(StudyLanguage.codes()), "langcraft.language.all")))
				.then(Commands.literal("theme").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.THEME)))
				.then(Commands.literal("config").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.CONFIG)))
				.then(Commands.literal("study").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.STUDY)))
				.then(Commands.literal("words").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.WORDS)))
				.then(Commands.literal("alphabet").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.ALPHABET)))
				.then(Commands.literal("titles").executes(context -> titles(context.getSource())))
				.then(Commands.literal("missing").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.EXPORT_MISSING)))
				.then(toggle("on", true))
				.then(toggle("off", false)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> minigame(String name, SubjectType type, Minigame minigame) {
		return Commands.literal(name).executes(context -> challenge(context.getSource(), type, minigame));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> language(String name, StudyLanguage language) {
		return languageChoice(name, language.code(), "langcraft.language." + language.code());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> languageChoice(String name, String choice, String displayKey) {
		return Commands.literal(name).executes(context -> {
			ServerPlayer player = context.getSource().getPlayerOrException();
			LangCraft.setProgress(player, LangCraft.progress(player).withLanguage(choice));
			LangCraft.challenges.onLanguageChanged(player);
			context.getSource().sendSuccess(() -> Component.translatable("langcraft.command.language", Component.translatable(displayKey)), false);
			return 1;
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean enabled) {
		return Commands.literal(name)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> setEnabled(context.getSource(), Target.ALL, enabled))
				.then(Commands.literal("items").executes(context -> setEnabled(context.getSource(), Target.ITEMS, enabled)))
				.then(Commands.literal("mobs").executes(context -> setEnabled(context.getSource(), Target.MOBS, enabled)));
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("✦ LangCraft ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (HelpGroup group : HELP) {
			// A lista esconde o que a pessoa não pode usar.
			if (group.adminOnly() && !Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
				continue;
			}
			source.sendSuccess(() -> Component.translatable("langcraft.help.group." + group.key()).withStyle(ChatFormatting.AQUA), false);
			for (HelpEntry entry : group.entries()) {
				source.sendSuccess(() -> helpLine(entry), false);
			}
		}
		source.sendSuccess(() -> Component.translatable("langcraft.help.tip").withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	/** Lista os títulos, marca o atual e diz quanto falta para o próximo. */
	private static int titles(CommandSourceStack source) throws CommandSyntaxException {
		int correct = LangCraft.progress(source.getPlayerOrException()).totalCorrect();
		PlayerTitle current = PlayerTitle.of(correct);
		source.sendSuccess(() -> Component.translatable("langcraft.titles.heading", correct).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (PlayerTitle title : PlayerTitle.values()) {
			String marker = title == current ? " » " : "   ";
			source.sendSuccess(() -> Component.literal(marker).withStyle(ChatFormatting.GOLD)
					.append(title.tag())
					.append(Component.translatable("langcraft.titles.required", title.requiredCorrect()).withStyle(ChatFormatting.GRAY)), false);
		}
		PlayerTitle next = current.next();
		if (next != null) {
			source.sendSuccess(() -> Component.translatable("langcraft.titles.next", next.requiredCorrect() - correct, next.tag()).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/** Clicar abre o que não pede mais nada, e nos outros preenche o chat para o jogador completar. */
	private static Component helpLine(HelpEntry entry) {
		ClickEvent click = entry.ready() ? new ClickEvent.RunCommand(entry.command()) : new ClickEvent.SuggestCommand(entry.command() + " ");
		Component hover = entry.ready() ? Component.translatable("langcraft.help.click_run") : Component.translatable("langcraft.help.usage", entry.usage());
		Component command = Component.literal(entry.command()).withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
				.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(hover)));
		return Component.literal(" ").append(command)
				.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("langcraft.help." + entry.key()).withStyle(ChatFormatting.GRAY));
	}

	private static int openScreen(CommandSourceStack source, OpenScreenPayload.Target target) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!ServerPlayNetworking.canSend(player, OpenScreenPayload.TYPE)) {
			source.sendFailure(Component.translatable("langcraft.command.no_mod"));
			return 0;
		}
		ServerPlayNetworking.send(player, new OpenScreenPayload(target));
		return 1;
	}

	private static int challenge(CommandSourceStack source, SubjectType type, @Nullable Minigame minigame) throws CommandSyntaxException {
		String error = LangCraft.challenges.startManual(source.getPlayerOrException(), type, minigame);
		if (error != null) {
			source.sendFailure(Component.translatable(error));
			return 0;
		}
		return 1;
	}

	private static int setEnabled(CommandSourceStack source, Target target, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerProgress progress = LangCraft.progress(player);
		boolean items = target == Target.MOBS ? progress.itemsEnabled() : enabled;
		boolean mobs = target == Target.ITEMS ? progress.mobsEnabled() : enabled;
		LangCraft.setProgress(player, progress.withEnabled(items, mobs));
		if (enabled) {
			LangCraft.challenges.restartTimers(player);
		}
		source.sendSuccess(() -> Component.translatable("langcraft.command.status",
				Component.translatable(items ? "langcraft.command.on" : "langcraft.command.off"),
				Component.translatable(mobs ? "langcraft.command.on" : "langcraft.command.off")), false);
		return 1;
	}
}
