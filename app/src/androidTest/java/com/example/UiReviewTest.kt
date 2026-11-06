package com.example

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI review walk-through. NOT a pass/fail regression test: it drives the app from a fresh install
 * and saves a full-screen PNG at every step so the screens can be reviewed by eye.
 *
 * Every step is wrapped so that one failing step (missing button, crash-free UI problem, timeout)
 * is logged, screenshotted as FAILED_<step> and the walk continues with the next step. At the end the
 * test fails if any step failed, so the instrumentation report lists them.
 *
 * Run with: am instrument -w -e class com.example.UiReviewTest -e prefix light_ <pkg>.test/androidx.test.runner.AndroidJUnitRunner
 * Output goes to the app's external media dir (<sdcard>/Android/media/<pkg>/), which the workflow pulls with adb.
 *
 * Uses createEmptyComposeRule + ActivityScenario (rather than createAndroidComposeRule) so the
 * splash screen can be captured: the activity is launched by the test body, not before it.
 *
 * Navigation expectations covered at the end of the walk: a second launch goes straight to the
 * dashboard (the onboardingDone flag was stored when onboarding finished) and Back from Home leaves
 * the app instead of landing on some earlier screen.
 */
@RunWith(AndroidJUnit4::class)
class UiReviewTest {

  @get:Rule
  val compose = createEmptyComposeRule()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val prefix: String = InstrumentationRegistry.getArguments().getString("prefix") ?: ""
  private val outDir: File by lazy {
    val base = instrumentation.targetContext.externalMediaDirs.firstOrNull { it != null }
      ?: instrumentation.targetContext.filesDir
    base.mkdirs()
    base
  }
  private val failures = mutableListOf<String>()
  private var scenario: ActivityScenario<MainActivity>? = null
  private val rnd = Random(20260106)

  @After
  fun tearDown() {
    try {
      scenario?.close()
    } catch (_: Throwable) {
    }
  }

  // ---------------------------------------------------------------- walk-through

