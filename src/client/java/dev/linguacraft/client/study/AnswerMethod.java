package dev.linguacraft.client.study;

/** Como o jogador respondeu a um desafio. Cada um treina uma habilidade diferente. */
public enum AnswerMethod {
	/** Falou no microfone: pronúncia. */
	SPEAK,
	/** Digitou a resposta: escrita. */
	TYPED,
	/** Escolheu entre as alternativas: leitura. */
	QUIZ,
	/** Segurou o item: reconhecer a palavra sozinha. */
	HOLD
}
