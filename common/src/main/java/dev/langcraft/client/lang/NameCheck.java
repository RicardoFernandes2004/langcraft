package dev.langcraft.client.lang;

import java.util.regex.Pattern;

/**
 * Reconhece nomes com defeito, que alguns mods trazem nos arquivos de idioma: letras trocadas por ponto de
 * interrogação, caracteres que não foram lidos direito ou a própria chave no lugar do nome. Um item assim não
 * entra nos desafios, nas alternativas nem no tooltip.
 */
public final class NameCheck {
	/** Texto em UTF-8 lido como Latin-1, como "Ã©" no lugar de "é". */
	private static final Pattern MOJIBAKE = Pattern.compile("[ÃÂâ][\\x80-\\xBF€™œ]");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]");

	private NameCheck() {
	}

	public static boolean isBroken(String name, String key) {
		if (name.isBlank() || name.equals(key)) {
			return true;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '?' || c == '�' || Character.isISOControl(c)) {
				return true;
			}
		}
		return MOJIBAKE.matcher(name).find() || PLACEHOLDER.matcher(name).find();
	}
}
