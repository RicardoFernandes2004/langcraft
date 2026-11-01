package dev.linguacraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.linguacraft.challenge.ChallengeTimers;
import dev.linguacraft.challenge.ChallengeTimers.Due;
import org.junit.jupiter.api.Test;

class ChallengeTimersTest {
	private static final int HUGE = 1_000_000;

	private static Due run(ChallengeTimers timers, int ticks) {
		Due due = Due.NONE;
		for (int i = 0; i < ticks && due == Due.NONE; i++) {
			due = timers.tick(true, true, HUGE, HUGE);
		}
		return due;
	}

	@Test
	void firesOnlyAfterTheIntervalPasses() {
		ChallengeTimers timers = new ChallengeTimers(100, 1000);
		assertEquals(Due.NONE, run(timers, 99));
		assertEquals(Due.ITEM, timers.tick(true, true, HUGE, HUGE));
	}

	@Test
	void restingStopsBothIntervals() {
		ChallengeTimers timers = new ChallengeTimers(10, 10);
		timers.rest(50);
		assertEquals(Due.NONE, run(timers, 50));
		assertEquals(Due.NONE, run(timers, 9));
		assertEquals(Due.ITEM, timers.tick(true, true, HUGE, HUGE));
	}

	@Test
	void startingOneDoesNotLetTheOtherFollowRightAfter() {
		ChallengeTimers timers = new ChallengeTimers(10, 10);
		assertEquals(Due.ITEM, run(timers, 10));
		timers.itemStarted(1000, 500);
		assertEquals(Due.NONE, run(timers, 499));
		assertEquals(Due.MOB, timers.tick(true, true, HUGE, HUGE));
	}

	@Test
	void anUnfinishedIntervalIsNotShortenedByTheOtherOneStarting() {
		ChallengeTimers timers = new ChallengeTimers(10, 800);
		assertEquals(Due.ITEM, run(timers, 10));
		timers.itemStarted(5000, 50);
		// O tick em que o item venceu não conta para o mob, então ainda faltam 791.
		assertEquals(Due.NONE, run(timers, 790));
		assertEquals(Due.MOB, timers.tick(true, true, HUGE, HUGE));
	}

	@Test
	void nothingToAskRetriesSoonInsteadOfWaitingAWholeInterval() {
		ChallengeTimers timers = new ChallengeTimers(10, HUGE);
		assertEquals(Due.ITEM, run(timers, 10));
		timers.retryItem(200);
		assertEquals(Due.NONE, run(timers, 199));
		assertEquals(Due.ITEM, timers.tick(true, true, HUGE, HUGE));
	}

	@Test
	void disabledIntervalsDoNotFireOrCount() {
		ChallengeTimers timers = new ChallengeTimers(5, 5);
		for (int i = 0; i < 100; i++) {
			assertEquals(Due.NONE, timers.tick(false, false, HUGE, HUGE));
		}
		assertEquals(Due.NONE, timers.tick(true, false, HUGE, HUGE));
	}

	@Test
	void loweringTheIntervalInTheConfigTakesEffectImmediately() {
		ChallengeTimers timers = new ChallengeTimers(100_000, HUGE);
		assertEquals(Due.NONE, timers.tick(true, false, 20, HUGE));
		Due due = Due.NONE;
		for (int i = 0; i < 19 && due == Due.NONE; i++) {
			due = timers.tick(true, false, 20, HUGE);
		}
		assertEquals(Due.ITEM, due);
	}
}
