# Plano da v1.1

Fica para depois da publicação da 1.0.

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
