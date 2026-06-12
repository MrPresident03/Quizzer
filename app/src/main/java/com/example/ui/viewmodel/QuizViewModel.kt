package com.example.ui.viewmodel

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.QuizApplication
import com.example.data.model.Achievement
import com.example.data.model.Question
import com.example.data.model.QuizHistory
import com.example.data.model.UserStats
import com.example.data.repository.QuizRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
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

    val allQuestions: StateFlow<List<Question>> = repository.allQuestions
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

    private val _timeLeft = MutableStateFlow(60) // 60 seconds per question
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _quizFinished = MutableStateFlow(false)
    val quizFinished: StateFlow<Boolean> = _quizFinished.asStateFlow()

    // Interactive Session Logs
    val quizAnswersList = mutableListOf<Boolean>() // list of true/false for questions
    val quizSelectedIndices = mutableListOf<Int?>() // option picked for each question
    var sessionStartTime = System.currentTimeMillis()
    var speedDemonStreak = 0
    var lifelinesUsedThisSession = 0

    // Lifeline UI Helpers
    private val _hiddenOptions = MutableStateFlow<Set<Int>>(emptySet())
    val hiddenOptions: StateFlow<Set<Int>> = _hiddenOptions.asStateFlow()

    private val _audiencePoll = MutableStateFlow<List<Int>?>(null)
    val audiencePoll: StateFlow<List<Int>?> = _audiencePoll.asStateFlow()

    // Daily Challenge Locker
    private val _dailyPlayedToday = MutableStateFlow(false)
    val dailyPlayedToday: StateFlow<Boolean> = _dailyPlayedToday.asStateFlow()

    // Timer Job
    private var timerJob: Job? = null
    private val maxQuestionTimer = 60

    init {
        checkDailyPlayedStatus()
    }

    fun checkDailyPlayedStatus() {
        viewModelScope.launch {
            val stats = repository.userStats.firstOrNull() ?: UserStats()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            _dailyPlayedToday.value = (stats.lastQuizDateStr == todayStr && stats.totalQuestionsAnswered > 0)
        }
    }

    // --- QUIZ GAME ENGINE ACTIONS ---

    fun startPracticeQuiz(categories: List<String>, difficulty: String, count: Int, format: String) {
        viewModelScope.launch {
            resetQuizState()
            val questions = repository.getPracticeQuiz(categories, difficulty, count, format)
            if (questions.isNotEmpty()) {
                _activeQuestions.value = questions
                startQuestionTimer()
            } else {
                // Fail-safe check: load general questions
                _activeQuestions.value = repository.getPracticeQuiz(emptyList(), "Mixed", 10, "Mixed")
                startQuestionTimer()
            }
        }
    }

    fun startDailyChallenge() {
        viewModelScope.launch {
            resetQuizState()
            val questions = repository.getDailyChallengeQuiz()
            _activeQuestions.value = questions
            startQuestionTimer()
        }
    }

    fun startFlagsQuiz(mode: String, difficulty: String, count: Int) {
        viewModelScope.launch {
            resetQuizState()
            val questions = repository.getFlagQuiz(mode, difficulty, count)
            _activeQuestions.value = questions
            startQuestionTimer()
        }
    }

    private fun resetQuizState() {
        _currentQuestionIndex.value = 0
        _currentSelectedOption.value = null
        _isAnswerRevealed.value = false
        _timeLeft.value = maxQuestionTimer
        _quizFinished.value = false
        _hiddenOptions.value = emptySet()
        _audiencePoll.value = null
        quizAnswersList.clear()
        quizSelectedIndices.clear()
        sessionStartTime = System.currentTimeMillis()
        speedDemonStreak = 0
        lifelinesUsedThisSession = 0
        stopTimer()
    }

    private fun startQuestionTimer() {
        stopTimer()
        _timeLeft.value = maxQuestionTimer
        timerJob = viewModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value -= 1
            }
            // Auto timeout is treated as WRONG / skipped
            onQuestionTimeout()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private suspend fun onQuestionTimeout() {
        if (_isAnswerRevealed.value) return
        
        _currentSelectedOption.value = null
        _isAnswerRevealed.value = true
        quizAnswersList.add(false)
        quizSelectedIndices.add(null)
        speedDemonStreak = 0
        
        val currentQ = _activeQuestions.value.getOrNull(_currentQuestionIndex.value)
        if (currentQ != null) {
            repository.incrementAnswerStats(currentQ.id, false)
        }

        delay(3000)
        advanceToNextQuestionOrFinish()
    }

    fun selectOption(optionIndex: Int) {
        if (_isAnswerRevealed.value) return
        
        stopTimer()
        _currentSelectedOption.value = optionIndex
        _isAnswerRevealed.value = true
        
        val currentQ = _activeQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return
        val isCorrect = optionIndex == currentQ.correctIndex
        
        quizAnswersList.add(isCorrect)
        quizSelectedIndices.add(optionIndex)

        // Evaluate Answer Tracking & Speed Demon Achievement
        val timeSpent = maxQuestionTimer - _timeLeft.value
        if (isCorrect) {
            if (timeSpent <= 10) {
                speedDemonStreak += 1
            } else {
                speedDemonStreak = 0
            }
        } else {
            speedDemonStreak = 0
        }

        viewModelScope.launch {
            repository.incrementAnswerStats(currentQ.id, isCorrect)
            
            // Wait 2.5 seconds to show explanation + colors before sliding out!
            delay(2500)
            advanceToNextQuestionOrFinish()
        }
    }

    private suspend fun advanceToNextQuestionOrFinish() {
        _currentSelectedOption.value = null
        _isAnswerRevealed.value = false
        _hiddenOptions.value = emptySet()
        _audiencePoll.value = null
        
        val nextIndex = _currentQuestionIndex.value + 1
        if (nextIndex < _activeQuestions.value.size) {
            _currentQuestionIndex.value = nextIndex
            startQuestionTimer()
        } else {
            finishQuizSession()
        }
    }

    private suspend fun finishQuizSession() {
        _quizFinished.value = true
        stopTimer()

        val correctCount = quizAnswersList.count { it }
        val totalCount = _activeQuestions.value.size
        val duration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
        
        // Find category: if multiple categories was played, call it "Mixed Practice"
        val distinctCats = _activeQuestions.value.map { it.category }.distinct()
        val finalCategory = if (distinctCats.size == 1) distinctCats.first() else "Mixed Practice"
        val finalDifficulty = if (_activeQuestions.value.isNotEmpty()) _activeQuestions.value.first().difficulty else "medium"
        
        val isDaily = totalCount == 10 && _activeQuestions.value.getOrNull(0)?.category != "Mixed Practice" // daily challenge
        val quizType = if (isDaily) "Daily" else "Practice"

        repository.recordQuizSession(
            correctCount = correctCount,
            totalCount = totalCount,
            timeTakenSeconds = duration,
            category = finalCategory,
            difficulty = finalDifficulty,
            quizType = quizType,
            speedDemonQuestionsAnsweredInTimeStreak = speedDemonStreak,
            lifelinesUsedCount = lifelinesUsedThisSession
        )
        
        if (isDaily) {
            _dailyPlayedToday.value = true
        }
    }

    // --- LIFELINES ACTIONS ---

    fun useLifeline5050() {
        val stats = userStats.value
        if (stats.lifelineFiftyFiftyCount <= 0 || _isAnswerRevealed.value) return
        
        viewModelScope.launch {
            if (repository.useLifeline("5050")) {
                val currentQ = _activeQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return@launch
                val incorrectIndices = currentQ.optionsList.indices.filter { it != currentQ.correctIndex }
                if (incorrectIndices.size >= 2) {
                    _hiddenOptions.value = incorrectIndices.shuffled().take(2).toSet()
                    lifelinesUsedThisSession += 1
                }
            }
        }
    }

    fun useLifelineSkip() {
        val stats = userStats.value
        if (stats.lifelineSkipCount <= 0 || _isAnswerRevealed.value) return
        
        viewModelScope.launch {
            if (repository.useLifeline("skip")) {
                // Consume lifeline
                lifelinesUsedThisSession += 1
                stopTimer()
                
                // Treat as skipped/correct or neutral. Let's make skip a neutral score
                quizAnswersList.add(false)
                quizSelectedIndices.add(null)
                
                advanceToNextQuestionOrFinish()
            }
        }
    }

    fun useLifelineExtraTime() {
        val stats = userStats.value
        if (stats.lifelineExtraTimeCount <= 0 || _isAnswerRevealed.value) return
        
        viewModelScope.launch {
            if (repository.useLifeline("time")) {
                _timeLeft.value += 15
                lifelinesUsedThisSession += 1
            }
        }
    }

    fun useLifelineAskAudience() {
        val stats = userStats.value
        if (stats.lifelineAskAudienceCount <= 0 || _audiencePoll.value != null || _isAnswerRevealed.value) return
        
        viewModelScope.launch {
            if (repository.useLifeline("audience")) {
                lifelinesUsedThisSession += 1
                // Generate a smart audience poll showing visual bar chart percentages.
                // Correct answer should get highest weight, but have some noise
                val currentQ = _activeQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return@launch
                val size = currentQ.optionsList.size
                if (size == 0) return@launch
                
                val poll = MutableList(size) { 0 }
                val correctIdx = currentQ.correctIndex
                
                // High probability of audience choosing correctly
                val correctPercent = Random.nextInt(55, 80)
                val remaining = 100 - correctPercent
                
                poll[correctIdx] = correctPercent
                
                var left = remaining
                val remainingIndices = (0 until size).filter { it != correctIdx }
                for (i in remainingIndices.indices) {
                    if (i == remainingIndices.lastIndex) {
                        poll[remainingIndices[i]] = left
                    } else {
                        val p = Random.nextInt(0, left)
                        poll[remainingIndices[i]] = p
                        left -= p
                    }
                }
                
                _audiencePoll.value = poll
            }
        }
    }

    // --- XP SHOP & CUSTOMIZATION ---

    fun buyLifelineItem(type: String, cost: Int) {
        viewModelScope.launch {
            val success = repository.buyLifeline(type, cost)
            if (success) {
                Log.d("QuizViewModel", "Successfully purchased item of type $type")
            }
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
