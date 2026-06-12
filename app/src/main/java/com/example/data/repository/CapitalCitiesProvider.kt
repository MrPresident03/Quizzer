package com.example.data.repository

import com.example.data.model.Question
import java.util.Locale
import kotlin.random.Random

object CapitalCitiesProvider {

    data class CountryCapital(
        val country: String,
        val capital: String,
        val continent: String,
        val difficulty: String // "easy", "medium", "hard"
    )

    val capitals = listOf(
        // --- EASY DIFFICULTIES ---
        CountryCapital("United States", "Washington, D.C.", "Americas", "easy"),
        CountryCapital("United Kingdom", "London", "Europe", "easy"),
        CountryCapital("Canada", "Ottawa", "Americas", "easy"),
        CountryCapital("Australia", "Canberra", "Oceania", "easy"),
        CountryCapital("Japan", "Tokyo", "Asia", "easy"),
        CountryCapital("Germany", "Berlin", "Europe", "easy"),
        CountryCapital("France", "Paris", "Europe", "easy"),
        CountryCapital("Italy", "Rome", "Europe", "easy"),
        CountryCapital("Brazil", "Brasília", "Americas", "easy"),
        CountryCapital("India", "New Delhi", "Asia", "easy"),
        CountryCapital("South Africa", "Pretoria", "Africa", "easy"),
        CountryCapital("China", "Beijing", "Asia", "easy"),
        CountryCapital("Mexico", "Mexico City", "Americas", "easy"),
        CountryCapital("Spain", "Madrid", "Europe", "easy"),
        CountryCapital("Russia", "Moscow", "Europe/Asia", "easy"),
        CountryCapital("South Korea", "Seoul", "Asia", "easy"),
        CountryCapital("Saudi Arabia", "Riyadh", "Asia", "easy"),
        CountryCapital("Argentina", "Buenos Aires", "Americas", "easy"),
        CountryCapital("Greece", "Athens", "Europe", "easy"),
        CountryCapital("Turkey", "Ankara", "Europe/Asia", "easy"),
        CountryCapital("Egypt", "Cairo", "Africa/Asia", "easy"),
        CountryCapital("Sweden", "Stockholm", "Europe", "easy"),
        CountryCapital("Switzerland", "Bern", "Europe", "easy"),
        CountryCapital("Netherlands", "Amsterdam", "Europe", "easy"),
        CountryCapital("Indonesia", "Jakarta", "Asia", "easy"),
        CountryCapital("Iran", "Tehran", "Asia", "easy"),
        CountryCapital("Iraq", "Baghdad", "Asia", "easy"),

        // --- MEDIUM DIFFICULTIES ---
        CountryCapital("Chile", "Santiago", "Americas", "medium"),
        CountryCapital("Colombia", "Bogotá", "Americas", "medium"),
        CountryCapital("Peru", "Lima", "Americas", "medium"),
        CountryCapital("Vietnam", "Hanoi", "Asia", "medium"),
        CountryCapital("Thailand", "Bangkok", "Asia", "medium"),
        CountryCapital("Philippines", "Manila", "Asia", "medium"),
        CountryCapital("Malaysia", "Kuala Lumpur", "Asia", "medium"),
        CountryCapital("Singapore", "Singapore", "Asia", "medium"),
        CountryCapital("New Zealand", "Wellington", "Oceania", "medium"),
        CountryCapital("Ukraine", "Kyiv", "Europe", "medium"),
        CountryCapital("Poland", "Warsaw", "Europe", "medium"),
        CountryCapital("Norway", "Oslo", "Europe", "medium"),
        CountryCapital("Finland", "Helsinki", "Europe", "medium"),
        CountryCapital("Denmark", "Copenhagen", "Europe", "medium"),
        CountryCapital("Ireland", "Dublin", "Europe", "medium"),
        CountryCapital("Austria", "Vienna", "Europe", "medium"),
        CountryCapital("Belgium", "Brussels", "Europe", "medium"),
        CountryCapital("Portugal", "Lisbon", "Europe", "medium"),
        CountryCapital("Hungary", "Budapest", "Europe", "medium"),
        CountryCapital("Czechia", "Prague", "Europe", "medium"),
        CountryCapital("Romania", "Bucharest", "Europe", "medium"),
        CountryCapital("Cuba", "Havana", "Americas", "medium"),
        CountryCapital("Morocco", "Rabat", "Africa", "medium"),
        CountryCapital("Kenya", "Nairobi", "Africa", "medium"),
        CountryCapital("Nigeria", "Abuja", "Africa", "medium"),
        CountryCapital("Ghana", "Accra", "Africa", "medium"),
        CountryCapital("Ethiopia", "Addis Ababa", "Africa", "medium"),
        CountryCapital("Afghanistan", "Kabul", "Asia", "medium"),
        CountryCapital("Pakistan", "Islamabad", "Asia", "medium"),
        CountryCapital("Bangladesh", "Dhaka", "Asia", "medium"),
        CountryCapital("Venezuela", "Caracas", "Americas", "medium"),

        // --- HARD DIFFICULTIES ---
        CountryCapital("Andorra", "Andorra la Vella", "Europe", "hard"),
        CountryCapital("Liechtenstein", "Vaduz", "Europe", "hard"),
        CountryCapital("San Marino", "San Marino", "Europe", "hard"),
        CountryCapital("Monaco", "Monaco", "Europe", "hard"),
        CountryCapital("Malta", "Valletta", "Europe", "hard"),
        CountryCapital("Cyprus", "Nicosia", "Europe", "hard"),
        CountryCapital("Luxembourg", "Luxembourg", "Europe", "hard"),
        CountryCapital("Estonia", "Tallinn", "Europe", "hard"),
        CountryCapital("Latvia", "Riga", "Europe", "hard"),
        CountryCapital("Lithuania", "Vilnius", "Europe", "hard"),
        CountryCapital("Ecuador", "Quito", "Americas", "hard"),
        CountryCapital("Bolivia", "Sucre", "Americas", "hard"),
        CountryCapital("Paraguay", "Asunción", "Americas", "hard"),
        CountryCapital("Uruguay", "Montevideo", "Americas", "hard"),
        CountryCapital("Madagascar", "Antananarivo", "Africa", "hard"),
        CountryCapital("Iceland", "Reykjavik", "Europe", "hard"),
        CountryCapital("Mongolia", "Ulaanbaatar", "Asia", "hard"),
        CountryCapital("Nepal", "Kathmandu", "Asia", "hard"),
        CountryCapital("Sri Lanka", "Sri Jayawardenepura Kotte", "Asia", "hard"),
        CountryCapital("Kazakhstan", "Astana", "Asia", "hard"),
        CountryCapital("Bhutan", "Thimphu", "Asia", "hard"),
        CountryCapital("Myanmar", "Naypyidaw", "Asia", "hard"),
        CountryCapital("Cambodia", "Phnom Penh", "Asia", "hard"),
        CountryCapital("Laos", "Vientiane", "Asia", "hard"),
        CountryCapital("Brunei", "Bandar Seri Begawan", "Asia", "hard"),
        CountryCapital("Uzbekistan", "Tashkent", "Asia", "hard"),
        CountryCapital("Kyrgyzstan", "Bishkek", "Asia", "hard"),
        CountryCapital("Tajikistan", "Dushanbe", "Asia", "hard"),
        CountryCapital("Turkmenistan", "Ashgabat", "Asia", "hard"),
        CountryCapital("Armenia", "Yerevan", "Asia", "hard"),
        CountryCapital("Azerbaijan", "Baku", "Asia", "hard"),
        CountryCapital("Georgia", "Tbilisi", "Asia/Europe", "hard"),
        CountryCapital("Albania", "Tirana", "Europe", "hard"),
        CountryCapital("Seychelles", "Victoria", "Africa", "hard"),
        CountryCapital("Mauritius", "Port Louis", "Africa", "hard"),
        CountryCapital("Senegal", "Dakar", "Africa", "hard"),
        CountryCapital("Ivory Coast", "Yamoussoukro", "Africa", "hard"),
        CountryCapital("Zimbabwe", "Harare", "Africa", "hard"),
        CountryCapital("Fiji", "Suva", "Oceania", "hard"),
        CountryCapital("Papua New Guinea", "Port Moresby", "Oceania", "hard")
    )

