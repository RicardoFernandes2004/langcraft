package dev.langcraft.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langcraft.challenge.MobPunishment.Tier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModMobsTest {
	private static final List<Tier> TIERS = List.of(
			new Tier(ModMobs.HARMLESS, 40, List.of("minecraft:chicken")),
			new Tier(ModMobs.HOSTILE, 22, List.of("minecraft:zombie", "outro:ja_listado")));

	@Test
	void classificationFollowsTheLimits() {
		assertEquals(ModMobs.HARMLESS, ModMobs.tierFor(false, 20));
		assertEquals(ModMobs.NEUTRAL, ModMobs.tierFor(false, 20.5));
		assertEquals(ModMobs.NEUTRAL, ModMobs.tierFor(false, 500));
		assertEquals(ModMobs.HOSTILE, ModMobs.tierFor(true, 20));
		assertEquals(ModMobs.HOSTILE, ModMobs.tierFor(true, 40));
		assertEquals(ModMobs.DANGEROUS, ModMobs.tierFor(true, 40.5));
		assertEquals(ModMobs.DANGEROUS, ModMobs.tierFor(true, 100));
		assertEquals(ModMobs.EXTREME, ModMobs.tierFor(true, 100.5));
	}

	private static Map<String, String> installed() {
		Map<String, String> installed = new LinkedHashMap<>();
		installed.put("outro:coelho", ModMobs.HARMLESS);
		installed.put("outro:orc", ModMobs.HOSTILE);
		installed.put("outro:ja_listado", ModMobs.HARMLESS);
		installed.put("outro:chefe", ModMobs.EXTREME);
		return installed;
	}

	@Test
	void installedMobsJoinTheirTierWithoutRemovingAnyone() {
		List<Tier> merged = ModMobs.merge(TIERS, installed(), List.of());
		assertEquals(List.of("minecraft:chicken", "outro:coelho"), merged.get(0).mobs());
		assertEquals(List.of("minecraft:zombie", "outro:ja_listado", "outro:orc"), merged.get(1).mobs());
		assertEquals(40, merged.get(0).weight());
	}

	@Test
	void aMobAlreadyListedIsNotDuplicated() {
		List<Tier> merged = ModMobs.merge(TIERS, installed(), List.of());
		assertFalse(merged.get(0).mobs().contains("outro:ja_listado"));
		assertEquals(1, merged.get(1).mobs().stream().filter("outro:ja_listado"::equals).count());
	}

	@Test
	void blockedMobsStayOut() {
		List<Tier> merged = ModMobs.merge(TIERS, installed(), List.of("outro:orc"));
		assertFalse(merged.get(1).mobs().contains("outro:orc"));
		assertTrue(merged.get(0).mobs().contains("outro:coelho"));
	}

	@Test
	void aTierMissingFromTheConfigGetsNobody() {
		List<Tier> merged = ModMobs.merge(TIERS, installed(), List.of());
		assertEquals(2, merged.size());
		assertFalse(merged.stream().anyMatch(tier -> tier.mobs().contains("outro:chefe")));
	}
}
