package dev.langcraft.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.langcraft.LangCraft;
import dev.langcraft.challenge.InventoryPicker;
import dev.langcraft.treasure.TreasureIndex;
import dev.langcraft.treasure.TreasureService;
import dev.langcraft.treasure.TreasureTier;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** {@code /lang admin treasure}: ajustes do tesouro e as ferramentas para conferir a classificação. */
final class TreasureCommand {
	private static final int LIST_LIMIT = 40;

	private TreasureCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("treasure")
				.executes(context -> status(context.getSource()))
				.then(Commands.literal("give").executes(context -> {
					LangCraft.challenges.grantTreasure(context.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("check").executes(context -> check(context.getSource())))
				.then(Commands.literal("list").then(Commands.argument("tier", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(TreasureTier.values()).map(TreasureTier::id), builder))
						.executes(context -> list(context.getSource(), StringArgumentType.getString(context, "tier")))))
				.then(number("every", 0, 1000, value -> LangCraft.config.treasureEvery = value))
				.then(number("pity", 0, 200, value -> LangCraft.config.treasurePity = value))
				.then(number("window", 0, 20, value -> LangCraft.config.treasureModWindow = value))
				.then(Commands.literal("bymod").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
					LangCraft.config.treasureByMod = BoolArgumentType.getBool(context, "enabled");
					return saved(context.getSource());
				})))
				.then(Commands.literal("chance").then(Commands.argument("tier", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(TreasureTier.values()).map(TreasureTier::id), builder))
						.then(Commands.argument("weight", IntegerArgumentType.integer(0, 1000)).executes(context -> {
							TreasureTier tier = TreasureTier.byId(StringArgumentType.getString(context, "tier"));
							if (tier == null) {
								context.getSource().sendFailure(Component.translatable("langcraft.treasure.command.unknown_tier"));
								return 0;
							}
							LangCraft.config.treasureChances.put(tier.id(), IntegerArgumentType.getInteger(context, "weight"));
							return saved(context.getSource());
						}))))
				.then(Commands.literal("reload").executes(context -> {
					LangCraft.challenges.treasures().reload(context.getSource().getServer());
					context.getSource().sendSuccess(() -> Component.translatable("langcraft.treasure.command.reloading"), true);
					return 1;
				}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> number(String name, int min, int max, IntConsumer setter) {
		return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(min, max)).executes(context -> {
			setter.accept(IntegerArgumentType.getInteger(context, "value"));
			return saved(context.getSource());
		}));
	}

	private static int saved(CommandSourceStack source) {
		LangCraft.config.save();
		return status(source);
	}

	private static int status(CommandSourceStack source) {
		TreasureService service = LangCraft.challenges.treasures();
		TreasureIndex index = service.ready();
		Component state = index != null ? Component.translatable("langcraft.treasure.command.ready", index.size())
				: Component.translatable(service.failed() ? "langcraft.treasure.command.failed" : "langcraft.treasure.command.building");
		source.sendSuccess(() -> Component.translatable("langcraft.treasure.command.status", LangCraft.config.treasureEvery, LangCraft.config.treasurePity,
				LangCraft.config.treasureModWindow, Component.translatable(LangCraft.config.treasureByMod ? "langcraft.command.on" : "langcraft.command.off"), state)
				.withStyle(ChatFormatting.GOLD), false);
		MutableComponent chances = Component.empty();
		for (TreasureTier tier : TreasureTier.values()) {
			chances.append(tierName(tier)).append(Component.literal(" " + LangCraft.config.treasureChances.getOrDefault(tier.id(), 0) + "  "));
		}
		source.sendSuccess(() -> chances, false);
		return 1;
	}

	private static int check(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack hand = player.getMainHandItem();
		TreasureIndex index = LangCraft.challenges.treasures().ready();
		if (hand.isEmpty() || index == null) {
			source.sendFailure(Component.translatable(hand.isEmpty() ? "langcraft.command.block_empty_hand" : "langcraft.treasure.command.building"));
			return 0;
		}
		TreasureIndex.Entry entry = index.entry(InventoryPicker.idOf(hand.getItem()));
		if (entry == null) {
			source.sendSuccess(() -> Component.translatable("langcraft.treasure.command.not_eligible", hand.getHoverName()), false);
			return 1;
		}
		source.sendSuccess(() -> Component.translatable("langcraft.treasure.command.check", hand.getHoverName(), tierName(entry.tier()),
				String.format(java.util.Locale.ROOT, "%.2f", entry.score())), false);
		source.sendSuccess(() -> Component.literal(" " + String.join(", ", entry.reasons())).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int list(CommandSourceStack source, String tierId) {
		TreasureTier tier = TreasureTier.byId(tierId);
		TreasureIndex index = LangCraft.challenges.treasures().ready();
		if (tier == null || index == null) {
			source.sendFailure(Component.translatable(tier == null ? "langcraft.treasure.command.unknown_tier" : "langcraft.treasure.command.building"));
			return 0;
		}
		List<String> items = index.items(tier);
		source.sendSuccess(() -> Component.translatable("langcraft.treasure.command.list", tierName(tier), items.size()), false);
		String shown = String.join(", ", items.stream().sorted().limit(LIST_LIMIT).toList()) + (items.size() > LIST_LIMIT ? ", ..." : "");
		source.sendSuccess(() -> Component.literal(" " + shown).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static MutableComponent tierName(TreasureTier tier) {
		return Component.translatable("langcraft.treasure.tier." + tier.id()).withStyle(style -> style.withColor(tier.color() & 0xFFFFFF));
	}
}