    fun generateCapitalQuiz(difficulty: String, count: Int): List<Question> {
        val questions = mutableListOf<Question>()

        // Filter based on selected difficulty tier
        val targetPool = when (difficulty.lowercase(Locale.getDefault())) {
            "easy" -> capitals.filter { it.difficulty == "easy" }
            "medium" -> capitals.filter { it.difficulty == "medium" }
            "hard" -> capitals.filter { it.difficulty == "hard" }
            else -> capitals // "mixed"
        }

        val sourcePool = if (targetPool.size < 4) capitals else targetPool
        val shuffledPool = sourcePool.shuffled()

        val qty = if (count == 999) 40 else count
        val finalCount = qty.coerceAtMost(shuffledPool.size)

        for (i in 0 until finalCount) {
            val target = shuffledPool[i]

            // Deciding direction: What is the capital of X? OR Which country has Y as capital?
            val askCapitalOfCountry = Random.nextBoolean()

            if (askCapitalOfCountry) {
                val wrongOptions = capitals.filter { it.capital != target.capital }
                    .shuffled()
                    .take(3)
                    .map { it.capital }

                val options = (wrongOptions + target.capital).shuffled()
                val correctIndex = options.indexOf(target.capital)

                questions.add(
                    Question(
                        text = "What is the official capital city of ${target.country}?",
                        optionsList = options,
                        correctIndex = correctIndex,
                        category = "Capital Cities Challenge",
                        difficulty = target.difficulty,
                        explanation = "The capital of ${target.country} is ${target.capital}."
                    )
                )
            } else {
                val wrongOptions = capitals.filter { it.country != target.country }
                    .shuffled()
                    .take(3)
                    .map { it.country }

                val options = (wrongOptions + target.country).shuffled()
                val correctIndex = options.indexOf(target.country)

                questions.add(
                    Question(
                        text = "Which country has ${target.capital} as its official capital city?",
                        optionsList = options,
                        correctIndex = correctIndex,
                        category = "Capital Cities Challenge",
                        difficulty = target.difficulty,
                        explanation = "${target.capital} is the capital city of ${target.country}."
                    )
                )
            }
        }

        return questions
    }
}
