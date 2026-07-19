# Getting Started - Game Contributions

Great! You want to contribute to the game and want to know how you can get started. This page tells you how you can do so and provide good, meaningful contributions to the game itself.

Want a simpler task? {wiki_contribution_link} is also a great way to learn your way around the codebase and figure out how things work, explaining things for new players.

{{table_of_contents}}

## Ways to contribute

### Squashing bugs

As the game develops, bugs are bound to pop up and starting here is a great place to get familiar with the codebase as you'll slowly learn how everything works. Generally, the process you should follow is:

1. Find a bug that you want to fix. This involves going into the [issues](https://github.com/tristinbaker/IdleFantasy/issues) and finding an issue that you think you might be able to work on. Generally starting small is a good idea.
2. Assign yourself to the issue or comment saying that you'd like to fix this. This is an important step to avoid having multiple people work on the same issue at the same time. Also, if you have an idea, it's a good idea to specify how long you think you'll take. This helps others get an idea as to when it should roughly be finished and also when they should potentially look at doing it themselves if you have issues, for example.
3. Once the issue is fixed, make sure to sync the fork with the upstream branch - resolving any merge conflicts. You should aim to keep your changes as small as possible to fix the bug so as to prevent any other bugs from appearing.
4. Test the code with Android Studio or similar to make sure the issue is fixed; go back to step 3 especially if syncing the fork caused any issues to try and figure out what went wrong.
5. Once your fork is fixed, and you've tested to make sure the code works, create a pull request and explain what you've changed, referencing the issue that you're solving. Sometimes you might find some changes need to be made, in which case you can make the changes and redo any checks/syncs.

If you find that a bug is much harder to solve than you expected, that's normal and not a problem. Simply mention on the issue that you're having issues and people can help out - if you need, you can also pass it on to another community member.

Also, it's important to remain close to the [KISS principle](https://en.wikipedia.org/wiki/KISS_principle). Changes while fixing a bug should ideally be as minimal as possible to avoid inadvertently adding new bugs. To do this, you'll likely need to spend most of your time analysing what's causing the bug to ensure you understand the cause. Using AI is not discouraged, but it's worthwhile noting that AI systems sometimes ignore this principle and make substantial changes to fix bugs which could be solved by changing a few lines. As such, you should ensure you understand what's being changes and try to minimise any extraneous modifications.

### Developing new features

Developing new features is a fun way to add to the game and see the changes you've made as you play. However, following the process is highly recommended to avoid spending several days coding up a great new feature only to find that there are some fundamental changes necessary or that it may not match the intended direction of the game.

If you want to develop a new feature you should follow the below steps:

1. Create a new discussion topic in [discussions](https://github.com/tristinbaker/IdleFantasy/discussions). This ensures that the community is able to chip in and provide feedback before you start development. If this is a significant new feature, you could consider adding some example designs of what you're thinking to help provide more clarity.
2. Create a new fork and start working on the project. If this is a large feature, consider updating the discussion with how you're progressing although we don't necessarily need a day-by-day update.
3. Just like with squashing bugs, once you've finished working on your feature, you should sync your fork with the upstream branches and resolve any potential merge conflicts.
4. Then, once all the merge conflicts have been sorted, you can test to ensure the feature still works properly.
5. Finally, once you've tested and synced your fork, you can create a pull request, explaining what you've added and provide a reference to the relevant discussion.

It's quite common while developing an idea that you come up with lots of additional changes and revisions. If these are large, you should consider updating the discussion topic so it properly reflects the idea in full.

## Building and compiling

To get started and build the game, you'll need to install [Android Studio](https://developer.android.com/studio). Once done, you'll need to have the following prerequisites:

* JDK 17+
* Android SDK 34

Then in the terminal run:

```bash
git clone https://github.com/tristinbaker/IdleFantasy.git
cd IdleFantasy
./gradlew :app:assembleDebug
```

You can then check out the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
