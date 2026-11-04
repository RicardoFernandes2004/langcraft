# Publicação

Passo a passo para publicar no Modrinth e no CurseForge. Depois da primeira configuração, publicar uma versão nova é só criar uma release no GitHub.

## Publicação automática

O workflow `.github/workflows/publish.yml` roda quando uma release é publicada no GitHub. Ele gera os cinco arquivos e envia cada um ao Modrinth, ao CurseForge e à própria release, com as novidades tiradas de `CHANGELOG.md`.

### Configurar uma vez

Em **Settings > Secrets and variables > Actions** do repositório:

| Onde | Nome | Valor |
|---|---|---|
| Secrets | `MODRINTH_TOKEN` | Token criado em modrinth.com, em **Settings > PATs**, com a permissão **Create versions** |
| Secrets | `CURSEFORGE_TOKEN` | Token criado em authors.curseforge.com, em **API tokens** |
| Variables | `MODRINTH_ID` | Id do projeto no Modrinth, em **⋮ > Copy ID** na página do projeto, ou o endereço `langcraft` |
| Variables | `CURSEFORGE_ID` | Número em **Project ID**, na lateral da página do projeto no CurseForge |

Um site sem token fica de fora, então dá para começar só com um deles.

### A cada versão

1. Trocar `version` em `gradle.properties` nas duas branches, `main` e `mc/1.20.1`, e enviar as duas.
2. Escrever a seção `## <versão>` no `CHANGELOG.md` da `main`.
3. No GitHub, criar uma release com a tag `v<versão>`, como `v1.0.2`, apontando para a `main`, e publicar.
4. Acompanhar em **Actions**. Se a versão das duas branches não bater com a tag, ou se faltar a seção no changelog, nada é enviado.

Se o envio falhar depois de gerar os arquivos, dá para rodar de novo em **Actions > Publicar > Run workflow**, com a tag da release. Antes, apague o que já tiver subido. O Modrinth recusa um arquivo repetido, e no CurseForge ele apareceria duas vezes.

## Antes de publicar

- [ ] Testar no jogo cada combinação da tabela abaixo. NeoForge, Forge e 1.20.1 nunca foram abertos em um cliente de verdade.
- [ ] Testar a voz em cada loader, em coreano e em japonês.
- [ ] Testar em um servidor dedicado, com um jogador que tem o mod e outro sem.
- [ ] Ler as regras de cada site sobre mods que baixam arquivos. O mod baixa modelos de voz no primeiro uso, e a descrição já avisa isso.
- [ ] Tirar capturas de tela: um desafio de fala, o popup, a roleta de desastre, o caderno e a tela de temas.

## Arquivos

Para publicar à mão, gere os arquivos de cada versão com `./gradlew build`, na branch indicada.

| Minecraft | Loader | Branch | Arquivo |
|---|---|---|---|
| 1.21.11 | Fabric | `main` | `fabric/build/libs/langcraft-fabric-1.21.11-1.0.0.jar` |
| 1.21.11 | NeoForge | `main` | `neoforge/build/libs/langcraft-neoforge-1.21.11-1.0.0.jar` |
| 1.21.11 | Forge | `main` | `forge/build/libs/langcraft-forge-1.21.11-1.0.0.jar` |
| 1.20.1 | Fabric | `mc/1.20.1` | `fabric/build/libs/langcraft-fabric-1.20.1-1.0.0.jar` |
| 1.20.1 | Forge | `mc/1.20.1` | `forge/build/release/langcraft-forge-1.20.1-1.0.0.jar` |

Nunca envie o arquivo terminado em `-slim`, que não leva as bibliotecas de voz. Na 1.20.1, os outros arquivos de `forge/build/libs` também não servem.

## Dados da página

| Campo | Valor |
|---|---|
| Nome | LangCraft |
| Endereço | `langcraft` |
| Resumo | Learn Korean, Japanese, English, German and Spanish with your inventory. Lose a challenge and you pay for it. |
| Descrição | `docs/deploy/description.md` |
| Ícone | `docs/deploy/images/icon.png` |
| Banner | `docs/deploy/images/banner.png`, como imagem de destaque da galeria ou no topo da descrição |
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
