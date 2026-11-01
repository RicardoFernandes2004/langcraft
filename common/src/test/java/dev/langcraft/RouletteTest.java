package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.Roulette;
import dev.langcraft.challenge.Roulette.Slice;
import dev.langcraft.challenge.Roulette.SliceSpec;
import dev.langcraft.challenge.RouletteEffect;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RouletteTest {
	private static final List<SliceSpec> DEFAULTS = List.of(
			new SliceSpec(0, 25), new SliceSpec(25, 25), new SliceSpec(50, 20), new SliceSpec(75, 15), new SliceSpec(100, 15));

	@Test
	void fullStackHasAllFiveSlices() {
		assertEquals(List.of(new Slice(1, 25), new Slice(16, 25), new Slice(32, 20), new Slice(48, 15), new Slice(64, 15)),
				Roulette.buildSlices(64, DEFAULTS));
	}

	@Test
	void stackOfTwoMergesIntoOneAndTwo() {
		assertEquals(List.of(new Slice(1, 70), new Slice(2, 30)), Roulette.buildSlices(2, DEFAULTS));
	}

	@Test
	void stackOfOneHasSingleSlice() {
		assertEquals(List.of(new Slice(1, 100)), Roulette.buildSlices(1, DEFAULTS));
	}

	@Test
	void percentagesRoundUp() {
		List<Slice> slices = Roulette.buildSlices(5, DEFAULTS);
		assertEquals(List.of(1, 2, 3, 4, 5), slices.stream().map(Slice::amount).toList());
	}

	@Test
	void eachMistakeInARowTakesTheLightestSlice() {
		assertEquals(DEFAULTS, Roulette.harsher(DEFAULTS, 1));
		assertEquals(DEFAULTS.subList(1, 5), Roulette.harsher(DEFAULTS, 2));
		assertEquals(DEFAULTS.subList(4, 5), Roulette.harsher(DEFAULTS, 5));
		assertEquals(DEFAULTS.subList(4, 5), Roulette.harsher(DEFAULTS, 50), "a mais pesada sempre fica");
		assertEquals(List.of(), Roulette.harsher(List.of(), 3));
	}

	@Test
	void unreadableOrImpossibleEffectsLeaveTheWheel() {
		Roulette.EffectSpec death = new Roulette.EffectSpec(RouletteEffect.DEATH, 10);
		assertEquals(List.of(death), Roulette.usableEffects(java.util.Arrays.asList(
				death, new Roulette.EffectSpec(RouletteEffect.CURSE, 0), new Roulette.EffectSpec(null, 5), null)));
	}

	@Test
	void drawFollowsWeights() {
		List<Slice> slices = Roulette.buildSlices(64, DEFAULTS);
		Random random = new Random(42);
		int[] counts = new int[slices.size()];
		int runs = 100_000;
		for (int i = 0; i < runs; i++) {
			counts[Roulette.draw(slices, random)]++;
		}
		for (int i = 0; i < slices.size(); i++) {
			double expected = slices.get(i).weight() / 100.0;
			assertTrue(Math.abs((double) counts[i] / runs - expected) < 0.01, "fatia " + i);
		}
	}
}
