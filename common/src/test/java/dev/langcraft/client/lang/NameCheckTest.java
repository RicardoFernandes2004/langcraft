package dev.langcraft.client.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void rejectsLettersTheFontCannotDraw() {
		assertTrue(NameCheck.isBroken("CocaCola", KEY), "area de uso privado");
		assertTrue(NameCheck.isBroken("Cola 🥤", KEY), "emoji fora do plano basico");
		assertTrue(NameCheck.isBroken("Cola \uD83E", KEY), "metade solta de um par");
		assertTrue(NameCheck.isBroken("Cola ͸", KEY), "caractere que nao existe");
	}

	@Test
	void koreanAndJapaneseNeedTheirOwnScript() {
		assertFalse(NameCheck.fitsScript("Iron Sword", "ko_kr"));
		assertTrue(NameCheck.fitsScript("철 검", "ko_kr"));
		assertTrue(NameCheck.fitsScript("TNT 블록", "ko_kr"));
		assertFalse(NameCheck.fitsScript("Iron Sword", "ja_jp"));
		assertTrue(NameCheck.fitsScript("鉄の剣", "ja_jp"));
		assertTrue(NameCheck.fitsScript("ダイヤモンド", "ja_jp"));
		assertFalse(NameCheck.fitsScript("철 검", "ja_jp"));
		assertTrue(NameCheck.fitsScript("Iron Sword", "de_de"));
	}

	@Test
	void cleanRemovesIconsThatModpacksAddToNames() {
		assertEquals("パン", NameCheck.clean("パン\uD83C\uDF57\uD83C\uDF57\uD83C\uDF57"));
		assertEquals("Bread", NameCheck.clean("Bread \uE001\uE001"));
		assertEquals("Iron Sword", NameCheck.clean("Iron \u2694 Sword"));
		assertEquals("다이아몬드 곡괭이", NameCheck.clean("다이아몬드 곡괭이"));
		assertEquals("", NameCheck.clean("\uE000\uE001"));
	}

	@Test
	void iconCharactersAreFoundAcrossNamesAndRemoved() {
		java.util.List<String> names = java.util.List.of("パン 渚海海", "焼き鳥 海海海", "リンゴ 海渚", "クッキー 渚渚", "魚 渚渚海", "ダイヤモンドの剣", "Sword II",
				"다이아몬드 검", "철 검", "돌 검", "나무 검");
		java.util.Set<Integer> icons = NameCheck.iconChars(names);
		assertEquals(java.util.Set.of((int) '渚', (int) '海'), icons);
		assertEquals("パン", NameCheck.clean("パン 渚海海", icons));
		assertEquals("リンゴ", NameCheck.clean("リンゴ 渚", icons), "um ícone sozinho também sai, depois de reconhecido");
		assertEquals("다이아몬드 검", NameCheck.clean("다이아몬드 검", icons), "palavra sem repetição nunca vira ícone");
		assertEquals("Sword II", NameCheck.clean("Sword II", icons));
		assertEquals("海", NameCheck.clean("海", icons), "o nome inteiro nunca some");
	}
}
