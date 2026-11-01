package dev.langcraft.client.study;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.langcraft.LangCraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/** As palavras que o jogador já encontrou nos desafios de um mundo, para estudar depois. */
public final class FlashcardDeck {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_ANSWER_LENGTH = 40;
	/** Dias até a próxima revisão depois de cada acerto seguido no estudo. */
	private static final int[] REVIEW_DAYS = {1, 3, 7, 14, 30, 60};
	private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;

	public static final class Tally {
		public int right;
		public int wrong;
	}

	public static final class Card {
		public String language = "";
		public String type = "";
		public String subject = "";
		public String word = "";
		public String nativeName = "";
		public int correct;
		public int wrong;
		public int studyRight;
		public int studyWrong;
		public @Nullable String lastWrongAnswer;
		/** Acertos e erros por {@link AnswerMethod}. Cartas antigas não têm essa separação. */
		public Map<String, Tally> methods = new LinkedHashMap<>();
		/** Quantos acertos seguidos a carta tem no estudo, para escolher o próximo intervalo. */
		public int reviewStep;
		/** Quando revisar de novo. Zero, o padrão de uma carta nova ou antiga, significa agora. */
		public long dueAt;

		public boolean isDue(long now) {
			return dueAt <= now;
		}

		public Tally tally(AnswerMethod method) {
			Tally tally = methods.get(method.name());
			return tally == null ? new Tally() : tally;
		}

		private int difficulty() {
			return wrong + studyWrong - correct - studyRight;
		}
	}

	private final Path file;
	private final Map<String, Card> cards = new LinkedHashMap<>();

	public FlashcardDeck(Path file) {
		this.file = file;
		load();
	}

	/** Registra o resultado de um desafio. {@code wrongAnswer} é o que o jogador disse, digitou ou clicou de errado. */
	public void record(String language, String type, String subject, String word, String nativeName, AnswerMethod method, boolean won, @Nullable String wrongAnswer) {
		Card card = cards.computeIfAbsent(language + "|" + type + "|" + subject, key -> new Card());
		card.language = language;
		card.type = type;
		card.subject = subject;
		card.word = word;
		card.nativeName = nativeName;
		Tally tally = card.methods.computeIfAbsent(method.name(), key -> new Tally());
		if (won) {
			card.correct++;
			tally.right++;
		} else {
			card.wrong++;
			tally.wrong++;
			// Perder um desafio mostra que a palavra não está firme. Vencer não adia a revisão, porque nem todo minigame pede para lembrar sozinho.
			card.reviewStep = Math.max(0, card.reviewStep - 1);
			card.dueAt = 0;
		}
		if (wrongAnswer != null && !wrongAnswer.isBlank()) {
			card.lastWrongAnswer = wrongAnswer.length() > MAX_ANSWER_LENGTH ? wrongAnswer.substring(0, MAX_ANSWER_LENGTH) : wrongAnswer;
		}
		save();
	}

	/**
	 * Marca a resposta do estudo e agenda a próxima revisão. Errar traz a carta de volta ao começo. Acertar
	 * uma carta que ainda não venceu não adianta o calendário, para uma revisão extra não encurtar o espaçamento.
	 */
	public void studied(Card card, boolean right, long now) {
		if (right) {
			card.studyRight++;
			if (card.isDue(now)) {
				card.dueAt = now + nextReviewDays(card) * MILLIS_PER_DAY;
				card.reviewStep = Math.min(card.reviewStep + 1, REVIEW_DAYS.length - 1);
			}
		} else {
			card.studyWrong++;
			card.reviewStep = 0;
			card.dueAt = 0;
		}
		save();
	}

	/** Em quantos dias a carta volta, se o jogador acertar agora. */
	public int nextReviewDays(Card card) {
		return REVIEW_DAYS[Math.min(card.reviewStep, REVIEW_DAYS.length - 1)];
	}

	/** Nas consultas abaixo, {@code languages} limita aos idiomas da lista. Nulo significa todos. */
	private static boolean inScope(Card card, @Nullable Collection<String> languages) {
		return languages == null || languages.contains(card.language);
	}

	public int size(@Nullable Collection<String> languages) {
		return (int) cards.values().stream().filter(card -> inScope(card, languages)).count();
	}

	public int dueCount(@Nullable Collection<String> languages, long now) {
		return (int) cards.values().stream().filter(card -> inScope(card, languages) && card.isDue(now)).count();
	}

	/** Quando a próxima carta vence, para dizer ao jogador quando voltar. */
	public long nextDueAt(@Nullable Collection<String> languages) {
		return cards.values().stream().filter(card -> inScope(card, languages)).mapToLong(card -> card.dueAt).min().orElse(0);
	}

	/** Só as cartas que venceram, a rodada normal de estudo. */
	public List<Card> dueSession(@Nullable Collection<String> languages, long now, Random random) {
		List<Card> session = session(languages, random);
		session.removeIf(card -> !card.isDue(now));
		return session;
	}

	/** Todas as cartas. As palavras em que o jogador mais erra vêm primeiro, e as empatadas vêm embaralhadas. */
	public List<Card> session(@Nullable Collection<String> languages, Random random) {
		List<Card> session = new ArrayList<>(cards.values().stream().filter(card -> inScope(card, languages)).toList());
		Collections.shuffle(session, random);
		session.sort(Comparator.comparingInt(Card::difficulty).reversed());
		return session;
	}

	/** Todas as palavras, da descoberta mais recente para a mais antiga. */
	public List<Card> words(@Nullable Collection<String> languages) {
		List<Card> words = new ArrayList<>(cards.values().stream().filter(card -> inScope(card, languages)).toList());
		Collections.reverse(words);
		return words;
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<Card> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Card>>() { }.getType());
			if (loaded != null) {
				for (Card card : loaded) {
					cards.put(card.language + "|" + card.type + "|" + card.subject, card);
				}
			}
		} catch (IOException | JsonParseException e) {
			// Guarda o arquivo ruim, porque o próximo desafio salvaria por cima dele.
			LangCraft.LOGGER.error("Flashcards inválidos em {}", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveFailure) {
				LangCraft.LOGGER.warn("Não foi possível guardar {}", file, moveFailure);
			}
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(new ArrayList<>(cards.values())), StandardCharsets.UTF_8);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LangCraft.LOGGER.warn("Não foi possível salvar {}", file, e);
		}
	}
}
