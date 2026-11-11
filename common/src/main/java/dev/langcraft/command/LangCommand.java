package dev.langcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.langcraft.LangCraft;
import dev.langcraft.challenge.InventoryPicker;
import dev.langcraft.challenge.Minigame;
import dev.langcraft.challenge.PlayerTitle;
import dev.langcraft.challenge.SubjectType;
import dev.langcraft.data.PlayerProgress;
import dev.langcraft.data.StudyLanguage;
import dev.langcraft.net.OpenScreenPayload;
import dev.langcraft.net.RouletteStartPayload;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

	private record HelpGroup(String key, Predicate<CommandSourceStack> visible, List<HelpEntry> entries) {
	}

	private static final Predicate<CommandSourceStack> EVERYONE = source -> true;

	private static final List<HelpGroup> HELP = List.of(
			new HelpGroup("study", EVERYONE, List.of(
					HelpEntry.opens("/lang study", "study"),
					HelpEntry.opens("/lang words", "words"),
					HelpEntry.opens("/lang alphabet", "alphabet"),
					HelpEntry.opens("/lang titles", "titles"))),
			new HelpGroup("admin", LangCommand::isAdmin, List.of(
					HelpEntry.needsMore("/lang admin challenge", "challenge", "/lang admin challenge [item|mob|biome] [speak|quiz|hold]"),
					HelpEntry.needsMore("/lang admin roulette", "roulette", "/lang admin roulette [daily|disaster|jackpot]"),
					HelpEntry.needsMore("/lang admin on", "on", "/lang admin on [items|mobs]"),
					HelpEntry.needsMore("/lang admin off", "off", "/lang admin off [items|mobs]"),
					HelpEntry.needsMore("/lang admin treasure", "treasure", "/lang admin treasure [give|check|list|every|chance|pity|window|bymod|reload]"),
					HelpEntry.opens("/lang admin block", "admin_block"),
					HelpEntry.needsMore("/lang admin unblock", "admin_unblock", "/lang admin unblock [item]"))),
			new HelpGroup("blocking", EVERYONE, List.of(
					HelpEntry.opens("/lang block", "block"),
					HelpEntry.needsMore("/lang unblock", "unblock", "/lang unblock [item]"),
					HelpEntry.opens("/lang block list", "block_list"))),
			new HelpGroup("settings", EVERYONE, List.of(
					HelpEntry.opens("/lang language", "language"),
					HelpEntry.opens("/lang theme", "theme"),
					HelpEntry.opens("/lang config", "config"),
					HelpEntry.opens("/lang missing", "missing"))));

	private LangCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lang")
				.executes(context -> help(context.getSource()))
				.then(Commands.literal("block")
						.executes(context -> block(context.getSource(), false))
						.then(Commands.literal("list").executes(context -> listBlocked(context.getSource()))))
				.then(unblockCommand(false))
				// Tudo o que vale para o servidor inteiro fica atrás do admin, para o jogador comum não mexer sem querer.
				.then(adminCommand())
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
				.then(Commands.literal("missing").executes(context -> openScreen(context.getSource(), OpenScreenPayload.Target.EXPORT_MISSING))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
		return Commands.literal("admin")
				.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("challenge")
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
								})))
						.then(Commands.literal("biome").executes(context -> challenge(context.getSource(), SubjectType.BIOME, Minigame.SPEAK))))
				.then(Commands.literal("roulette")
						.then(roulette("daily", RouletteStartPayload.Kind.DAILY))
						.then(roulette("disaster", RouletteStartPayload.Kind.DISASTER))
						.then(roulette("jackpot", RouletteStartPayload.Kind.JACKPOT)))
				.then(toggle("on", true))
				.then(toggle("off", false))
				.then(Commands.literal("block").executes(context -> block(context.getSource(), true)))
				.then(unblockCommand(true))
				.then(TreasureCommand.build());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> minigame(String name, SubjectType type, Minigame minigame) {
		return Commands.literal(name).executes(context -> challenge(context.getSource(), type, minigame));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> roulette(String name, RouletteStartPayload.Kind kind) {
		return Commands.literal(name).executes(context -> {
			String error = LangCraft.challenges.startRoulette(context.getSource().getPlayerOrException(), kind);
			if (error != null) {
				context.getSource().sendFailure(Component.translatable(error));
				return 0;
			}
			return 1;
		});
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
				.executes(context -> setEnabled(context.getSource(), Target.ALL, enabled))
				.then(Commands.literal("items").executes(context -> setEnabled(context.getSource(), Target.ITEMS, enabled)))
				.then(Commands.literal("mobs").executes(context -> setEnabled(context.getSource(), Target.MOBS, enabled)));
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("✦ LangCraft ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (HelpGroup group : HELP) {
			// A lista esconde o que a pessoa não pode usar.
			if (!group.visible().test(source)) {
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

	private static boolean isAdmin(CommandSourceStack source) {
		return source.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> unblockCommand(boolean forAll) {
		return Commands.literal("unblock")
				.executes(context -> unblockHand(context.getSource(), forAll))
				.then(Commands.argument("item", StringArgumentType.greedyString())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(forAll ? LangCraft.config.blockedItems : personalBlocked(context.getSource()), builder))
						.executes(context -> unblock(context.getSource(), StringArgumentType.getString(context, "item").trim(), forAll)));
	}

	private static List<String> personalBlocked(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? List.of() : List.copyOf(LangCraft.progress(player).preferences().blockedItems());
	}

	private static int block(CommandSourceStack source, boolean forAll) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack hand = player.getMainHandItem();
		if (hand.isEmpty()) {
			source.sendFailure(Component.translatable("langcraft.command.block_empty_hand"));
			return 0;
		}
		String id = InventoryPicker.idOf(hand.getItem());
		boolean added = forAll ? LangCraft.challenges.blockItemForAll(player, id) : LangCraft.challenges.blockItem(player, id);
		if (!added) {
			source.sendFailure(Component.translatable("langcraft.command.already_blocked", itemName(id)));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable(forAll ? "langcraft.command.blocked_all" : "langcraft.command.blocked", itemName(id)), forAll);
		return 1;
	}

	private static int unblockHand(CommandSourceStack source, boolean forAll) throws CommandSyntaxException {
		ItemStack hand = source.getPlayerOrException().getMainHandItem();
		if (hand.isEmpty()) {
			source.sendFailure(Component.translatable("langcraft.command.unblock_empty_hand"));
			return 0;
		}
		return unblock(source, InventoryPicker.idOf(hand.getItem()), forAll);
	}

	private static int unblock(CommandSourceStack source, String id, boolean forAll) throws CommandSyntaxException {
		boolean removed = forAll ? LangCraft.challenges.unblockItemForAll(id) : LangCraft.challenges.unblockItem(source.getPlayerOrException(), id);
		if (!removed) {
			source.sendFailure(Component.translatable("langcraft.command.not_blocked", itemName(id)));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("langcraft.command.unblocked", itemName(id)), forAll);
		return 1;
	}

	/** Os bloqueados pelo próprio jogador e, à parte, os bloqueados para todos pelo administrador. */
	private static int listBlocked(CommandSourceStack source) throws CommandSyntaxException {
		List<String> personal = personalBlocked(source);
		List<String> forAll = LangCraft.config.blockedItems;
		if (personal.isEmpty() && forAll.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("langcraft.command.blocked_none"), false);
			return 1;
		}
		listSection(source, "langcraft.command.blocked_list", personal, "/lang unblock ");
		listSection(source, "langcraft.command.blocked_list_all", forAll, isAdmin(source) ? "/lang admin unblock " : null);
		return 1;
	}

	/** Clicar em um item preenche o comando para liberar, se a pessoa puder usar esse comando. */
	private static void listSection(CommandSourceStack source, String headingKey, List<String> ids, @Nullable String unblockCommand) {
		if (ids.isEmpty()) {
			return;
		}
		source.sendSuccess(() -> Component.translatable(headingKey).withStyle(ChatFormatting.GOLD), false);
		for (String id : ids) {
			Style style = unblockCommand == null ? Style.EMPTY : Style.EMPTY
					.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, unblockCommand + id))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("langcraft.command.click_unblock")));
			Component line = Component.literal(" ").append(itemName(id)).withStyle(style);
			source.sendSuccess(() -> line, false);
		}
	}

	/** Nome e id, porque o nome de um item com defeito pode estar errado ou faltando. Um item de mod removido mostra só o id. */
	private static Component itemName(String id) {
		ResourceLocation parsed = ResourceLocation.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		Component idText = Component.literal(id).withStyle(ChatFormatting.GRAY);
		return item == null ? idText : Component.translatable(item.getDescriptionId()).append(Component.literal(" (").append(idText).append(")").withStyle(ChatFormatting.GRAY));
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
		ClickEvent click = entry.ready() ? new ClickEvent(ClickEvent.Action.RUN_COMMAND, entry.command()) : new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, entry.command() + " ");
		Component hover = entry.ready() ? Component.translatable("langcraft.help.click_run") : Component.translatable("langcraft.help.usage", entry.usage());
		Component command = Component.literal(entry.command()).withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
				.withClickEvent(click).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
		return Component.literal(" ").append(command)
				.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("langcraft.help." + entry.key()).withStyle(ChatFormatting.GRAY));
	}

	private static int openScreen(CommandSourceStack source, OpenScreenPayload.Target target) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!LangCraft.platform.canSend(player, OpenScreenPayload.TYPE)) {
			source.sendFailure(Component.translatable("langcraft.command.no_mod"));
			return 0;
		}
		LangCraft.platform.send(player, new OpenScreenPayload(target));
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
