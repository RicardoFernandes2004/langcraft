# Changelog

## 1.0.2

- Two new speaking challenges. Say the name of a biome the first time you enter it, and the name of an item the first time it shows up in your inventory. Items you already have and the biome you are in when you update do not count.
- Turn each challenge on or off for yourself in the config screen, inside a world.
- Open the config screen from the pause menu, with the LangCraft button in the top corner.
- Item tooltips show the name in the languages you study, below the item name. The language your game is in is skipped. Turn it off with "Names in tooltips".
- `/lang block` now blocks an item only for you. Admins block it for everyone with `/lang admin block`.
- Names that a mod ships broken, with question marks, unreadable characters or the raw key, are no longer asked, shown as answers or used in tooltips. They show up in `/lang missing` so you can fix them.
- `/lang challenge biome` asks about the biome you are in.

## 1.0.1

- No pop-ups in the middle of a fight. For 10 seconds after a mob hits you, automatic challenges only ask you to speak, never to click an answer or hold an item. Change the time with `combatSeconds`.
- Block an item that breaks the challenges, such as one from another mod with a wrong name. Hold it and use `/lang block`. Undo with `/lang unblock`, and see the list with `/lang block list`. Admins can use these commands, and so can the world owner in singleplayer without cheats.
- The Daily and Jackpot roulettes no longer show slice names on top of each other.
- Fixed "1 experience levels" in the message for lost levels.

## 1.0.0

First release, for Minecraft 1.21.11 on Fabric, NeoForge and Forge, and Minecraft 1.20.1 on Fabric and Forge.

### Learning

- Practice Korean, Japanese, English, German and Spanish, alone or mixed.
- Three challenges with the items in your inventory: speak the name, pick it from a list, or put it in your hand.
- Challenges about mobs, including mobs from other mods.
- Offline speech recognition. Your voice never leaves your computer.
- Reading hints in hiragana over Japanese kanji.
- A study notebook with spaced repetition, a word list with results by answer type, and alphabet training.
- Translations for items from other mods, through `/lang missing`.

### Consequences

- Lose a challenge and you lose items and experience levels, or a mob spawns on top of you. Each mistake in a row costs more.
- Every 3 mistakes in a row open the Disaster roulette, with slowness and weakness that get worse each time.
- Once per game day, a mistake may open the Daily roulette instead.
- Every 5 correct answers in a row give you a reward roulette and a shield that cancels the next common punishment.
- Every 15 correct answers in a row open the Jackpot roulette.
- Correct answers give experience, doubled when you speak.

### Everything else

- Seven visual themes, chosen for each world.
- Titles above player names, and a ranking on the Tab list.
- Commands under `/lang`, with the challenge, roulette and on/off commands limited to admins.
