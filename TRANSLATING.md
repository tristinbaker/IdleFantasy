# Translating Fantasy Idler

Thank you for helping translate Fantasy Idler! Translations are managed through
[Weblate](https://weblate.org) — no Git knowledge required.

---

## How to contribute

1. Visit the project on Weblate (link to be added once the instance is set up).
2. Select your language (or request a new one).
3. Translate strings in the browser — Weblate saves automatically.
4. Your translations are reviewed and merged into the game periodically.

---

## File structure

Strings are split into thematic files so you can focus on what you know:

| File | Contents |
|---|---|
| `strings.xml` | Core UI: buttons, labels, error messages, settings |
| `strings_notifications.xml` | Push notification titles and body text |
| `strings_skills.xml` | Skill names and descriptions |
| `strings_items.xml` | Item names and descriptions |
| `strings_quests.xml` | Quest names, descriptions, and objectives |
| `strings_enemies.xml` | Enemy names, dungeon names, boss names |
| `strings_game.xml` | In-game messages: level-up text, session summaries |

You do not need to translate all files — partial translations are welcome.

---

## Format arguments

Many strings contain `%1$s`, `%1$d`, `%2$s` etc. These are **positional placeholders**
that get filled in at runtime (e.g. a skill name, a number).

- **Do** keep all placeholders in your translation.
- **Do** reorder them if your language's grammar requires it (that's why they're numbered).
- **Do not** change the number or type of a placeholder (e.g. `%1$s` must stay a string).

Example:
```
English:  "Your %1$s session has finished."
French:   "Ta session de %1$s est terminée."
Japanese: "%1$sのセッションが終了しました。"
```

---

## Translator notes

Strings with `<!-- Translator note: ... -->` comments contain guidance specific to
that string — please read them before translating.

---

## Game glossary

To keep terminology consistent, please follow these translations if your language
has established equivalents for idle RPG terminology:

| English term | Notes |
|---|---|
| Session | A 1-hour activity period |
| XP / Experience | The idle RPG progression currency |
| Skill | One of the 18 trainable skills (Mining, Fishing, etc.) |
| Dungeon | A solo combat zone |
| Boss | A powerful unique enemy at the end of a dungeon or solo encounter |
| Arena | Where the player fights NPC challengers |
| Patch | A farming plot |
| Pet | A companion animal that grants an XP bonus |

---

## Questions?

Open an issue on the project repository.
