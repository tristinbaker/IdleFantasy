# Idle Fantasy

**Set your hero to work. Close the app. Come back to loot.**

A free, open-source offline idle RPG for Android. No internet connection, no account, no ads.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="170" alt="Home screen">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="170" alt="Dungeons">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="170" alt="Combat session">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="170" alt="Profile">
</p>

## How it works

Pick a skill or dungeon, start a session, then put your phone down. Your hero keeps training for up to an hour while the app is closed. Come back whenever you want to collect your XP and loot, then send them back out. There is no stamina bar, no energy system, and nothing that pressures you to stay in the app.

## Skills

Train **20 skills** at your own pace:

- **Gathering**: Mine ores, catch fish, chop logs, tend crops, run agility courses
- **Crafting**: Smelt bars, cook food, fletch bows and arrows, craft jewellery, burn logs for Prayer XP
- **Combat**: Fight in 12 dungeons with Melee, Ranged, or Magic. Each style levels its own skill tree

Better equipment means faster gathering and surviving tougher dungeons. Craft your own gear or buy it from the Shop.

## Combat and dungeons

Explore 12 dungeons from the starter Farm all the way to late-game Fortress Ruins and beyond. Each dungeon has its own enemy roster, difficulty rating, and potential drops. Before you go in, the game tells you how your current gear stacks up.

## Quests

Over **100 quests** span all skills. Daily quests reset every morning for a quick goal to aim at. Long-term Combat and Gathering quests track cumulative progress over many sessions. Completing quests earns XP rewards.

## Pets and the Inn

Rare enemies drop **collectible pets** that grant permanent passive XP bonuses. The **Inn** lets you hire workers who can queue up sessions on your behalf, and buy food to keep your fighter alive longer in tougher dungeons.

## Getting the app

[Download on F-Droid](https://f-droid.org/packages/com.fantasyidler/)

Or grab the latest APK from the [Releases page](https://github.com/tristinbaker/IdleFantasy/releases).

## Translating

The game is available in English, German, French, Spanish, and Turkish. Translations live in standard Android string resource files and are Weblate-compatible. See [TRANSLATING.md](TRANSLATING.md) to add a new language or improve an existing one.

## Contributing

Bug reports and pull requests are welcome. Open an issue before starting large changes so the approach can be discussed first.

See [CONTRIBUTORS.md](CONTRIBUTORS.md) for a full list of contributors.

---

## For developers

**Language:** Kotlin  
**UI:** Jetpack Compose + Material 3  
**Database:** Room (SQLite)  
**Background work:** WorkManager  
**JSON parsing:** kotlinx.serialization  
**Architecture:** MVVM + Repository  
**Dependency injection:** Hilt  
**Notifications:** NotificationCompat  
**Localization:** Android string resources (Weblate-compatible)  

No Google Play Services dependency. F-Droid compatible.

### Building from source

Requirements: Android Studio Hedgehog or newer, JDK 17+, Android SDK 34

```bash
git clone https://github.com/tristinbaker/IdleFantasy.git
cd IdleFantasy
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPL-3.0. See [LICENSE](LICENSE) for details.
