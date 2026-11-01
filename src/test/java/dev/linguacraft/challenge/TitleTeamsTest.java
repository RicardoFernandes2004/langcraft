package dev.linguacraft.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TitleTeamsTest {
	private static final UUID ANA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
	private static final UUID BRUNO = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");

	@Test
	void moreCorrectAnswersSortFirstAsTheTabListOrdersThem() {
		List<String> names = new ArrayList<>(List.of(
				TitleTeams.teamName(3, ANA), TitleTeams.teamName(1042, BRUNO), TitleTeams.teamName(47, ANA), TitleTeams.teamName(0, BRUNO), TitleTeams.teamName(613, ANA)));
		names.sort(String::compareTo);
		assertEquals(List.of(
				TitleTeams.teamName(1042, BRUNO), TitleTeams.teamName(613, ANA), TitleTeams.teamName(47, ANA), TitleTeams.teamName(3, ANA), TitleTeams.teamName(0, BRUNO)), names);
	}

	@Test
	void eachPlayerHasTheirOwnTeamEvenWhenTied() {
		assertNotEquals(TitleTeams.teamName(10, ANA), TitleTeams.teamName(10, BRUNO));
	}

	@Test
	void hugeScoresKeepTheNameValid() {
		assertTrue(TitleTeams.teamName(Integer.MAX_VALUE, ANA).startsWith("lc_0000000_"));
		assertEquals(TitleTeams.teamName(9_999_999, ANA), TitleTeams.teamName(Integer.MAX_VALUE, ANA));
	}
}
