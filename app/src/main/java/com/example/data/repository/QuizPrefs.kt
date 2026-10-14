package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Small key-value store for state that doesn't need a Room table (and therefore no DB migration):
 * which questions were recently shown, the OpenTDB session token, bundled-question seed version,
 * the daily-challenge lock and a few achievement counters.
 */
class QuizPrefs(context: Context) : OpenTdbTokenStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val lock = Any()
    private var seenLog: SeenLog? = null

    // --- Recently seen questions ---

    private fun seen(): SeenLog = seenLog ?: SeenLog.parse(prefs.getString(KEY_SEEN, null)).also { seenLog = it }

    fun seenSnapshot(): Map<String, Long> = synchronized(lock) { seen().snapshot() }

    fun markSeen(keys: Collection<String>, at: Long = System.currentTimeMillis()) {
        if (keys.isEmpty()) return
        synchronized(lock) {
            val log = seen()
            keys.forEach { log.mark(it, at) }
            prefs.edit().putString(KEY_SEEN, log.serialize()).apply()
        }
    }

    fun clearSeen() = synchronized(lock) {
        seen().clear()
        prefs.edit().remove(KEY_SEEN).apply()
    }

    // --- Open Trivia DB session token ---

    override var openTdbToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    // --- Bundled question bank version ---

    var bundledSeedVersion: Int
        get() = prefs.getInt(KEY_SEED_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_SEED_VERSION, value).apply()

    // --- Daily challenge lock (ISO yyyy-MM-dd) ---

    var dailyPlayedDate: String?
        get() = prefs.getString(KEY_DAILY_DATE, null)
        set(value) = prefs.edit().putString(KEY_DAILY_DATE, value).apply()

    // --- Achievement counters ---

    fun categoryCorrect(category: String): Int = prefs.getInt(KEY_CATEGORY_PREFIX + category, 0)

    fun addCategoryCorrect(counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        val editor = prefs.edit()
        counts.forEach { (category, n) ->
            editor.putInt(KEY_CATEGORY_PREFIX + category, categoryCorrect(category) + n)
        }
        editor.apply()
    }

    val lifelinesUsedTotal: Int get() = prefs.getInt(KEY_LIFELINES_USED, 0)

    fun addLifelinesUsed(n: Int) {
        if (n > 0) prefs.edit().putInt(KEY_LIFELINES_USED, lifelinesUsedTotal + n).apply()
    }

    val shopPurchases: Int get() = prefs.getInt(KEY_SHOP_PURCHASES, 0)

    fun addShopPurchase() = prefs.edit().putInt(KEY_SHOP_PURCHASES, shopPurchases + 1).apply()

    /** Wipes player progress but keeps technical state (token, seed version). */
    fun resetProgress() = synchronized(lock) {
        seenLog = null
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_CATEGORY_PREFIX) }
            .forEach { editor.remove(it) }
        editor.remove(KEY_SEEN)
            .remove(KEY_DAILY_DATE)
            .remove(KEY_LIFELINES_USED)
            .remove(KEY_SHOP_PURCHASES)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "quiz_prefs"
        const val KEY_SEEN = "seen_log"
        const val KEY_TOKEN = "opentdb_token"
        const val KEY_SEED_VERSION = "bundled_seed_version"
        const val KEY_DAILY_DATE = "daily_played_date"
        const val KEY_CATEGORY_PREFIX = "category_correct_"
        const val KEY_LIFELINES_USED = "lifelines_used_total"
        const val KEY_SHOP_PURCHASES = "shop_purchases"
    }
}
