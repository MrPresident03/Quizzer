package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Achievement
import com.example.data.model.Question
import com.example.data.model.QuizHistory
import com.example.data.model.UserStats
import com.example.data.repository.Categories
import com.example.data.repository.QuestionPicker
import com.example.data.repository.QuizRepository
import com.example.data.repository.Scoring
import com.example.data.repository.SessionSummary
import com.example.data.repository.SessionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class QuizViewModel(
    application: Application,
    private val repository: QuizRepository
) : AndroidViewModel(application) {

    // --- DATA STATE FLOWS ---
    val userStats: StateFlow<UserStats> = repository.userStats
        .map { it ?: UserStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserStats())

    val quizHistory: StateFlow<List<QuizHistory>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val achievements: StateFlow<List<Achievement>> = repository.allAchievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ACTIVE QUIZ STATE ---
    private val _activeQuestions = MutableStateFlow<List<Question>>(emptyList())
    val activeQuestions: StateFlow<List<Question>> = _activeQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _currentSelectedOption = MutableStateFlow<Int?>(null)
    val currentSelectedOption: StateFlow<Int?> = _currentSelectedOption.asStateFlow()

    private val _isAnswerRevealed = MutableStateFlow(false)
    val isAnswerRevealed: StateFlow<Boolean> = _isAnswerRevealed.asStateFlow()

    private val _timeLeft = MutableStateFlow(MAX_QUESTION_SECONDS)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _quizFinished = MutableStateFlow(false)
    val quizFinished: StateFlow<Boolean> = _quizFinished.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _isTimed = MutableStateFlow(true)
    val isTimed: StateFlow<Boolean> = _isTimed.asStateFlow()

    private val _sessionType = MutableStateFlow(SessionType.PRACTICE)
    val sessionType: StateFlow<SessionType> = _sessionType.asStateFlow()

    private val _lastSessionXp = MutableStateFlow(0)
    val lastSessionXp: StateFlow<Int> = _lastSessionXp.asStateFlow()

    // Per-question log for the results/review screens
    private val answers = mutableListOf<Boolean>()
    private val selections = mutableListOf<Int?>()
    val quizAnswersList: List<Boolean> get() = answers.toList()
    val quizSelectedIndices: List<Int?> get() = selections.toList()

    private var sessionStartTime = System.currentTimeMillis()
    private var speedStreak = 0
    private var bestSpeedStreak = 0
    private var lifelinesUsedThisSession = 0

    // Lifeline UI Helpers
    private val _hiddenOptions = MutableStateFlow<Set<Int>>(emptySet())
    val hiddenOptions: StateFlow<Set<Int>> = _hiddenOptions.asStateFlow()

    private val _audiencePoll = MutableStateFlow<List<Int>?>(null)
    val audiencePoll: StateFlow<List<Int>?> = _audiencePoll.asStateFlow()

    // Daily Challenge Locker
    private val _dailyPlayedToday = MutableStateFlow(false)
    val dailyPlayedToday: StateFlow<Boolean> = _dailyPlayedToday.asStateFlow()

    // Session bookkeeping. Every new/quit session bumps sessionId, and every delayed coroutine checks
    // it before touching state, so a stale "advance in 2.5 s" can't leak into the next quiz.
    private var sessionId = 0
    private var loadJob: Job? = null
    private var timerJob: Job? = null
    private var advanceJob: Job? = null
    private var refillJob: Job? = null
    private var lifelineBusy = false
    private var advancing = false // between an answer/skip/timeout and the next question
    private var finishing = false
    private var selectedDifficulty = "Mixed"
    private var practiceRequest: PracticeRequest? = null

    private data class PracticeRequest(val categories: List<String>, val difficulty: String, val format: String)

    init {
        checkDailyPlayedStatus()
    }

    /** Re-check on every dashboard visit so the lock clears after midnight while the app is open. */
    fun checkDailyPlayedStatus() {
        _dailyPlayedToday.value = repository.isDailyPlayed()
    }

    // --- QUIZ GAME ENGINE ACTIONS ---

    fun startPracticeQuiz(categories: List<String>, difficulty: String, count: Int, format: String, timed: Boolean = true) {
        val endless = count == QuizRepository.ENDLESS_COUNT
        val id = beginSession(if (endless) SessionType.ENDLESS else SessionType.PRACTICE, difficulty, timed)
        practiceRequest = PracticeRequest(categories, difficulty, format)
        load(id) { repository.getPracticeQuiz(categories, difficulty, count, format) }
    }

    fun startDailyChallenge() {
        val id = beginSession(SessionType.DAILY, "Mixed", timed = true)
        if (repository.isDailyPlayed()) {
            _dailyPlayedToday.value = true
            _loadError.value = "You've already played today's challenge. A new one unlocks at midnight."
            return
        }
        load(id) { repository.getDailyChallengeQuiz() }
    }

    fun startFlagsQuiz(mode: String, difficulty: String, count: Int) {
        val id = beginSession(SessionType.FLAGS, difficulty, timed = true)
        load(id) { repository.getFlagQuiz(mode, difficulty, count) }
    }

    /** Called when the player leaves the quiz screen early. Returns true if a results screen will follow. */
    fun quitQuiz(): Boolean {
        if (_quizFinished.value) return true
        val type = _sessionType.value
        if (type == SessionType.ENDLESS && answers.isNotEmpty()) {
            // Endless only ends when you stop, so stopping shows your run's results.
            val id = sessionId
            cancelSessionJobs()
            viewModelScope.launch { finishQuizSession(id) }
            return true
        }
        if (type == SessionType.DAILY && answers.isNotEmpty()) {
            // One attempt per day: bailing out after seeing questions uses it up.
            repository.markDailyPlayed()
            _dailyPlayedToday.value = true
        }
        sessionId++
        cancelSessionJobs()
        _activeQuestions.value = emptyList()
        _isLoading.value = false
        return false
    }

    private fun beginSession(type: SessionType, difficulty: String, timed: Boolean): Int {
        sessionId++
        cancelSessionJobs()
        _sessionType.value = type
        _isTimed.value = timed
        selectedDifficulty = difficulty
        practiceRequest = null
        _activeQuestions.value = emptyList()
        _currentQuestionIndex.value = 0
        _currentSelectedOption.value = null
        _isAnswerRevealed.value = false
        _timeLeft.value = MAX_QUESTION_SECONDS
        _quizFinished.value = false
        _isLoading.value = false
        _loadError.value = null
        _hiddenOptions.value = emptySet()
        _audiencePoll.value = null
        _lastSessionXp.value = 0
        answers.clear()
        selections.clear()
        sessionStartTime = System.currentTimeMillis()
        speedStreak = 0
        bestSpeedStreak = 0
        lifelinesUsedThisSession = 0
        lifelineBusy = false
        advancing = false
        finishing = false
        return sessionId
    }

    private fun cancelSessionJobs() {
        loadJob?.cancel()
        timerJob?.cancel()
        advanceJob?.cancel()
        refillJob?.cancel()
        loadJob = null
        timerJob = null
        advanceJob = null
        refillJob = null
    }

    private fun load(id: Int, fetch: suspend () -> List<Question>) {
        _isLoading.value = true
        loadJob = viewModelScope.launch {
            val questions = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build quiz", e)
                emptyList()
            }
            if (id != sessionId) return@launch
            _isLoading.value = false
            if (questions.isEmpty()) {
                _loadError.value = "Couldn't load any questions for these settings. Try different topics or check your connection."
                return@launch
            }
            sessionStartTime = System.currentTimeMillis()
            _activeQuestions.value = questions
            startQuestionTimer(id)
        }
    }

    private fun currentQuestion(): Question? = _activeQuestions.value.getOrNull(_currentQuestionIndex.value)

    private fun canInteract(): Boolean =
        !_isAnswerRevealed.value && !advancing && !_isLoading.value && !_quizFinished.value && !finishing &&
            currentQuestion() != null

    private fun startQuestionTimer(id: Int) {
        timerJob?.cancel()
        _timeLeft.value = MAX_QUESTION_SECONDS
        if (!_isTimed.value) return
        timerJob = viewModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                if (id != sessionId) return@launch
                _timeLeft.value -= 1
            }
            onQuestionTimeout(id)
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun onQuestionTimeout(id: Int) {
        if (id != sessionId || !canInteract()) return
        val question = currentQuestion() ?: return
        _currentSelectedOption.value = null
        _isAnswerRevealed.value = true
        logAnswer(question, null, correct = false)
        speedStreak = 0
        scheduleAdvance(id, REVEAL_TIMEOUT_MS)
    }

    fun selectOption(optionIndex: Int) {
        if (!canInteract()) return
        val question = currentQuestion() ?: return
        if (optionIndex !in question.optionsList.indices) return
        val id = sessionId

        stopTimer()
        _currentSelectedOption.value = optionIndex
        _isAnswerRevealed.value = true

        val isCorrect = optionIndex == question.correctIndex
        logAnswer(question, optionIndex, isCorrect)

        // Speed Demon: consecutive correct answers within 10 seconds each.
        val timeSpent = MAX_QUESTION_SECONDS - _timeLeft.value
        speedStreak = if (isCorrect && _isTimed.value && timeSpent <= SPEED_SECONDS) speedStreak + 1 else 0
        bestSpeedStreak = maxOf(bestSpeedStreak, speedStreak)

        scheduleAdvance(id, REVEAL_ANSWER_MS)
    }

    private fun logAnswer(question: Question, selected: Int?, correct: Boolean) {
        answers.add(correct)
        selections.add(selected)
        viewModelScope.launch {
            try {
                repository.recordAnswer(question, correct)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't record answer", e)
            }
        }
    }

    private fun scheduleAdvance(id: Int, delayMs: Long) {
        advancing = true
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(delayMs)
            if (id == sessionId) advanceToNextQuestionOrFinish(id)
        }
    }

    private suspend fun advanceToNextQuestionOrFinish(id: Int) {
        val nextIndex = _currentQuestionIndex.value + 1
        val isEndless = _sessionType.value == SessionType.ENDLESS

        if (isEndless) {
            maybeRefillEndless(id, nextIndex)
            if (nextIndex >= _activeQuestions.value.size) {
                // Out of prefetched questions: wait for the refill rather than ending the run.
                _isLoading.value = true
                refillJob?.join()
                if (id != sessionId) return
                _isLoading.value = false
            }
        }

        if (nextIndex < _activeQuestions.value.size) {
            _hiddenOptions.value = emptySet()
            _audiencePoll.value = null
            _currentSelectedOption.value = null
            _currentQuestionIndex.value = nextIndex
            _isAnswerRevealed.value = false
            advancing = false
            startQuestionTimer(id)
        } else {
            finishQuizSession(id)
        }
    }

    /** Endless mode keeps fetching fresh batches a few questions before the end. */
    private fun maybeRefillEndless(id: Int, nextIndex: Int) {
        val request = practiceRequest ?: return
        if (refillJob?.isActive == true) return
        if (_activeQuestions.value.size - nextIndex > ENDLESS_REFILL_THRESHOLD) return

        refillJob = viewModelScope.launch {
            val usedKeys = _activeQuestions.value.mapTo(HashSet()) { QuestionPicker.keyOf(it) }
            val more = try {
                repository.getPracticeQuiz(
                    request.categories, request.difficulty, QuizRepository.ENDLESS_COUNT, request.format, usedKeys
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (id == sessionId && more.isNotEmpty()) {
                _activeQuestions.value = _activeQuestions.value + more
            }
        }
    }

    private suspend fun finishQuizSession(id: Int) {
        if (finishing || id != sessionId) return
        finishing = true
        stopTimer()

        // Only count what was actually played (endless mode prefetches ahead).
        val played = _activeQuestions.value.take(answers.size)
        val playedAnswers = answers.toList()
        val summary = SessionSummary(
            type = _sessionType.value,
            questions = played,
            answers = playedAnswers,
            selectedDifficulty = selectedDifficulty,
            categoryLabel = sessionLabel(played),
            isMixedTopics = isMixedTopics(),
            durationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
            bestSpeedStreak = bestSpeedStreak,
            lifelinesUsed = lifelinesUsedThisSession
        )

        // Show results immediately; XP shown uses the same formula the repository saves.
        _lastSessionXp.value = Scoring.sessionXp(played, playedAnswers)
        _activeQuestions.value = played
        _isLoading.value = false
        _quizFinished.value = true

        if (summary.type == SessionType.DAILY) {
            repository.markDailyPlayed()
            _dailyPlayedToday.value = true
        }
        try {
            _lastSessionXp.value = repository.recordQuizSession(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save session", e)
        }
    }

    private fun sessionLabel(played: List<Question>): String = when (_sessionType.value) {
        SessionType.DAILY -> "Daily Challenge"
        SessionType.FLAGS -> "Flags"
        else -> played.map { it.category }.distinct().singleOrNull() ?: "Mixed Practice"
    }

    private fun isMixedTopics(): Boolean {
        val selected = practiceRequest?.categories?.filter { it != Categories.ALL_TOPICS }.orEmpty()
        return practiceRequest != null && (selected.isEmpty() || selected.size > 1)
    }

    // --- LIFELINES ACTIONS ---

    /** Runs a lifeline once, even if the button is double-tapped while the DB write is in flight. */
    private fun runLifeline(type: String, onSuccess: (Question) -> Unit) {
        if (lifelineBusy || !canInteract()) return
        val id = sessionId
        lifelineBusy = true
        viewModelScope.launch {
            try {
                val question = currentQuestion() ?: return@launch
                if (repository.useLifeline(type) && id == sessionId && canInteract() && currentQuestion() == question) {
                    lifelinesUsedThisSession += 1
                    onSuccess(question)
                }
            } finally {
                lifelineBusy = false
            }
        }
    }

    fun useLifeline5050() {
        if (_hiddenOptions.value.isNotEmpty() || userStats.value.lifelineFiftyFiftyCount <= 0) return
        val question = currentQuestion() ?: return
        if (question.optionsList.size < 4) return // pointless on True/False
        runLifeline("5050") { q ->
            val incorrect = q.optionsList.indices.filter { it != q.correctIndex }
            _hiddenOptions.value = incorrect.shuffled().take(2).toSet()
        }
    }

    fun useLifelineSkip() {
        if (userStats.value.lifelineSkipCount <= 0) return
        runLifeline("skip") { q ->
            stopTimer()
            val id = sessionId
            // A skip counts as not-correct but isn't shown as a wrong answer in the review.
            logAnswer(q, null, correct = false)
            speedStreak = 0
            scheduleAdvance(id, 0)
        }
    }

    fun useLifelineExtraTime() {
        if (!_isTimed.value || userStats.value.lifelineExtraTimeCount <= 0) return
        runLifeline("time") {
            _timeLeft.value += EXTRA_TIME_SECONDS
        }
    }

    fun useLifelineAskAudience() {
        if (_audiencePoll.value != null || userStats.value.lifelineAskAudienceCount <= 0) return
        runLifeline("audience") { q ->
            _audiencePoll.value = buildAudiencePoll(q, _hiddenOptions.value)
        }
    }

    /** Correct answer gets 55-79%, the rest is split randomly across the visible wrong answers. */
    private fun buildAudiencePoll(question: Question, hidden: Set<Int>): List<Int> {
        val size = question.optionsList.size
        val poll = MutableList(size) { 0 }
        val correctPercent = Random.nextInt(55, 80)
        poll[question.correctIndex] = correctPercent
        val others = (0 until size).filter { it != question.correctIndex && it !in hidden }
        var left = 100 - correctPercent
        others.forEachIndexed { i, optionIndex ->
            val share = if (i == others.lastIndex) left else Random.nextInt(0, left + 1)
            poll[optionIndex] = share
            left -= share
        }
        if (others.isEmpty()) poll[question.correctIndex] = 100
        return poll
    }

    // --- XP SHOP & CUSTOMIZATION ---

    fun buyLifelineItem(type: String, cost: Int) {
        viewModelScope.launch {
            repository.buyLifeline(type, cost)
        }
    }

    fun buyOrUnlockTheme(themeId: String, cost: Int) {
        viewModelScope.launch {
            repository.unlockTheme(themeId, cost)
        }
    }

    fun selectActiveTheme(themeId: String) {
        viewModelScope.launch {
            repository.selectTheme(themeId)
        }
    }

    // --- SETTINGS ACTIONS ---

    fun updateProfile(displayName: String, avatarId: String) {
        viewModelScope.launch {
            val stats = userStats.value
            repository.updateConfig(
                sound = stats.isSoundEffectsEnabled,
                haptic = stats.isHapticFeedbackEnabled,
                textLarge = stats.fontSizePreference == "large",
                displayName = displayName,
                avatar = avatarId
            )
        }
    }

    fun updateToggles(sound: Boolean, haptic: Boolean, textLarge: Boolean) {
        viewModelScope.launch {
            val stats = userStats.value
            repository.updateConfig(
                sound = sound,
                haptic = haptic,
                textLarge = textLarge,
                displayName = stats.displayName,
                avatar = stats.avatarId
            )
        }
    }

    fun resetAllAppProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            _dailyPlayedToday.value = false
        }
    }

    fun clearHistoryAndRepeats() {
        viewModelScope.launch {
            repository.clearQuestionHistory()
        }
    }

    private companion object {
        const val TAG = "QuizViewModel"
        const val MAX_QUESTION_SECONDS = 60
        const val EXTRA_TIME_SECONDS = 15
        const val SPEED_SECONDS = 10
        const val REVEAL_ANSWER_MS = 2500L
        const val REVEAL_TIMEOUT_MS = 3000L
        const val ENDLESS_REFILL_THRESHOLD = 5
    }
}

// Custom factory class for instantiating the QuizViewModel with Application and Repository singletons
class QuizViewModelFactory(
    private val application: Application,
    private val repository: QuizRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuizViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuizViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