  @Test
  fun walkThrough() {
    log("START prefix='$prefix' out=${outDir.absolutePath}")
    val launchedAt = SystemClock.elapsedRealtime()
    scenario = ActivityScenario.launch(MainActivity::class.java)
    // System splash (androidx.core:core-splashscreen) stays up until the start route is known: grab it early.
    SystemClock.sleep(350)
    try {
      shot("00_splash")
    } catch (t: Throwable) {
      log("splash shot failed: $t")
    }
    log("activity launched ${SystemClock.elapsedRealtime() - launchedAt}ms")

    step("01_onboarding") {
      awaitTag("onboarding_continue", 60_000)
      settle()
      shot("01_onboarding_1")
      clickTag("onboarding_continue")
      settle(700)
      shot("02_onboarding_2")
      clickTag("onboarding_continue")
      settle(700)
      shot("03_onboarding_3")
      clickTag("onboarding_continue")
      awaitTag("nav_tab_home", 30_000)
    }

    step("04_dashboard") {
      awaitTag("quick_play_card", 30_000)
      // Let the onboarding -> dashboard cross-fade finish: on the slow small emulator the first frame
      // otherwise still shows the last onboarding page.
      settle(2000)
      scrollCapture("04_dashboard", 6)
    }

    step("05_daily_lobby") {
      recover()
      scrollToTop()
      clickTag("daily_challenge_card")
      awaitTag("start_daily_challenge_button", 15_000)
      scrollCapture("05_daily_lobby", 3)
      recover()
    }

    step("06_stats_empty") {
      clickTag("nav_tab_analytics")
      settle(800)
      scrollCapture("06_stats_empty", 5)
      if (has("achievements_tab_button")) {
        clickTag("achievements_tab_button")
        settle(800)
        scrollCapture("07_achievements_empty", 6)
      }
    }

    step("08_topic_selection") {
      clickTag("nav_tab_practice")
      awaitTag("start_practice_quiz_button", 15_000)
      settle(800)
      shot("08_topic_selection_top")
      // The page is a plain scrolling column; jump to landmarks instead of swiping (the topic grid
      // inside it would swallow swipes).
      scrollToText("CHOOSE GAMEPLAY FORMAT")
      shot("09_topic_selection_flag_card")
      scrollToText("Select Difficulty")
      shot("10_topic_selection_options")
      scrollToTag("start_practice_quiz_button")
      shot("11_topic_selection_bottom")
      // Exercise the selectors so selected states are visible.
      clickText("Hard", 1)
      clickText("20 Qs", 1)
      clickText("True/False")
      clickText("General Knowledge")
      scrollToText("Select Difficulty")
      shot("12_topic_selection_selected")
      // Put it back to defaults.
      clickText("Mixed", 1)
      clickText("10 Qs", 1)
      clickText("General Knowledge")
    }

    // ---- Normal multiple choice: the long run (lifelines, outcomes, results, review).
    step("20_mc_quiz") {
      openPractice()
      clickText("Multiple Choice")
      clickTag("start_practice_quiz_button")
      waitQuiz("19_quiz_loading")
      settle(600)
      shot("20_quiz_mc_question")
      if (has("fifty_fifty_lifeline")) {
        clickTag("fifty_fifty_lifeline")
        settle(600)
        shot("21_quiz_mc_5050")
      }
      clickTag("ask_audience_lifeline")
      settle(800)
      shot("22_quiz_mc_audience")
      if (has("extra_time_lifeline")) clickTag("extra_time_lifeline")
      clickTag("skip_lifeline")
      settle(900)
      shot("23_quiz_mc_lifelines_used")
      playForOutcomes("24_quiz_mc_correct", "25_quiz_mc_wrong", maxQuestions = 14, finishQuiz = true)
      awaitTag("results_back_to_dashboard_button", 30_000)
      settle(1200)
      scrollCapture("26_results", 4)
      clickTag("review_answers_button")
      settle(1000)
      scrollCapture("27_review", 6)
      recover()
    }

    // ---- True/False
    step("30_tf_quiz") {
      openPractice()
      clickText("True/False")
      clickTag("start_practice_quiz_button")
      waitQuiz(null)
      settle(600)
      shot("30_quiz_tf_question")
      playForOutcomes("31_quiz_tf_correct", "32_quiz_tf_wrong", maxQuestions = 8, finishQuiz = false)
      recover()
    }

    // ---- Capital Cities
    step("40_capitals_quiz") {
      openPractice()
      clickText("Multiple Choice")
      clickTag("featured_capital_cities_card")
      waitQuiz(null)
      settle(600)
      shot("40_quiz_capitals_question")
      playForOutcomes("41_quiz_capitals_correct", "42_quiz_capitals_wrong", maxQuestions = 8, finishQuiz = false)
      recover()
    }

    // ---- Flags: Flag to Country
    step("50_flag_to_country") {
      openPractice()
      scrollToText("CHOOSE GAMEPLAY FORMAT")
      clickText("Flag ➔ Name")
      shot("49_topic_flag_card_flag_to_country_selected")
      clickText("START GEOGRAPHY QUIZ")
      waitQuiz(null)
      settle(800)
      shot("50_quiz_flag_to_country_question")
      playForOutcomes("51_quiz_flag_to_country_correct", "52_quiz_flag_to_country_wrong", maxQuestions = 8, finishQuiz = false)
      recover()
    }

    // ---- Flags: Country to Flag
    step("60_country_to_flag") {
      openPractice()
      scrollToText("CHOOSE GAMEPLAY FORMAT")
      clickText("Name ➔ Flag")
      clickText("START GEOGRAPHY QUIZ")
      waitQuiz(null)
      settle(800)
      shot("60_quiz_country_to_flag_question")
      if (has("fifty_fifty_lifeline")) {
        clickTag("fifty_fifty_lifeline")
        settle(600)
        shot("61_quiz_country_to_flag_5050")
      }
      playForOutcomes("62_quiz_country_to_flag_correct", "63_quiz_country_to_flag_wrong", maxQuestions = 8, finishQuiz = false)
      recover()
    }

    // ---- Screens that now have data in them.
    step("70_stats") {
      recover()
      clickTag("nav_tab_analytics")
      settle(1000)
      scrollCapture("70_stats", 8)
      if (has("achievements_tab_button")) {
        clickTag("achievements_tab_button")
        settle(800)
        scrollCapture("71_achievements", 8)
      }
    }

    step("72_dashboard_after_play") {
      clickTag("nav_tab_home")
      settle(1000)
      scrollCapture("72_dashboard_after_play", 6)
    }

    step("80_settings") {
      clickTag("nav_tab_settings")
      awaitTag("save_profile_button", 15_000)
      settle(800)
      scrollCapture("80_settings", 10)
    }

    step("90_daily_quiz") {
      clickTag("nav_tab_home")
      settle(600)
      // Home keeps its scroll position across tabs now, so the daily card may be off-screen (lazy list).
      scrollToTop()
      clickTag("daily_challenge_card")
      awaitTag("start_daily_challenge_button", 15_000)
      clickTag("start_daily_challenge_button")
      waitQuiz(null)
      settle(600)
      shot("90_daily_quiz_question")
      recover()
    }

    // ---- Onboarding flag + back stack: must stay the last steps (they relaunch / leave the app).
    step("95_relaunch_skips_onboarding") {
      recover()
      scenario?.close()
      scenario = ActivityScenario.launch(MainActivity::class.java)
      awaitTag("nav_tab_home", 60_000)
      settle(800)
      if (has("onboarding_continue")) throw IllegalStateException("onboarding shown again on second launch")
      shot("95_relaunch_dashboard")
    }

    step("96_back_from_home_leaves_app") {
      awaitTag("nav_tab_home", 15_000)
      instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
      val start = SystemClock.elapsedRealtime()
      while (scenario?.state == Lifecycle.State.RESUMED && SystemClock.elapsedRealtime() - start < 10_000) {
        SystemClock.sleep(200)
      }
      if (scenario?.state == Lifecycle.State.RESUMED) {
        shot("96_back_from_home_FAILED")
        throw IllegalStateException("still on screen after Back from Home")
      }
    }

    log("DONE failures=${failures.size}")
    if (failures.isNotEmpty()) {
      fail("Steps failed (${failures.size}): " + failures.joinToString(" | "))
    }
  }

