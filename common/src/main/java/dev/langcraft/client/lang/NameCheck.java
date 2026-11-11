package dev.langcraft.client.lang;

import dev.langcraft.data.PlayerProgress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reconhece nomes com defeito, que alguns mods trazem nos arquivos de idioma: letras trocadas por ponto de
 * interrogação, caracteres que não foram lidos direito, letras que a fonte do jogo não desenha ou a própria chave
 * no lugar do nome. Um item assim não entra nos desafios, nas alternativas nem no tooltip.
 */
public final class NameCheck {
	/** Texto em UTF-8 lido como Latin-1, como "Ã©" no lugar de "é". */
	private static final Pattern MOJIBAKE = Pattern.compile("[ÃÂâ][\\x80-\\xBF€™œ]");
	/** Em quantos nomes um caractere precisa aparecer como ícone para contar como ícone. */
	private static final int ICON_MIN_NAMES = 3;
	private static final int ICON_MAX_TOKEN = 8;
	private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]");

	private NameCheck() {
	}

	/**
	 * Tira do nome os ícones que alguns modpacks colocam junto, como o valor da comida desenhado por uma fonte
	 * própria: símbolos, emoji e a área de uso privado. Assim, "パン" com três coxinhas volta a ser "パン".
	 */
	public static String clean(String name) {
		StringBuilder kept = new StringBuilder(name.length());
		name.codePoints().filter(c -> drawable(c) && Character.getType(c) != Character.OTHER_SYMBOL).forEach(kept::appendCodePoint);
		return kept.toString().strip().replaceAll("\\s{2,}", " ");
	}

	/**
	 * Alguns modpacks desenham ícones, como o valor da comida, com letras comuns que a fonte deles troca por
	 * uma figura. "パン 渚海海" aparece como pão e três coxinhas. Esses caracteres aparecem no fim de vários
	 * nomes, depois de um espaço, num pedaço curto com um caractere repetido. Uma palavra de verdade quase
	 * nunca se repete assim, e as letras do alfabeto latino nunca contam, para "Sword II" continuar inteiro.
	 */
	public static Set<Integer> iconChars(Collection<String> names) {
		Map<Integer, Integer> counts = new HashMap<>();
		for (String name : names) {
			String token = lastToken(name);
			if (token == null || token.length() > ICON_MAX_TOKEN) {
				continue;
			}
			int[] codePoints = token.codePoints().toArray();
			Set<Integer> distinct = new HashSet<>();
			for (int c : codePoints) {
				distinct.add(c);
			}
			if (codePoints.length >= 2 && distinct.size() <= 2 && distinct.size() < codePoints.length
					&& distinct.stream().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN || Character.isDigit(c))) {
				distinct.forEach(c -> counts.merge(c, 1, Integer::sum));
			}
		}
		Set<Integer> icons = new HashSet<>();
		counts.forEach((c, count) -> {
			if (count >= ICON_MIN_NAMES) {
				icons.add(c);
			}
		});
		return icons;
	}

	/** Tira do fim do nome os pedaços feitos só de caracteres-ícone, além do que {@link #clean(String)} já tira. */
	public static String clean(String name, Set<Integer> icons) {
		String clean = clean(name);
		String token = lastToken(clean);
		while (token != null && !icons.isEmpty() && token.codePoints().allMatch(icons::contains)) {
			clean = clean.substring(0, clean.length() - token.length()).strip();
			token = lastToken(clean);
		}
		return clean;
	}

	private static String lastToken(String name) {
		String stripped = name.strip();
		int space = stripped.lastIndexOf(' ');
		return space < 0 ? null : stripped.substring(space + 1);
	}

	public static boolean isBroken(String name, String key) {
		if (name.isBlank() || name.equals(key)) {
			return true;
		}
		boolean[] broken = {false};
		name.codePoints().forEach(c -> broken[0] |= c == '?' || c == 0xFFFD || Character.isISOControl(c) || !drawable(c));
		return broken[0] || MOJIBAKE.matcher(name).find() || PLACEHOLDER.matcher(name).find();
	}

	/**
	 * A fonte do jogo desenha todo o plano básico do Unicode, então o que fica sem desenho é o que não existe,
	 * a área de uso privado, uma metade solta de par e o que está fora desse plano, como emoji.
	 */
	private static boolean drawable(int c) {
		int type = Character.getType(c);
		return c <= 0xFFFF && Character.isDefined(c) && type != Character.PRIVATE_USE && type != Character.SURROGATE;
	}

	/**
	 * Muitos mods trazem o arquivo do coreano ou do japonês com parte dos nomes ainda em inglês. Um nome assim
	 * não pode ser falado no idioma estudado, então conta como sem tradução. Nos idiomas de alfabeto latino,
	 * não há como separar um nome em inglês de um nome traduzido.
	 */
	public static boolean fitsScript(String name, String language) {
		if (language.equals(PlayerProgress.KOREAN)) {
			return name.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
		}
		if (language.equals(PlayerProgress.JAPANESE)) {
			return name.codePoints().anyMatch(c -> switch (Character.UnicodeScript.of(c)) {
				case HIRAGANA, KATAKANA, HAN -> true;
				default -> false;
			});
		}
		return true;
	}
}
