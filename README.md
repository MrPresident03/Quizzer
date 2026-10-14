# Quizzer

[![Android CI](https://github.com/MrPresident03/Quizzer/actions/workflows/android.yml/badge.svg)](https://github.com/MrPresident03/Quizzer/actions/workflows/android.yml)

A trivia app for Android built with Kotlin, Jetpack Compose and Room.

## Features

- **Questions that don't repeat.** The app remembers what you've seen and always serves unseen questions first. Online it uses Open Trivia DB with a session token; everything fetched is cached so the offline bank keeps growing. 399 hand-checked questions ship with the app across 20 topics, plus flag quizzes (185 countries) and capital-city quizzes (98 countries).
- **Learn from every answer.** After each answer you see why it's right, and a **Next** button lets you move on at your own pace.
- **Review your mistakes.** Every question you miss is saved and comes back in a review session until you get it right.
- **Shared Daily Challenge.** Everyone gets the same 10 questions each day (easy → hard, works offline). Share your score as a 🟩🟥 grid without spoiling the answers.
- **Topic mastery.** See your accuracy per topic and drill your weakest one in a tap.
- **Quick Play.** One tap on the home screen starts a quiz with your last practice settings.
- **Progression.** XP, levels, daily streaks (play 7 days in a row to earn a streak freeze), 20 achievements and four themes. No shop or currency: lifelines (50/50, skip, +15 s, ask the audience) are free, once each per quiz.
- **Practice your way.** Pick any mix of topics, difficulty, length (including Endless), multiple choice or true/false, timed (20 s per question) or untimed. Flag quizzes live on the Practice tab.
- **Accessibility & comfort.** Larger text option, sound effects, haptics, and an opt-in evening reminder for the Daily Challenge.

## Build

Every push to `main` and every pull request is built by GitHub Actions: it compiles a debug APK and runs the unit tests. Download the dev APK ("Quizzer Dev") from the run's **Artifacts** section. Signed Play Store builds come from the **Release** workflow; see [PLAY_STORE.md](PLAY_STORE.md).

Locally:

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

Or open the project in Android Studio and press Run. Use JDK 21 (Robolectric needs it to run the Android 16 / SDK 36 tests).

Dev builds (`nz.durbs.quizzer.debug`) are signed with the committed `debug.keystore`, so every dev APK installs over the previous one and keeps your progress, alongside the store version. Release builds (`nz.durbs.quizzer`) are shrunk with R8 and signed with a private upload key kept in GitHub secrets. Builds are numbered by workflow run, shown in Settings.

Privacy policy: [PRIVACY.md](PRIVACY.md).

## Project layout

- `data/repository/QuestionPicker.kt` — freshness-first selection, option shuffling, topic balancing
- `data/repository/OpenTdbRemoteSource.kt` — Open Trivia DB client (token, rate limit, retries)
- `data/repository/LearningTools.kt` — mistakes book, shared daily challenge, share text
- `data/repository/QuizRepository.kt` — builds quizzes and records progress
- `ui/viewmodel/QuizViewModel.kt` — quiz engine (timer, lifelines, endless refills)
- `app/src/main/assets/questions.json` — bundled question bank (bump `BUNDLED_SEED_VERSION` when it changes)
