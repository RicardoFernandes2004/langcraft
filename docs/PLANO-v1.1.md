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

## 2. Alemão e o cadastro de idiomas

Os nomes vêm do `de_de` do próprio jogo, e a fonte já tem ä, ö, ü e ß. Coreano e japonês estão escritos direto em vários arquivos, e a opção "Ambos" só prevê dois idiomas.

| Parte | O que muda | Trabalho |
|---|---|---|
| Cadastro de idiomas | Reunir em um só lugar o código, o modelo, o tamanho e as regras de cada idioma | Médio, só uma vez |
| Opção "Ambos" | Virar escolha de vários idiomas, mantendo os mundos salvos com `both` | Médio |
| Telas e comandos | Botão na tela de idioma, `/lingua idioma alemao` e textos | Baixo |
| Digitação | Aceitar `ae` no lugar de `ä` e `ss` no lugar de `ß` | Baixo |
| Voz | Baixar o modelo do Vosk em alemão, medir com gravações de nativos e ajustar | Incerto |

O maior risco é a voz. Nomes longos, como `Diamantspitzhacke`, podem não existir no modelo pequeno do Vosk. O teste de medição (`VoiceBenchmarkTest`) já existe, então a decisão de somar ou não o Whisper sai das medições.

Com o cadastro pronto, espanhol, francês e italiano ficam mais baratos. Chinês precisaria de uma ajuda de leitura, como o pinyin. Árabe é o mais difícil, por ser escrito da direita para a esquerda.
