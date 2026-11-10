package dev.langcraft.client.lang;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameCheckTest {
	private static final String KEY = "item.zombie.cola";

	@Test
	void acceptsNormalNamesInEveryLanguage() {
		for (String name : new String[] {"Diamond Pickaxe", "Picareta de diamante", "다이아몬드 곡괭이", "ダイヤモンドのツルハシ", "Diamantspitzhacke", "Pico de diamante", "Coca-Cola"}) {
			assertFalse(NameCheck.isBroken(name, KEY), name);
		}
	}

	@Test
	void rejectsQuestionMarksAndReplacementCharacters() {
		assertTrue(NameCheck.isBroken("Coca-Cola ??", KEY));
		assertTrue(NameCheck.isBroken("?? ??", KEY));
		assertTrue(NameCheck.isBroken("Refrigerante �", KEY));
	}

	@Test
	void rejectsMisreadText() {
		assertTrue(NameCheck.isBroken("MaÃ§Ã£", KEY));
		assertTrue(NameCheck.isBroken("CafÃ©", KEY));
	}

	@Test
	void rejectsKeysPlaceholdersAndEmptyNames() {
		assertTrue(NameCheck.isBroken(KEY, KEY));
		assertTrue(NameCheck.isBroken("  ", KEY));
		assertTrue(NameCheck.isBroken("%s Cola", KEY));
		assertTrue(NameCheck.isBroken("Linha\nquebrada", KEY));
	}
}
