# LinguaCraft

Mod Fabric para Minecraft 1.21.11 que treina vocabulário coreano e japonês com os itens do seu inventário e com os mobs do jogo. Quem perde o desafio perde itens ou recebe um mob em cima da cabeça. Quem mantém uma sequência de acertos gira uma roleta de prêmios.

## Instalação

1. Instale o [Fabric Loader](https://fabricmc.net/use/installer/) para o Minecraft 1.21.11.
2. Baixe a [Fabric API](https://modrinth.com/mod/fabric-api) para 1.21.11 e coloque na pasta `mods`.
3. Coloque `linguacraft-1.0.0.jar` (em `build/libs`) na mesma pasta.
4. Opcional: coloque também o [Mod Menu](https://modrinth.com/mod/modmenu) 17.0.1, que adiciona o botão **Mods** e a engrenagem da configuração.
5. Abra o jogo com o perfil Fabric 1.21.11.

Funciona em Windows e em Linux de 64 bits (x86-64). No Linux, a opção recomendada é **Entrada do sistema**, que segue o microfone escolhido nas configurações do seu desktop. Os microfones vêm do PulseAudio ou do PipeWire, com os mesmos nomes das configurações do sistema, por meio dos comandos `pactl` e `parec` (pacote `pulseaudio-utils` ou `libpulse`). Sem esses comandos, como em um launcher Flatpak sem acesso a eles, o mod usa o ALSA, que só mostra as placas de som, com nomes como `[plughw:0,0]`.

Em servidores, o mod precisa estar no servidor e no jogo de cada jogador. Jogadores sem o mod não recebem desafios.

## Primeira entrada no mundo

1. Uma tela pergunta qual idioma você quer praticar: coreano, japonês ou ambos. A escolha fica salva por jogador em cada mundo.
2. Outra tela lista os microfones do computador, com o padrão do sistema em primeiro lugar. Se o microfone escolhido sumir depois, o mod usa o padrão.
3. Se o modelo de voz ainda não foi baixado, uma tela mostra o progresso e fecha sozinha ao terminar. Dá para continuar sem esperar, o que pula o teste. Se o download falhar, a tela oferece tentar de novo ou continuar sem voz, só com digitação.
4. Uma palavra de teste aparece em cada idioma escolhido, com a barra de volume do microfone e o que foi entendido. Errar não tem punição, e dá para trocar de microfone ou pular o teste.
5. Só depois disso os desafios começam a contar.

Trocar de idioma com `/lingua idioma` segue o mesmo caminho: se faltar o modelo, a tela de download aparece.

## Minigames

| Minigame | O que aparece | Como vencer |
|---|---|---|
| Alerta de fala | Ícone e nome no idioma, no topo da tela | Falar o nome no microfone, ou apertar `Y` e digitar |
| Popup de alternativas | Ícone, nome em português e 4 alternativas | Clicar na escrita certa |
| Pegue na mão | Só o nome no idioma | Segurar aquele item na mão por 1 segundo |

Você pode tentar quantas vezes quiser até o tempo acabar, e cada erro dá um aviso visual. Só o fim do tempo causa punição.

- **Desafio de item:** se o tempo acabar, uma roleta define quanto da pilha é perdido, de 1 item até a pilha inteira. Uma pilha de 1 item é perdida direto, sem roleta.
- **Desafio de mob:** se o tempo acabar, o mob perguntado nasce em cima de você, de uma galinha até o Warden (2% de chance).
- **Roleta invertida:** a cada 5 acertos seguidos, uma roleta dá itens de uma pilha do seu inventário, de +1 até dobrar a pilha.

## Comandos

- `/lingua desafio [item|mob] [fala|quiz|segurar]`: inicia um desafio agora. `segurar` só funciona com `item` e precisa de um item que não esteja na sua mão.
- `/lingua idioma <coreano|japones|ambos>`: escolhe o idioma.
- `/lingua ligar [itens|mobs]` e `/lingua desligar [itens|mobs]`: ligam e desligam os desafios automáticos.

## Furigana

Os kanjis aparecem com a leitura em hiragana por cima, no alerta e nas alternativas do popup. No chat e nos títulos, a leitura vem entre parênteses, como `石(いし)`. Para desligar, use `showFurigana` na configuração.

As leituras vêm do dicionário Kuromoji, com uma tabela de correções para os nomes do Minecraft em `KanaNormalizer`. A leitura de itens de outros mods pode sair errada. A mesma leitura é usada para avaliar voz e digitação.

## Fonte

O hangul usa a fonte "LinguaCraft Hangul", uma versão reduzida da Nanum Gothic Bold (Copyright 2011 NHN Corporation), distribuída sob a SIL Open Font License 1.1. A licença e o aviso de modificação estão em `assets/linguacraft/font/`.

## Reconhecimento de voz

O reconhecimento é offline, com [Vosk](https://alphacephei.com/vosk/) e, no coreano, também com [Whisper](https://github.com/ggerganov/whisper.cpp). O mod baixa os modelos para `config/linguacraft/models/` depois da escolha do idioma (160 MB para coreano, 48 MB para japonês, e mais espaço depois de extraídos). O microfone só fica aberto durante um alerta de fala.

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

1. Em um mundo de teste, use `/lingua idioma coreano`.
2. Dê a si mesmo um item de nome simples: `/give @s minecraft:stone 8`.
3. Use `/lingua desafio item fala` e espere o alerta mostrar "Ouvindo...".
4. Fale a palavra do alerta. O alerta mostra "Ouvi: ..." com o que foi entendido.
5. Se a resposta ficar perto mas não passar, baixe `voiceSimilarity` em `config/linguacraft.json`.
6. Repita com `/lingua idioma japones`, e com nomes mais longos, como os de ferramentas de diamante.

## Configuração

Com o [Mod Menu](https://modrinth.com/mod/modmenu) instalado, clique em **Mods**, escolha o LinguaCraft e clique na **engrenagem**. Essa tela troca e testa o microfone e ajusta exigência da fala, furigana, intervalos, tempos e roleta invertida. Tudo é salvo ao fechar. Em um servidor dedicado, os intervalos, os tempos e a roleta seguem o arquivo do servidor.

O Mod Menu é opcional. Sem ele, ou para ajustes que a tela não mostra, como as chances das roletas e dos mobs, edite `config/linguacraft.json` e reinicie o jogo.

| Ajuste | Padrão | Significado |
|---|---|---|
| `itemIntervalMinSeconds`, `itemIntervalMaxSeconds` | 300, 600 | Intervalo entre desafios de item |
| `mobIntervalMinSeconds`, `mobIntervalMaxSeconds` | 480, 900 | Intervalo entre desafios de mob |
| `speakSeconds`, `quizSeconds`, `holdSeconds` | 30, 20, 30 | Tempo de cada minigame |
| `microphone` | vazio | Nome do microfone. Vazio usa o padrão do sistema |
| `voiceSimilarity` | 0.75 | Similaridade mínima para aceitar a fala, de 0 a 1 |
| `showFurigana` | `true` | Mostra a leitura em hiragana sobre os kanjis |
| `voiceModelBaseUrl` | site do Vosk | Endereço de onde baixar os modelos de voz |
| `rouletteAutoSpinSeconds` | 10 | Tempo até a roleta girar sozinha |
| `rouletteSlices` | ver abaixo | Fatias das duas roletas. `percent: 0` significa 1 item |
| `rewardRouletteEnabled`, `rewardStreak` | `true`, 5 | Liga a roleta de prêmios e define a sequência exigida |
| `rewardFallbackLevels` | 5 | Níveis dados quando não há item empilhável |
| `rewardBlockedItems` | vazio | Itens que a roleta de prêmios não pode dar, como `minecraft:netherite_block` |
| `mobTiers` | ver abaixo | Categorias de mob, com chance e lista |

Chances padrão das roletas: 1 item 25%, 25% da pilha 25%, 50% da pilha 20%, 75% da pilha 15%, pilha inteira 15%.

Chances padrão de mob: inofensivos 40%, neutros 25%, hostis 22%, perigosos 11%, Warden 2%. Na dificuldade Pacífico, só entram mobs que não sejam hostis.

## Desenvolvimento

É preciso ter o Java 21, indicado em `JAVA_HOME`.

```bash
./gradlew build
```

```bash
./gradlew test
```

```bash
./gradlew runClient
```

```bash
./gradlew runClientGameTest
```

```bash
./gradlew runProductionGameTest
```

Os dois últimos comandos abrem o jogo e percorrem os cenários automaticamente: `runClientGameTest` no ambiente de desenvolvimento, com capturas de tela em `build/run/clientGameTest/screenshots`, e `runProductionGameTest` com o `.jar` final. Eles também baixam os modelos de voz e abrem o microfone por alguns instantes.

Para medir o reconhecimento, coloque amostras em `build/run/clientGameTest/voice-samples/<idioma>/`, com arquivos `0.wav`, `1.wav` e assim por diante, mais um `expected.txt` com uma resposta por linha. O `runClientGameTest` passa a usá-las.