  // ---------------------------------------------------------------- quiz helpers

  private fun openPractice() {
    recover()
    clickTag("nav_tab_practice")
    awaitTag("start_practice_quiz_button", 15_000)
    settle(500)
  }

  /** Waits for the first question of a quiz; screenshots the "Finding fresh questions" spinner if it shows. */
  private fun waitQuiz(loadingShotName: String?) {
    val start = SystemClock.elapsedRealtime()
    var loadingShot = false
    while (SystemClock.elapsedRealtime() - start < 180_000) {
      if (has("quiz_option_a")) {
        log("quiz ready after ${SystemClock.elapsedRealtime() - start}ms")
        return
      }
      if (!loadingShot && loadingShotName != null && hasText("Finding fresh questions", substring = true)) {
        loadingShot = true
        shot(loadingShotName)
        log("saw loading spinner at ${SystemClock.elapsedRealtime() - start}ms")
      }
      SystemClock.sleep(250)
    }
    throw IllegalStateException("Quiz did not show a question within 180s")
  }

  private fun pickOption(): Boolean {
    val labels = listOf("a", "b", "c", "d").filter { has("quiz_option_$it") }
    if (labels.isEmpty()) throw IllegalStateException("no answer options on screen")
    clickTag("quiz_option_${labels[rnd.nextInt(labels.size)]}")
    compose.waitUntil(15_000) { has("next_question_button") }
    settle(500)
    return !hasDesc("Wrong")
  }

  /**
   * Answers questions (random option) until both a correct and a wrong reveal have been captured
   * (or maxQuestions is reached). With finishQuiz=true it keeps answering to the results screen.
   */
  private fun playForOutcomes(correctName: String, wrongName: String, maxQuestions: Int, finishQuiz: Boolean) {
    var gotCorrect = false
    var gotWrong = false
    for (i in 1..maxQuestions) {
      if (!has("quiz_option_a")) break // quiz ended
      val correct = pickOption()
      if (correct && !gotCorrect) {
        shot(correctName)
        gotCorrect = true
      } else if (!correct && !gotWrong) {
        shot(wrongName)
        gotWrong = true
      }
      if (!finishQuiz && gotCorrect && gotWrong) return
      clickTag("next_question_button")
      compose.waitUntil(15_000) {
        has("review_answers_button") || has("results_back_to_dashboard_button") ||
          (has("quiz_option_a") && !has("next_question_button"))
      }
      settle(300)
    }
    if (!gotCorrect) log("NOT CAPTURED: $correctName (no correct answer in $maxQuestions questions)")
    if (!gotWrong) log("NOT CAPTURED: $wrongName (no wrong answer in $maxQuestions questions)")
  }

  // ---------------------------------------------------------------- generic helpers

  private fun step(name: String, block: () -> Unit) {
    log("STEP $name begin")
    try {
      block()
      log("STEP $name ok")
    } catch (t: Throwable) {
      val msg = "${t.javaClass.simpleName}: ${t.message?.replace('\n', ' ')?.take(300)}"
      failures += "$name -> $msg"
      log("STEP $name FAILED: $msg")
      try {
        shot("FAILED_$name")
      } catch (_: Throwable) {
      }
      recover()
    }
  }

