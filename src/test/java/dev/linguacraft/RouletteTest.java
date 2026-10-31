package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linguacraft.challenge.Roulette;
import dev.linguacraft.challenge.Roulette.Slice;
import dev.linguacraft.challenge.Roulette.SliceSpec;
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
