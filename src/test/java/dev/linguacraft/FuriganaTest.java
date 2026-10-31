package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.client.lang.Furigana;
import dev.linguacraft.client.lang.Furigana.Segment;
import dev.linguacraft.client.match.AnswerMatcher;
import dev.linguacraft.data.PlayerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuriganaTest {
	private static final String JA = PlayerProgress.JAPANESE;

	@Test
	void kanjiGetsReadingAndKanaStaysPlain() {
		assertEquals(List.of(new Segment("ダイヤモンドの", null), new Segment("剣", "けん")), Furigana.segments("ダイヤモンドの剣", JA));
	}

	@Test
	void okuriganaStaysOutsideTheReading() {
		assertEquals(List.of(new Segment("食", "た"), new Segment("べる", null)), Furigana.segments("食べる", JA));
	}

	@Test
	void textWithoutKanjiHasNoReadings() {
		List<Segment> segments = Furigana.segments("ツルハシ", JA);
		assertEquals(List.of(new Segment("ツルハシ", null)), segments);
		assertFalse(Furigana.hasReadings(segments));
	}

	@Test
	void koreanIsNeverAnnotated() {
		assertEquals(List.of(new Segment("다이아몬드 검", null)), Furigana.segments("다이아몬드 검", PlayerProgress.KOREAN));
	}

	@Test
	void inlineFormForChat() {
		assertEquals("鉄(てつ)のツルハシ", Furigana.inline("鉄のツルハシ", JA));
		assertEquals("검", Furigana.inline("검", PlayerProgress.KOREAN));
	}

	@Test
	void minecraftNamesUseCorrectedReadings() {
		assertEquals("黄緑色(きみどりいろ)のハーネス", Furigana.inline("黄緑色のハーネス", JA));
		assertEquals("砂岩(さがん)", Furigana.inline("砂岩", JA));
		assertEquals("歪(ゆが)んだ幹(みき)", Furigana.inline("歪んだ幹", JA));
		assertEquals("金床(かなとこ)", Furigana.inline("金床", JA));
		assertEquals("金(きん)ブロック", Furigana.inline("金ブロック", JA));
		assertEquals("的(まと)", Furigana.inline("的", JA));
		assertEquals("粗(あら)い土(つち)", Furigana.inline("粗い土", JA));
		assertEquals("石(いし)の感圧板(かんあつばん)", Furigana.inline("石の感圧板", JA));
		assertEquals("鍛冶(かじ)台(だい)", Furigana.inline("鍛冶台", JA));
	}

	@Test
	void singleKanjiOverrideDoesNotBreakCompounds() {
		assertEquals("金網(かなあみ)", Furigana.inline("金網", JA));
		assertEquals("金塊(きんかい)", Furigana.inline("金塊", JA));
	}

	@Test
	void correctedReadingCountsWhenAnswering() {
		assertTrue(AnswerMatcher.matchesTyped("黄緑色のハーネス", "きみどりいろのはーねす", JA));
		assertTrue(AnswerMatcher.matchesTyped("金床", "かなとこ", JA));
		assertTrue(AnswerMatcher.matchesSpoken("砂岩", "さがん", JA, 0.75));
	}

	@Test
	void compoundKeepsItsWholeReading() {
		List<Segment> segments = Furigana.segments("黒曜石", JA);
		assertTrue(Furigana.hasReadings(segments));
		assertEquals("こくようせき", String.join("", segments.stream().map(Segment::reading).toList()));
	}
}