  /** Gets back to a screen with the bottom bar whatever state the previous step left behind. */
  private fun recover() {
    for (attempt in 1..4) {
      try {
        when {
          has("confirm_quit_quiz") -> clickTag("confirm_quit_quiz")
          has("quit_quiz_button") -> {
            clickTag("quit_quiz_button")
            compose.waitUntil(5_000) { has("confirm_quit_quiz") }
            clickTag("confirm_quit_quiz")
          }
          has("results_back_to_dashboard_button") -> clickTag("results_back_to_dashboard_button")
          has("nav_tab_home") -> {
            return
          }
          hasDesc("Back") -> compose.onAllNodesWithContentDescription("Back").onFirst().performClick()
          else -> instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
      } catch (t: Throwable) {
        log("recover attempt $attempt: $t")
      }
      settle(500)
    }
  }

  private fun safe(f: () -> Boolean): Boolean = try {
    f()
  } catch (_: Throwable) {
    false
  }

  private fun has(tag: String) = safe { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  private fun hasText(t: String, substring: Boolean = false) =
    safe { compose.onAllNodesWithText(t, substring = substring).fetchSemanticsNodes().isNotEmpty() }
  private fun hasDesc(d: String) =
    safe { compose.onAllNodesWithContentDescription(d).fetchSemanticsNodes().isNotEmpty() }

  private fun awaitTag(tag: String, ms: Long) {
    compose.waitUntil(ms) { has(tag) }
  }

  private fun clickTag(tag: String) {
    awaitTag(tag, 15_000)
    val node = compose.onAllNodesWithTag(tag).onFirst()
    try {
      node.performScrollTo()
    } catch (_: Throwable) {
    }
    node.performClick()
    settle(350)
  }

  private fun textNodes(text: String): SemanticsNodeInteractionCollection {
    compose.waitUntil(15_000) { hasText(text) }
    return compose.onAllNodesWithText(text)
  }

  private fun clickText(text: String, index: Int = 0) {
    val all = textNodes(text)
    val count = all.fetchSemanticsNodes().size
    val node = all[minOf(index, count - 1)]
    try {
      node.performScrollTo()
    } catch (_: Throwable) {
    }
    node.performClick()
    settle(350)
  }

  private fun scrollToText(text: String) {
    val node = textNodes(text).onFirst()
    try {
      node.performScrollTo()
    } catch (_: Throwable) {
    }
    settle(500)
  }

  private fun scrollToTag(tag: String) {
    awaitTag(tag, 15_000)
    try {
      compose.onAllNodesWithTag(tag).onFirst().performScrollTo()
    } catch (_: Throwable) {
    }
    settle(500)
  }

  private fun scrollToTop() {
    repeat(3) {
      try {
        compose.onRoot().performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.85f, durationMillis = 250) }
      } catch (_: Throwable) {
      }
    }
    settle(400)
  }

  /** Waits for Compose to go idle, then a little extra real time for window/transition animations. */
  private fun settle(extraMs: Long = 500) {
    try {
      compose.waitForIdle()
    } catch (_: Throwable) {
    }
    SystemClock.sleep(extraMs)
  }

  // ---------------------------------------------------------------- screenshots + logging

  private fun grab(): Bitmap? = instrumentation.uiAutomation.takeScreenshot()

  private fun save(bmp: Bitmap, name: String) {
    File(outDir, "$prefix$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    log("shot $prefix$name.png")
  }

  private fun shot(name: String) {
    settle(250)
    val bmp = grab() ?: throw IllegalStateException("takeScreenshot returned null")
    save(bmp, name)
  }

  /** Screenshot, then repeatedly swipe up and screenshot until the screen stops changing (status bar ignored). */
  private fun scrollCapture(base: String, max: Int) {
    settle(300)
    var prev = grab() ?: throw IllegalStateException("takeScreenshot returned null")
    save(prev, base)
    for (i in 1..max) {
      try {
        compose.onRoot().performTouchInput {
          swipeUp(startY = height * 0.8f, endY = height * 0.25f, durationMillis = 350)
        }
      } catch (t: Throwable) {
        log("scrollCapture $base: swipe failed: $t")
        break
      }
      settle(600)
      val cur = grab() ?: break
      if (sameBelowStatusBar(prev, cur)) break
      save(cur, "${base}_s$i")
      prev = cur
    }
  }

  private fun sameBelowStatusBar(a: Bitmap, b: Bitmap): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    val top = (a.height * 0.08f).toInt()
    val ca = Bitmap.createBitmap(a, 0, top, a.width, a.height - top)
    val cb = Bitmap.createBitmap(b, 0, top, b.width, b.height - top)
    return ca.sameAs(cb)
  }

  private fun log(msg: String) {
    try {
      File(outDir, "${prefix}steps.log").appendText("${SystemClock.elapsedRealtime()} $msg\n")
    } catch (_: Throwable) {
    }
  }
}
