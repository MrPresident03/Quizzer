package com.example.data.repository

import com.example.data.model.Question
import java.util.Locale
import kotlin.random.Random

object FlagQuestionsProvider {

    data class CountryFlag(val name: String, val flag: String, val continent: String)

    val countries = listOf(
        CountryFlag("United States", "🇺🇸", "Americas"),
        CountryFlag("United Kingdom", "🇬🇧", "Europe"),
        CountryFlag("Canada", "🇨🇦", "Americas"),
        CountryFlag("Australia", "🇦🇺", "Oceania"),
        CountryFlag("Japan", "🇯🇵", "Asia"),
        CountryFlag("Germany", "🇩🇪", "Europe"),
        CountryFlag("France", "🇫🇷", "Europe"),
        CountryFlag("Italy", "🇮🇹", "Europe"),
        CountryFlag("Brazil", "🇧🇷", "Americas"),
        CountryFlag("India", "🇮🇳", "Asia"),
        CountryFlag("South Africa", "🇿🇦", "Africa"),
        CountryFlag("China", "🇨🇳", "Asia"),
        CountryFlag("Mexico", "🇲🇽", "Americas"),
        CountryFlag("Spain", "🇪🇸", "Europe"),
        CountryFlag("Russia", "🇷🇺", "Europe/Asia"),
        CountryFlag("South Korea", "🇰🇷", "Asia"),
        CountryFlag("Singapore", "🇸🇬", "Asia"),
        CountryFlag("New Zealand", "🇳🇿", "Oceania"),
        CountryFlag("Switzerland", "🇨🇭", "Europe"),
        CountryFlag("Netherlands", "🇳🇱", "Europe"),
        CountryFlag("Sweden", "🇸🇪", "Europe"),
        CountryFlag("Norway", "🇳🇴", "Europe"),
        CountryFlag("Denmark", "🇩🇰", "Europe"),
        CountryFlag("Finland", "🇫🇮", "Europe"),
        CountryFlag("Ireland", "🇮🇪", "Europe"),
        CountryFlag("Austria", "🇦🇹", "Europe"),
        CountryFlag("Belgium", "🇧🇪", "Europe"),
        CountryFlag("Greece", "🇬🇷", "Europe"),
        CountryFlag("Portugal", "🇵🇹", "Europe"),
        CountryFlag("Turkey", "🇹🇷", "Europe/Asia"),
        CountryFlag("Egypt", "🇪🇬", "Africa/Asia"),
        CountryFlag("Saudi Arabia", "🇸🇦", "Asia"),
        CountryFlag("United Arab Emirates", "🇦🇪", "Asia"),
        CountryFlag("Argentina", "🇦🇷", "Americas"),
        CountryFlag("Chile", "🇨🇱", "Americas"),
        CountryFlag("Colombia", "🇨🇴", "Americas"),
        CountryFlag("Peru", "🇵🇪", "Americas"),
        CountryFlag("Thailand", "🇹🇭", "Asia"),
        CountryFlag("Vietnam", "🇻🇳", "Asia"),
        CountryFlag("Malaysia", "🇲🇾", "Asia"),
        CountryFlag("Indonesia", "🇮🇩", "Asia"),
        CountryFlag("Philippines", "🇵🇭", "Asia"),
        CountryFlag("Ukraine", "🇺🇦", "Europe"),
        CountryFlag("Poland", "🇵🇱", "Europe"),
        CountryFlag("Iceland", "🇮🇸", "Europe"),
        CountryFlag("Kenya", "🇰🇪", "Africa"),
        CountryFlag("Nigeria", "🇳🇬", "Africa"),
        CountryFlag("Morocco", "🇲🇦", "Africa"),
        CountryFlag("Jamaica", "🇯🇲", "Americas"),
        CountryFlag("Costa Rica", "🇨🇷", "Americas")
    )

    fun generateFlagQuiz(mode: String, count: Int): List<Question> {
        val qty = if (count == 999) 40 else count
        val questions = mutableListOf<Question>()
        val shuffledCountries = countries.shuffled()

        for (i in 0 until qty.coerceAtMost(countries.size)) {
            val target = shuffledCountries[i]
            
            // Choose question mode format:
            // "flag_to_country" (Recognize country name from flag)
            // "country_to_flag" (Recognize flag emoji from country name)
            val currentMode = if (mode == "Mixed") {
                if (Random.nextBoolean()) "flag_to_country" else "country_to_flag"
            } else {
                mode
            }

            if (currentMode == "flag_to_country") {
                // Wrong options are other countries
                val wrongOptions = countries.filter { it.name != target.name }
                    .shuffled()
                    .take(3)
                    .map { it.name }
                
                val options = (wrongOptions + target.name).shuffled()
                val correctIndex = options.indexOf(target.name)

                questions.add(
                    Question(
                        text = "Which country belongs to this flag?\n\n${target.flag}",
                        optionsList = options,
                        correctIndex = correctIndex,
                        category = "Flags: Flag to Country",
                        difficulty = "medium",
                        explanation = "This is the national flag of ${target.name}. It represents the country's unity and heritage in the ${target.continent} region."
                    )
                )
            } else {
                // Wrong options are other country flags
                val wrongOptions = countries.filter { it.flag != target.flag }
                    .shuffled()
                    .take(3)
                    .map { it.flag }
                
                val correctLabel = target.flag
                val options = (wrongOptions + correctLabel).shuffled()
                val correctIndex = options.indexOf(correctLabel)

                questions.add(
                    Question(
                        text = "Identify the correct flag for the country:\n\n${target.name}",
                        optionsList = options,
                        correctIndex = correctIndex,
                        category = "Flags: Country to Flag",
                        difficulty = "medium",
                        explanation = "${target.flag} belongs to ${target.name} (${target.continent})."
                    )
                )
            }
        }

        return questions
    }
}
