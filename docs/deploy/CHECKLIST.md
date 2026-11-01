# Publicação

Passo a passo para publicar no Modrinth e no CurseForge. A publicação é manual e usa a sua conta.

## Antes de publicar

- [ ] Testar no jogo cada combinação da tabela abaixo. NeoForge, Forge e 1.20.1 nunca foram abertos em um cliente de verdade.
- [ ] Testar a voz em cada loader, em coreano e em japonês.
- [ ] Testar em um servidor dedicado, com um jogador que tem o mod e outro sem.
- [ ] Ler as regras de cada site sobre mods que baixam arquivos. O mod baixa modelos de voz no primeiro uso, e a descrição já avisa isso.
- [ ] Tirar capturas de tela: um desafio de fala, o popup, a roleta de desastre, o caderno e a tela de temas.

## Arquivos

Gere os arquivos de cada versão com `./gradlew build`, na branch indicada.

| Minecraft | Loader | Branch | Arquivo |
|---|---|---|---|
| 1.21.11 | Fabric | `main` | `fabric/build/libs/langcraft-fabric-1.21.11-1.0.0.jar` |
| 1.21.11 | NeoForge | `main` | `neoforge/build/libs/langcraft-neoforge-1.21.11-1.0.0.jar` |
| 1.21.11 | Forge | `main` | `forge/build/libs/langcraft-forge-1.21.11-1.0.0.jar` |
| 1.20.1 | Fabric | `mc/1.20.1` | `fabric/build/libs/langcraft-fabric-1.20.1-1.0.0.jar` |
| 1.20.1 | Forge | `mc/1.20.1` | `forge/build/libs/langcraft-forge-1.20.1-1.0.0.jar` |

Nunca envie o arquivo terminado em `-slim`, que não leva as bibliotecas de voz.

## Dados da página

| Campo | Valor |
|---|---|
| Nome | LangCraft |
| Endereço | `langcraft` |
| Resumo | Learn Korean, Japanese, English, German and Spanish with your inventory. Lose a challenge and you pay for it. |
| Descrição | `docs/deploy/description.md` |
| Ícone | `common/src/main/resources/assets/langcraft/icon.png` |
| Categorias | Game Mechanics, Utility |
| Lado | Cliente e servidor, obrigatório nos dois |
| Licença | MIT |
| Código | https://github.com/RicardoFernandes2004/linguacraft |
| Versão | 1.0.0, tipo Release |
| Novidades | Seção 1.0.0 de `CHANGELOG.md` |
| Dependências no Fabric | Fabric API obrigatória, Mod Menu opcional |

## Depois de publicar

- [ ] Criar uma tag `v1.0.0` no GitHub.
- [ ] O endereço do código na página só funciona para os outros se o repositório for público.
