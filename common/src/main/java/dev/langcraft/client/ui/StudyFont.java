package dev.langcraft.client.ui;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Coreano e japonês saem sempre na fonte unicode do próprio jogo. Alguns modpacks trocam a fonte padrão por
 * uma que desenha essas letras com um quadrado preto atrás. No jogo sem modpack, a fonte padrão já usa essa
 * mesma fonte para essas letras, então nada muda na aparência.
 */
public final class StudyFont {
	private static final Style UNIFORM = Style.EMPTY.withFont(new FontDescription.Resource(Minecraft.UNIFORM_FONT));

	private StudyFont() {
	}

	public static MutableComponent of(String text) {
		return apply(Component.literal(text));
	}

	/** Troca a fonte só dos trechos em coreano e japonês, mantendo o estilo do resto, como cor e negrito. */
	public static MutableComponent apply(Component component) {
		MutableComponent result = Component.empty();
		component.visit((style, text) -> {
			StringBuilder run = new StringBuilder();
			boolean runIsAsian = false;
			for (int c : text.codePoints().toArray()) {
				boolean asian = isAsian(c);
				if (run.length() > 0 && asian != runIsAsian) {
					result.append(Component.literal(run.toString()).withStyle(runIsAsian ? style.withFont(UNIFORM.getFont()) : style));
					run.setLength(0);
				}
				runIsAsian = asian;
				run.appendCodePoint(c);
			}
			if (run.length() > 0) {
				result.append(Component.literal(run.toString()).withStyle(runIsAsian ? style.withFont(UNIFORM.getFont()) : style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return result;
	}

	private static boolean isAsian(int c) {
		return switch (Character.UnicodeScript.of(c)) {
			case HANGUL, HIRAGANA, KATAKANA, HAN -> true;
			default -> false;
		};
	}
}
