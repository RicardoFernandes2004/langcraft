package dev.langcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.MobPunishment;
import dev.langcraft.challenge.MobPunishment.Tier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MobPunishmentTest {
	private static final List<Tier> TIERS = List.of(
			new Tier("inofensivos", 40, List.of("chicken", "axolotl")),
			new Tier("neutros", 25, List.of("bee")),
			new Tier("hostis", 22, List.of("zombie")),
			new Tier("perigosos", 11, List.of("ravager")),
			new Tier("extremo", 2, List.of("warden")));

	@Test
	void drawFollowsTierWeights() {
		Random random = new Random(7);
		Map<String, Integer> counts = new HashMap<>();
		int runs = 200_000;
		for (int i = 0; i < runs; i++) {
			counts.merge(MobPunishment.draw(TIERS, mob -> true, random).orElseThrow(), 1, Integer::sum);
		}
		assertClose(0.20, counts.get("chicken"), runs);
		assertClose(0.20, counts.get("axolotl"), runs);
		assertClose(0.25, counts.get("bee"), runs);
		assertClose(0.22, counts.get("zombie"), runs);
		assertClose(0.11, counts.get("ravager"), runs);
		assertClose(0.02, counts.get("warden"), runs);
	}

	@Test
	void disallowedMobsNeverComeUp() {
		Set<String> hostile = Set.of("zombie", "ravager", "warden");
		Random random = new Random(7);
		for (int i = 0; i < 10_000; i++) {
			assertFalse(hostile.contains(MobPunishment.draw(TIERS, mob -> !hostile.contains(mob), random).orElseThrow()));
		}
	}

	@Test
	void nothingAllowedDrawsNothing() {
		assertTrue(MobPunishment.draw(TIERS, mob -> false, new Random(7)).isEmpty());
	}

	private static void assertClose(double expected, int count, int runs) {
		assertEquals(expected, (double) count / runs, 0.01);
	}
}
