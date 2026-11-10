package dev.langcraft.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChallengeKindTest {
	@Test
	void timedKindsMatchTheirMinigame() {
		assertEquals(ChallengeKind.ITEM_HOLD, ChallengeKind.timed(SubjectType.ITEM, Minigame.HOLD));
		assertEquals(ChallengeKind.ITEM_SPEAK, ChallengeKind.timed(SubjectType.ITEM, Minigame.SPEAK));
		assertEquals(ChallengeKind.MOB_QUIZ, ChallengeKind.timed(SubjectType.MOB, Minigame.QUIZ));
		assertNull(ChallengeKind.timed(SubjectType.MOB, Minigame.HOLD));
		assertNull(ChallengeKind.timed(SubjectType.BIOME, Minigame.SPEAK));
	}

	@Test
	void discoveriesAreNotTimed() {
		assertFalse(ChallengeKind.BIOME.timed());
		assertFalse(ChallengeKind.NEW_ITEM.timed());
		assertTrue(ChallengeKind.ITEM_QUIZ.timed());
	}

	@Test
	void idsRoundTrip() {
		for (ChallengeKind kind : ChallengeKind.values()) {
			assertEquals(kind, ChallengeKind.byId(kind.id()));
		}
		assertNull(ChallengeKind.byId("nada"));
	}
}
