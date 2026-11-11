# Plano da v1.2

O que ficou para depois da 1.1.

## Precisão da voz

No japonês, uma fala errada às vezes conta como certa. O coreano está bom e não deve mudar. Antes de mexer, é preciso gravar amostras de novo e medir com o `VoiceBenchmarkTest`, porque as gravações antigas se perderam.

Ideias já testadas e deixadas de lado na 1.1, por medo de piorar o reconhecimento sem medir:

- Contar só a escolha principal do Vosk restrito, sem as alternativas e os parciais dele.
- Colocar na gramática os nomes mais parecidos com a resposta, para o Vosk ter com o que confundir.
- Exigir a palavra exata em nomes de até 3 letras.

## Voz nos novos idiomas

Inglês, alemão e espanhol já entraram na 1.0, mas a voz deles ainda não foi medida.

- **Medir:** gravar ou reunir falas de nativos e rodar o `VoiceBenchmarkTest`, como foi feito no coreano e no japonês. A decisão de somar ou não o Whisper sai das medições.
- **Maior risco:** nomes longos em alemão, como `Diamantspitzhacke`, podem não existir no modelo pequeno do Vosk.
- **Outros idiomas:** cada um novo começa por `StudyLanguage`. Francês e italiano devem ser parecidos com o espanhol. Chinês precisaria de uma ajuda de leitura, como o pinyin. Árabe é o mais difícil, por ser escrito da direita para a esquerda.

## Desafio de ouvir

Um minigame novo: o jogo fala a palavra com uma voz sintetizada pelo Piper, e o jogador responde o que ouviu.

- **Resposta:** selecionar entre alternativas, que é o mais fácil e o caminho preferido. Digitar o que ouviu fica como opção a avaliar, aproveitando a caixa que a fala já usa.
- **Tela:** a palavra não pode aparecer como texto antes da resposta, porque entregaria o desafio. As alternativas já mostram a escrita, e um botão repete o áudio.
- **Fora do plano:** o inverso, em que a tela mostra a palavra e o jogador fala, já existe como o alerta de fala e não entra aqui.

Pontos a resolver antes de implementar:

- **Coreano e japonês:** a lista oficial de vozes do Piper não traz nenhum dos dois, e são os idiomas principais do mod. Inglês, alemão e espanhol têm várias vozes. As saídas a comparar são projetos paralelos, como o piper-plus, e outro motor só para esses dois idiomas.
- **Licença:** o Piper antigo era MIT e foi arquivado em outubro de 2025. O desenvolvimento seguiu no `piper1-gpl`, sob GPL, e o mod é MIT. Levar o motor dentro do `.jar` exige decidir entre a versão antiga, um processo separado ou outro motor. Cada voz também tem licença própria, a conferir uma a uma.
- **Tamanho:** as vozes seriam baixadas sob demanda, como os modelos de reconhecimento, com a mesma barra de download. O tamanho e o tempo de geração ainda precisam ser medidos.
- **Loaders:** o motor nativo tem que abrir no Fabric, no NeoForge e no Forge, em Windows e Linux.

## Bloqueio de palavras

A 1.0.2 já bloqueia itens pelo item da mão: `/lang block` só para o jogador, e `/lang admin block` para todos, em `blockedItems`. Fica para a 1.2:

- **Mobs:** `blockedMobs` só impede a entrada automática de mobs de outros mods. Falta bloquear qualquer mob, inclusive os que já estão em `mobTiers`, e um jeito de escolher o mob sem saber o id, como o mob para onde o jogador está olhando.
- **Alternativas erradas:** o popup ainda pode mostrar um item bloqueado como alternativa errada, porque as alternativas são montadas no cliente, que não recebe a lista do servidor.
- **Desafio em andamento:** bloquear pela palavra do desafio aberto, e não só pelo item da mão. Hoje, o desafio só é cancelado se o item bloqueado for o da pergunta.
