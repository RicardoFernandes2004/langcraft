# Plano da v1.1

Fica para depois da publicação da 1.0. As duas frentes são independentes e podem ser feitas em qualquer ordem. Por servir ao coreano e ao japonês logo de cara, o suporte a outros mods vem primeiro.

## 1. Suporte a itens e mobs de outros mods

O ícone e o nome já vêm do próprio jogo, sem nenhuma biblioteca de conexão. Um mod que traz `ko_kr` ou `ja_jp` já deve funcionar, mas isso nunca foi testado com outro mod instalado.

| Problema | Solução |
|---|---|
| Item sem tradução nunca é sorteado, sem aviso | Arquivo de nomes próprio, lido antes dos idiomas do jogo |
| O jogador não sabe o que falta traduzir | Comando `/lingua faltando`, que exporta os itens sem tradução dos mods instalados |
| Mob de outro mod só entra pela configuração | Classificar sozinho pelo tipo e pela vida do mob |
| Nome inventado pode não existir no modelo de voz | Conferir se o modelo conhece a palavra e, se não, sortear popup ou Pegue na mão |

O arquivo exportado tem este formato:

```json
{
  "item.botania.manasteel_ingot": {
    "nome": "Manasteel Ingot",
    "traducao": ""
  }
}
```

O jogador preenche à mão, com um tradutor ou com uma IA, e devolve em `config/linguacraft/`. O arquivo pronto de cada mod pode ser compartilhado. Os nomes que vierem desse arquivo aparecem como "tradução da comunidade".

Fora de escopo:

- **Tradução automática dentro do jogo:** exige internet e, em geral, uma chave de serviço.
- **Montar nomes a partir das palavras do jogo:** ensinaria palavras inventadas ou erradas.

Etapas:

1. Instalar um mod popular e registrar o que acontece hoje.
2. Implementar o arquivo de nomes e o `/lingua faltando`.
3. Implementar a classificação automática de mobs.
4. Medir a voz com nomes reais e implementar a conferência do vocabulário.

## 2. Voz nos novos idiomas

Inglês, alemão e espanhol já entraram na 1.0, mas a voz deles ainda não foi medida.

- **Medir:** gravar ou reunir falas de nativos e rodar o `VoiceBenchmarkTest`, como foi feito no coreano e no japonês. A decisão de somar ou não o Whisper sai das medições.
- **Maior risco:** nomes longos em alemão, como `Diamantspitzhacke`, podem não existir no modelo pequeno do Vosk.
- **Outros idiomas:** cada um novo começa por `StudyLanguage`. Francês e italiano devem ser parecidos com o espanhol. Chinês precisaria de uma ajuda de leitura, como o pinyin. Árabe é o mais difícil, por ser escrito da direita para a esquerda.
