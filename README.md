# Idle Fantasy

A free and open source offline idle RPG for Android. Train skills, fight in dungeons, craft gear, and complete quests. No internet connection, account, or ads required.

## Features

- **18 trainable skills**: Mining, Fishing, Woodcutting, Firemaking, Agility, Runecrafting, Smithing, Cooking, Fletching, Crafting, Prayer, Attack, Strength, Defence, Ranged, Magic, Hitpoints
- **Session-based training**: Start a session and close the app. Come back up to an hour later to collect your loot and XP
- **12 dungeons**: Fight solo bosses and enemies to earn combat XP, coins, and rare drops. Melee, Ranged, and Magic combat styles supported
- **Crafting system**: Smelt bars, cook fish, fletch bows and arrows, craft jewellery
- **Prayer**: Bury bones from combat to earn Prayer XP
- **Equipment system**: Equip weapons, armour, tools, and jewellery to boost stats and efficiency
- **100+ quests** spanning all skills with XP rewards
- **Pet system**: Rare pet drops provide permanent XP boosts
- **Achievements**: Track milestones across levelling, combat, quests, and collections
- **NPC Shop**: Buy supplies and sell gathered resources for coins

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite) |
| Background work | WorkManager |
| JSON parsing | kotlinx.serialization |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Notifications | NotificationCompat |
| Localization | Android string resources (Weblate-compatible) |

No Google Play Services dependency. F-Droid compatible.

## Building from Source

**Requirements:** Android Studio Hedgehog or newer, JDK 17+, Android SDK 34

```bash
git clone https://github.com/tristinbaker/IdleFantasy.git
cd IdleFantasy
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Translating

See [TRANSLATING.md](TRANSLATING.md) for instructions on adding or improving translations. The project uses standard Android string resources (`res/values/strings.xml`) for easy contribution via Weblate.

## Contributing

Bug reports and pull requests are welcome. I especially welcome PRs for bug fixes that you may find. Please open an issue before starting large changes so we can discuss the approach. I appreciate any and all suggestions for improving this app so more people can enjoy it to the fullest extent.

## License

GPL-3.0. See [LICENSE](LICENSE) for details.
