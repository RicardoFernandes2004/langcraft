# LangCraft

Mod para Fabric, NeoForge e Forge, no Minecraft 1.21.11, que treina vocabulário coreano e japonês com os itens do seu inventário e com os mobs do jogo. Quem perde o desafio perde itens ou recebe um mob em cima da cabeça. Quem mantém uma sequência de acertos gira uma roleta de prêmios.

## Instalação

Use o `.jar` do seu loader e nenhum dos outros.

**Fabric**

1. Instale o [Fabric Loader](https://fabricmc.net/use/installer/) para o Minecraft 1.21.11.
2. Baixe a [Fabric API](https://modrinth.com/mod/fabric-api) para 1.21.11 e coloque na pasta `mods`.
3. Coloque `langcraft-fabric-1.21.11-1.0.0.jar` (em `fabric/build/libs`) na mesma pasta.
4. Opcional: coloque também o [Mod Menu](https://modrinth.com/mod/modmenu) 17.0.1, que adiciona o botão **Mods** e a engrenagem da configuração.
5. Abra o jogo com o perfil Fabric 1.21.11.

**NeoForge**

1. Instale o [NeoForge](https://neoforged.net/) 21.11 para o Minecraft 1.21.11.
2. Coloque `langcraft-neoforge-1.21.11-1.0.0.jar` (em `neoforge/build/libs`) na pasta `mods`.

**Forge**

1. Instale o [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.11.html) 61 para o Minecraft 1.21.11.
2. Coloque `langcraft-forge-1.21.11-1.0.0.jar` (em `forge/build/libs`) na pasta `mods`. O arquivo terminado em `-slim` não leva as bibliotecas de voz e não serve para jogar.

No NeoForge e no Forge, a configuração abre pelo botão **Config** da lista de mods.

Funciona em Windows e em Linux de 64 bits (x86-64). No Linux, a opção recomendada é **Entrada do sistema**, que segue o microfone escolhido nas configurações do seu desktop. Os microfones vêm do PulseAudio ou do PipeWire, com os mesmos nomes das configurações do sistema, por meio dos comandos `pactl` e `parec` (pacote `pulseaudio-utils` ou `libpulse`). Sem esses comandos, como em um launcher Flatpak sem acesso a eles, o mod usa o ALSA, que só mostra as placas de som, com nomes como `[plughw:0,0]`.

Em servidores, o mod precisa estar no servidor e no jogo de cada jogador. Jogadores sem o mod não recebem desafios.

## Primeira entrada no mundo

1. Uma tela pede o tema visual, com uma amostra de cada um. Cada mundo tem o seu tema, então essa tela aparece na primeira entrada em cada mundo.
2. Uma tela pergunta quais idiomas você quer praticar: coreano, japonês, inglês, alemão ou espanhol. Dá para marcar quantos quiser, e cada desafio sorteia um deles. A escolha fica salva por jogador em cada mundo.
3. Outra tela lista os microfones do computador, com o padrão do sistema em primeiro lugar. Se o microfone escolhido sumir depois, o mod usa o padrão.
4. Se o modelo de voz ainda não foi baixado, uma tela mostra o progresso e fecha sozinha ao terminar. Dá para continuar sem esperar, o que pula o teste. Se o download falhar, a tela oferece tentar de novo ou continuar sem voz, só com digitação.
5. Uma palavra de teste aparece em cada idioma escolhido, com a barra de volume do microfone e o que foi entendido. Errar não tem punição, e dá para trocar de microfone ou pular o teste.
6. Só depois disso os desafios começam a contar.

Trocar de idioma com `/lang language` segue o mesmo caminho: se faltar o modelo, a tela de download aparece. Durante um desafio ou depois de "Continuar sem esperar", uma barra no canto da tela continua mostrando o progresso.

A voz em inglês, alemão e espanhol usa só o Vosk e ainda não foi medida com gravações de nativos, ao contrário do coreano e do japonês. Na digitação, esses idiomas aceitam a escrita sem acento, e o alemão aceita `ae`, `oe`, `ue` e `ss`. O treino do alfabeto só existe para o coreano e o japonês.

## Temas

| Tema | Visual |
|---|---|
| Clássico | Painel escuro translúcido e botões padrão do jogo |
| Inventário | Cinza claro em relevo, como as telas de baú |
| Bosque de Cerejeira | Moldura de madeira rosa e interior ameixa |
| Cidade Antiga | Sculk e ciano, com a barra de tempo em segmentos |
| Hanji | Papel claro, tinta preta e carimbo vermelho |
| Dancheong | Verde, vermelhão e as cinco cores dos templos coreanos |
| Festival Noturno | Índigo com ondas, viga de torii e botões cor de lanterna |

Troque com `/lang theme` ou na tela de configuração, dentro do mundo. O tema marcado já aparece na própria tela, e só fica salvo ao confirmar. Em um mundo próprio, ele fica na pasta do mundo. Em um servidor, fica em `config/langcraft/theme/<endereço>.json`. As texturas são geradas por `tools/generate_theme_sprites.py`, que precisa do Pillow.

## Outros mods

O LangCraft funciona com outros mods sem nenhuma biblioteca de conexão. O ícone e o nome vêm do próprio jogo, então itens e mobs de um mod com tradução no idioma já entram nos desafios e no caderno.

- **Sem tradução:** o item não é sorteado. `/lang missing` cria `config/langcraft/translations/<idioma>.json` com tudo o que falta nos seus idiomas. Preencha `translation`, à mão ou com um tradutor, e entre de novo no mundo. Esse arquivo tem prioridade, então também serve para corrigir uma tradução ruim, e pode ser compartilhado com outros jogadores. No verso do flashcard, essas palavras aparecem como tradução da comunidade.
- **Mobs:** mobs de outros mods com ovo de invocação entram sozinhos nas categorias de sorteio, pelo tipo e pela vida. Para tirar um chefe, use `blockedMobs`. Para desligar, use `includeModMobs`.
- **Fala:** fora do coreano, se o modelo de voz não conhecer uma palavra do nome, o desafio sorteado vira popup. Pedindo por comando, ele continua sendo de fala.
- **Item com defeito:** segure o item e use `/lang block` para ele não ser mais perguntado. `/lang unblock` desfaz, e `/lang block list` mostra os bloqueados. O bloqueio vale para todos os jogadores e fica em `blockedItems`.

## Minigames

| Minigame | O que aparece | Como vencer |
|---|---|---|
| Alerta de fala | Ícone e nome no idioma, no topo da tela | Falar o nome no microfone, ou apertar `Y` e digitar |
| Popup de alternativas | Ícone, nome em português e 4 alternativas | Clicar na escrita certa |
| Pegue na mão | Só o nome no idioma | Segurar aquele item na mão por 1 segundo |

O erro funciona de um jeito diferente em cada minigame:

- **Alerta de fala:** você pode tentar quantas vezes quiser até o tempo acabar, e cada erro dá um aviso visual. Só o fim do tempo causa punição.
- **Popup de alternativas:** a primeira alternativa errada já causa a punição, depois de mostrar a certa em verde.
- **Pegue na mão:** segurar outro item por 1 segundo já causa a punição. A mão vazia e o item que já estava na mão no início não contam.

No combate, os desafios automáticos são só de fala, para nenhum popup ou troca de item atrapalhar a luta. O jogo não tem um estado de combate, então conta como combate ter levado dano de um mob nos últimos 10 segundos (`combatSeconds`). Se a palavra sorteada não puder ser falada, o desafio espera um pouco em vez de virar popup. Os pedidos por comando não mudam.

- **Desafio de item:** se você perder, uma roleta define quanto da pilha é perdido, de 1 item até a pilha inteira. Uma pilha de 1 item é perdida direto, sem roleta.
- **Desafio de mob:** se você perder, o mob perguntado nasce em cima de você, de uma galinha até o Warden (2% de chance).
- **Todo erro custa mais:** além disso, você perde 2 níveis de experiência. Cada erro seguido multiplica esse valor, tira a fatia mais leve da roleta de itens e aumenta a quantidade de mobs, até 3.
- **Roleta de desastre:** a cada 3 erros seguidos, ela substitui a roleta de itens: nenhum nível, sem armadura, sem hotbar, sem metade do inventário, um chefe, a morte ou a perda de todos os itens. Ela também deixa você lento e fraco por 60 segundos, e isso piora a cada desastre da mesma sequência, até 180 segundos e nível III.
- **Roleta diária:** cada erro tem 35% de chance de abrir esta roleta no lugar do castigo comum, no máximo uma vez por dia do jogo. Pode tirar 3 pilhas ou metade dos níveis, trazer 3 monstros, amaldiçoar, desgastar o equipamento ou deixar você com um coração. Há 3% de chance de escapar.
- **Roleta invertida:** a cada 5 acertos seguidos, uma roleta dá itens de uma pilha do seu inventário, de +1 até dobrar a pilha, e você ganha um escudo.
- **Escudos:** cada um cancela o castigo de um erro comum, até um máximo de 3. Eles não seguram as roletas de desastre e diária.
- **Jackpot:** a cada 15 acertos seguidos, uma roleta com diamantes, níveis, maçãs douradas, efeitos bons, escudos, totem, netherita ou maçã encantada.
- **Experiência:** cada acerto dá experiência, mais alta com a sequência e em dobro na fala.

Os dois intervalos só contam enquanto você está livre: param durante um desafio, uma roleta, os 30 segundos de espera depois de cada desafio e a preparação da voz, e também com o jogo pausado. Quando um desafio começa, o outro intervalo é afastado, para os dois não virem em sequência. Sem nenhum item no inventário, o mod tenta de novo a cada 10 segundos, em vez de esperar um intervalo inteiro.

## Títulos

Cada jogador tem um título antes do nome, visível para todos, inclusive na lista de jogadores e no chat. Ele sobe com os desafios vencidos: Novato (0), Aprendiz (25), Estudante (100), Poliglota (250), Tradutor (500) e Mestre das Línguas (1000). Quando alguém sobe de título, todos veem um aviso no chat. `/lang titles` mostra a lista e quanto falta. Os flashcards não contam, porque é o próprio jogador que marca o acerto.

A lista do Tab funciona como ranking de quem está online: mostra o total de desafios vencidos ao lado do nome, com quem venceu mais em cima, e empates em ordem alfabética.

Título e ranking usam os times e a pontuação do Minecraft, com um time automático para cada jogador. Quem já está em outro time, como em um servidor que usa times, fica sem título e fora da ordem, para não atrapalhar. Para desligar os dois, use `showTitles` ou **Títulos e ranking** na configuração.

## Placar

O canto superior esquerdo mostra o título, a sequência atual, o recorde, o total de acertos e o total de erros, salvos por jogador em cada mundo. Para esconder, use **Placar na tela** na configuração ou `showScoreboard`.

## Flashcards

Cada palavra de um desafio terminado entra nos seus flashcards, com a contagem de acertos e erros e a última resposta errada, seja falada, digitada ou clicada.

As palavras são de cada mundo, então um mundo novo começa sem nenhuma. Em um mundo próprio, elas ficam em `saves/<mundo>/langcraft/flashcards.json` e são apagadas com ele. Em um servidor, ficam em `config/langcraft/flashcards/<endereço>.json`.

Para estudar, aperte `K`, use `/lang study` ou abra pela configuração dentro de um mundo. Você vê a palavra, tenta lembrar, mostra a resposta e marca **Acertei** ou **Errei**. As palavras em que você mais erra vêm primeiro, e as marcadas como erradas voltam no fim da rodada. Dá para filtrar por idioma e começar pelo português.

Os flashcards usam repetição espaçada: a rodada só traz as palavras que chegaram na hora, contada em dias reais. Cada acerto seguido afasta a próxima revisão para 1, 3, 7, 14, 30 e 60 dias, e o botão **Acertei** mostra esse prazo. Errar no estudo recomeça a contagem e traz a palavra de volta. Perder um desafio também traz a palavra para agora, voltando um passo, enquanto vencer não muda a data. **Revisar mesmo assim** mostra todas as palavras quando não há nada na hora, sem encurtar os prazos, a não ser que você erre.

O botão **Lista** na tela de estudo, ou `/lang words`, mostra todas as palavras do mundo, das mais recentes para as mais antigas, com a leitura e o significado. Os acertos e erros aparecem separados por tipo de resposta: **Fala**, **Escrita** (digitada na caixa do alerta), **Quiz** e **Mão** (Pegue na mão).

O caderno só mostra os idiomas que você escolheu. As palavras de um idioma que você deixou de estudar ficam guardadas e voltam se você o escolher de novo. Estudar não muda o placar, e as palavras continuam podendo cair nos desafios. Nenhum desafio começa enquanto uma tela de estudo está aberta. Essas telas têm sempre o visual de caderno, não importa o tema escolhido.

Um sininho avisa quando um desafio começa. O caderno faz som de folha ao abrir, ao trocar de tela e ao fechar. Esses sons seguem o volume de interface do jogo.

## Alfabeto

O botão **Alfabeto** na tela de estudo, ou `/lang alphabet`, abre uma tabela com as letras do idioma:

- **Coreano:** consoantes, consoantes duplas, vogais e vogais compostas.
- **Japonês:** hiragana e katakana, cada um com o básico, os sinais (como が e ぱ) e as junções (como きゃ).

Clique em uma letra para ver a leitura e uma dica de pronúncia. A barra de cada letra mostra o quanto ela já foi dominada. **Praticar** faz uma rodada de até 10 perguntas com o grupo aberto, nos dois sentidos, começando pelas letras menos dominadas. Uma letra errada volta no fim da rodada. Não há punição, e o progresso fica salvo em cada mundo.

## Comandos

A cada entrada no mundo, um aviso no chat lembra a tecla do caderno e o `/lang`, que lista os comandos.

`/lang challenge`, `/lang roulette`, `/lang on` e `/lang off` são só para administradores, com permissão de nível 2. Em um mundo próprio, é preciso estar com os comandos (cheats) ligados. `/lang block` e `/lang unblock` também valem para o dono do mundo próprio, mesmo sem cheats.

- `/lang challenge [item|mob] [speak|quiz|hold]`: inicia um desafio agora. `segurar` só funciona com `item` e precisa de um item que não esteja na sua mão.
- `/lang theme`: abre a tela de escolha do tema.
- `/lang language`: abre a tela de escolha do idioma.
- `/lang language <korean|japanese|english|german|spanish|both|all>`: escolhe direto. `ambos` é coreano e japonês. Para outra combinação, use a tela.
- `/lang config`: abre a tela de configuração, a mesma da engrenagem do Mod Menu.
- `/lang study`: abre os flashcards.
- `/lang words`: abre a lista de palavras do mundo.
- `/lang alphabet`: abre o treino do alfabeto.
- `/lang titles`: lista os títulos e mostra quanto falta para o próximo.
- `/lang missing`: lista os itens e mobs sem tradução nos seus idiomas e cria o arquivo para preencher.
- `/lang on [items|mobs]` e `/lang off [items|mobs]`: ligam e desligam os desafios automáticos.
- `/lang block`: o item da mão não é mais perguntado, para todos os jogadores.
- `/lang unblock [item]`: libera o item da mão, ou o id escrito. As sugestões mostram os bloqueados.
- `/lang block list`: lista os itens bloqueados. Clicar em um preenche o comando para liberar.

## Furigana

Os kanjis aparecem com a leitura em hiragana por cima, no alerta e nas alternativas do popup. No chat e nos títulos, a leitura vem entre parênteses, como `石(いし)`. Para desligar, use `showFurigana` na configuração.

As leituras vêm do dicionário Kuromoji, com uma tabela de correções para os nomes do Minecraft em `KanaNormalizer`. A leitura de itens de outros mods pode sair errada. A mesma leitura é usada para avaliar voz e digitação.

## Fonte

O hangul usa a fonte "LangCraft Hangul", uma versão reduzida da Noto Sans KR Bold, e o japonês usa a "LangCraft Japanese", uma versão reduzida da Noto Sans JP Bold (ambas Copyright 2014-2021 Adobe), distribuídas sob a SIL Open Font License 1.1. As duas valem para todo o texto do jogo nesses alfabetos, e não só para o mod. A licença e o aviso de modificação estão em `assets/langcraft/font/`.

## Reconhecimento de voz

O reconhecimento é offline, com [Vosk](https://alphacephei.com/vosk/) e, no coreano, também com [Whisper](https://github.com/ggerganov/whisper.cpp). O mod baixa os modelos para `config/langcraft/models/` depois da escolha do idioma (160 MB para coreano, 48 MB para japonês, e mais espaço depois de extraídos). O microfone só fica aberto durante um alerta de fala.

Vários reconhecedores ouvem ao mesmo tempo, e qualquer um pode acertar: o Vosk com fala livre, o Vosk restrito à resposta e a 30 nomes do jogo e, no coreano, o Whisper, que resiste melhor a ruído e a volume baixo. No coreano, só a palavra vale: `광부 사과` não acerta `사과`. Cada resultado fica registrado no log com o prefixo `[voz]`.

Resultado com gravações de falantes nativos do Lingua Libre, com ruído e volume simulados (palavra aceita / palavra errada aceita):

| Condição | Coreano, 30 gravações | Japonês, 24 gravações |
|---|---|---|
| Limpo | 30 / 1 | 24 / 3 |
| Ruído leve | 26 / 2 | 22 / 3 |
| Ruído forte | 11 / 0 | 13 / 0 |
| Volume baixo | 18 / 0 | 20 / 1 |

Nomes longos ou raros, como 네더라이트 주괴 e ネザライトインゴット, costumam falhar por voz. Nesses casos, use a digitação.

Digitar em coreano ou japonês exige o teclado desse idioma instalado no sistema. Em japonês, a escrita em kana também é aceita.

### Como testar sua voz

1. Em um mundo de teste, use `/lang language korean`.
2. Dê a si mesmo um item de nome simples: `/give @s minecraft:stone 8`.
3. Use `/lang challenge item speak` e espere o alerta mostrar "Ouvindo...".
4. Fale a palavra do alerta. O alerta mostra "Ouvi: ..." com o que foi entendido.
5. Se a resposta ficar perto mas não passar, baixe `voiceSimilarity` em `config/langcraft.json`.
6. Repita com `/lang language japanese`, e com nomes mais longos, como os de ferramentas de diamante.

## Configuração

Com o [Mod Menu](https://modrinth.com/mod/modmenu) instalado, clique em **Mods**, escolha o LangCraft e clique na **engrenagem**. Essa tela troca e testa o microfone e ajusta exigência da fala, furigana, intervalos, tempos e roleta invertida. Tudo é salvo ao fechar. Em um servidor dedicado, os intervalos, os tempos e a roleta seguem o arquivo do servidor.

O Mod Menu é opcional. Sem ele, ou para ajustes que a tela não mostra, como as chances das roletas e dos mobs, edite `config/langcraft.json` e reinicie o jogo.

| Ajuste | Padrão | Significado |
|---|---|---|
| `itemIntervalMinSeconds`, `itemIntervalMaxSeconds` | 300, 600 | Intervalo entre desafios de item |
| `mobIntervalMinSeconds`, `mobIntervalMaxSeconds` | 480, 900 | Intervalo entre desafios de mob |
| `speakSeconds`, `quizSeconds`, `holdSeconds` | 30, 20, 30 | Tempo de cada minigame |
| `combatSeconds` | 10 | Por quanto tempo depois de um dano de mob os desafios automáticos são só de fala |
| `microphone` | vazio | Nome do microfone. Vazio usa o padrão do sistema |
| `voiceSimilarity` | 0.75 | Similaridade mínima para aceitar a fala, de 0 a 1 |
| `showFurigana` | `true` | Mostra a leitura em hiragana sobre os kanjis |
| `showScoreboard` | `true` | Mostra o placar no canto da tela |
| `showTitles` | `true` | Mostra o título antes do nome e o ranking na lista do Tab |
| `voiceModelBaseUrl` | site do Vosk | Endereço de onde baixar os modelos de voz |
| `rouletteAutoSpinSeconds` | 10 | Tempo até a roleta girar sozinha |
| `rouletteSlices` | ver abaixo | Fatias das duas roletas. `percent: 0` significa 1 item |
| `rewardRouletteEnabled`, `rewardStreak` | `true`, 5 | Liga a roleta de prêmios e define a sequência exigida |
| `rewardFallbackLevels` | 5 | Níveis dados quando não há item empilhável |
| `rewardBlockedItems` | vazio | Itens que a roleta de prêmios não pode dar, como `minecraft:netherite_block` |
| `lossLevelsPerError` | 2 | Níveis perdidos por erro, vezes os erros seguidos |
| `disasterDebuffSeconds` | 60 | Segundos de lentidão e fraqueza do desastre, vezes os desastres da mesma sequência |
| `disasterStreak`, `disasterSlices` | 3, ver acima | Erros seguidos da roleta de desastre e suas fatias. Zero desliga |
| `dailyRouletteEnabled`, `dailySlices` | `true`, ver acima | Liga a roleta diária e define suas fatias |
| `dailyChancePercent` | 35 | Chance de um erro abrir a roleta diária, se ela ainda não saiu naquele dia |
| `jackpotStreak`, `jackpotSlices` | 15, ver acima | Acertos seguidos do jackpot e suas fatias. Zero desliga |
| `winExperience` | 10 | Experiência base de cada acerto. Zero desliga |
| `rewardShields`, `maxShields` | 1, 3 | Escudos ganhos com a sequência e o máximo guardado |
| `mobTiers` | ver abaixo | Categorias de mob, com chance e lista |
| `includeModMobs` | `true` | Coloca os mobs de outros mods nas categorias |
| `blockedMobs` | vazio | Mobs que nunca são sorteados, como `botania:doppleganger` |
| `blockedItems` | vazio | Itens que nunca são perguntados. `/lang block` preenche com o item da mão |

Chances padrão das roletas de itens: 1 item 15%, 25% da pilha 25%, 50% da pilha 25%, 75% da pilha 20%, pilha inteira 15%.

Chances padrão de mob: inofensivos 40%, neutros 25%, hostis 22%, perigosos 11%, Warden 2%. Na dificuldade Pacífico, só entram mobs que não sejam hostis.

## Desenvolvimento

É preciso ter o Java 21, indicado em `JAVA_HOME`. O ForgeGradle também usa o Java 25 para preparar o Minecraft, e o Gradle baixa essa versão sozinho se ela faltar.

O código fica em quatro módulos. `common` tem tudo o que é igual nos três loaders, e `fabric`, `neoforge` e `forge` têm só a ligação com cada um, por meio da interface `Platform` e dos métodos de `LangCraftClient`. Cada loader compila o código de `common` junto com o seu.

Gera os três `.jar` e roda os testes:

```bash
./gradlew build
```

Roda só os testes unitários, que ficam em `common`:

```bash
./gradlew :common:test
```

Abre o jogo em cada loader:

```bash
./gradlew :fabric:runClient
```

```bash
./gradlew :neoforge:runClient
```

```bash
./gradlew :forge:runClient
```

A bateria que abre o jogo e percorre os cenários existe só no Fabric, porque usa a Fabric API:

```bash
./gradlew :fabric:runClientGameTest
```

```bash
./gradlew :fabric:runProductionGameTest
```

`runClientGameTest` roda no ambiente de desenvolvimento, com capturas de tela em `fabric/build/run/clientGameTest/screenshots`, e `runProductionGameTest` usa o `.jar` final. Eles também baixam os modelos de voz e abrem o microfone por alguns instantes.

Para medir o reconhecimento, coloque amostras em `fabric/build/run/clientGameTest/voice-samples/<idioma>/`, com arquivos `0.wav`, `1.wav` e assim por diante, mais um `expected.txt` com uma resposta por linha. O `runClientGameTest` passa a usá-las.
