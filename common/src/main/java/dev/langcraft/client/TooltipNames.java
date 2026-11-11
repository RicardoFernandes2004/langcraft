package dev.langcraft.client;

import dev.langcraft.LangCraft;
import dev.langcraft.challenge.SubjectType;
import dev.langcraft.client.lang.Furigana;
import dev.langcraft.client.lang.GameNames;
import dev.langcraft.client.ui.StudyFont;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Coloca no tooltip de cada item o nome dele nos idiomas estudados, como uma legenda. O idioma em que o jogo
 * está não aparece, porque já é o nome de cima.
 */
public final class TooltipNames {
	/** O tooltip é desenhado a cada quadro, e a leitura do japonês passa pelo dicionário. */
	private static final Map<String, Optional<String>> CACHE = new HashMap<>();

	private TooltipNames() {
	}

	public static void append(ItemStack stack, List<Component> lines) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!LangCraft.config.showTooltipNames || minecraft.player == null || stack.isEmpty() || lines.isEmpty()) {
			return;
		}
		String selected = minecraft.getLanguageManager().getSelected();
		String subject = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		int line = 1;
		for (String language : VoiceSetup.currentLanguages()) {
			if (language.equals(selected)) {
				continue;
			}
			Optional<String> name = CACHE.computeIfAbsent(language + "|" + subject, unused -> GameNames.name(language, SubjectType.ITEM, subject)
					.map(word -> LangCraft.config.showFurigana ? Furigana.inline(word, language) : word));
			if (name.isPresent()) {
				lines.add(line++, Component.translatable("langcraft.tooltip.name", Component.translatable("langcraft.language." + language), StudyFont.of(name.get()))
						.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	public static void clear() {
		CACHE.clear();
	}
}
