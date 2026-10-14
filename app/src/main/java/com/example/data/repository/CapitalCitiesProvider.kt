package com.example.data.repository

import com.example.data.model.Question
import java.util.Locale
import kotlin.random.Random

object CapitalCitiesProvider {

    data class CountryCapital(
        val country: String,
        val capital: String,
        val continent: String,
        val difficulty: String, // "easy", "medium", "hard"
        val note: String? = null
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
        CountryCapital("South Africa", "Pretoria", "Africa", "easy", "South Africa has three capitals: Pretoria (executive), Cape Town (legislative) and Bloemfontein (judicial)."),
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
        CountryCapital("Netherlands", "Amsterdam", "Europe", "easy", "Amsterdam is the constitutional capital, but the government and parliament sit in The Hague."),
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
        CountryCapital("Bolivia", "Sucre", "Americas", "hard", "Sucre is the constitutional capital; La Paz is the seat of government."),
        CountryCapital("Paraguay", "Asunción", "Americas", "hard"),
        CountryCapital("Uruguay", "Montevideo", "Americas", "hard"),
        CountryCapital("Madagascar", "Antananarivo", "Africa", "hard"),
        CountryCapital("Iceland", "Reykjavik", "Europe", "hard"),
        CountryCapital("Mongolia", "Ulaanbaatar", "Asia", "hard"),
        CountryCapital("Nepal", "Kathmandu", "Asia", "hard"),
        CountryCapital("Sri Lanka", "Sri Jayawardenepura Kotte", "Asia", "hard", "Sri Jayawardenepura Kotte is the legislative capital; Colombo is the commercial capital."),
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
        CountryCapital("Ivory Coast", "Yamoussoukro", "Africa", "hard", "Yamoussoukro is the official capital, while Abidjan is the largest city and economic centre."),
        CountryCapital("Zimbabwe", "Harare", "Africa", "hard"),
        CountryCapital("Fiji", "Suva", "Oceania", "hard"),
        CountryCapital("Papua New Guinea", "Port Moresby", "Oceania", "hard")
    )

    fun capitalOfCountryText(c: CountryCapital) = "What is the official capital city of ${c.country}?"
    fun countryOfCapitalText(c: CountryCapital) = "Which country has ${c.capital} as its official capital city?"

    /** "Which country has Singapore as its capital?" gives the answer away, so only ask it one way. */
    private fun capitalSharesCountryName(c: CountryCapital) =
        c.capital.contains(c.country, ignoreCase = true) || c.country.contains(c.capital, ignoreCase = true)

    /**
     * @param lastSeen returns when a question key (see [QuestionPicker.keyOf]) was last shown, or null.
     *   Countries not asked about recently are picked first.
     */
    fun generateCapitalQuiz(
        difficulty: String,
        count: Int,
        lastSeen: (String) -> Long? = { null },
        random: Random = Random.Default
    ): List<Question> {
        val targetPool = when (difficulty.lowercase(Locale.ROOT)) {
            "easy", "medium", "hard" -> capitals.filter { it.difficulty == difficulty.lowercase(Locale.ROOT) }
            else -> capitals // "Mixed"
        }
        val sourcePool = if (targetPool.size < 4) capitals else targetPool

        val qty = if (count == 999) 40 else count
        val targets = QuestionPicker.pickFresh(
            candidates = sourcePool,
            count = qty,
            lastSeen = { c ->
                listOfNotNull(
                    lastSeen(QuestionPicker.keyOf(capitalOfCountryText(c))),
                    lastSeen(QuestionPicker.keyOf(countryOfCapitalText(c)))
                ).maxOrNull()
            },
            random = random
        )

        return targets.map { target ->
            val explanation = buildString {
                append("The capital of ${target.country} is ${target.capital}.")
                target.note?.let { append(" ").append(it) }
            }
            val askCapitalOfCountry = capitalSharesCountryName(target) || random.nextBoolean()

            if (askCapitalOfCountry) {
                val wrongOptions = capitals.filter { it.capital != target.capital }
                    .shuffled(random)
                    .take(3)
                    .map { it.capital }
                val options = (wrongOptions + target.capital).shuffled(random)
                Question(
                    text = capitalOfCountryText(target),
                    optionsList = options,
                    correctIndex = options.indexOf(target.capital),
                    category = Categories.CAPITAL_CITIES,
                    difficulty = target.difficulty,
                    explanation = explanation
                )
            } else {
                val wrongOptions = capitals.filter { it.country != target.country }
                    .shuffled(random)
                    .take(3)
                    .map { it.country }
                val options = (wrongOptions + target.country).shuffled(random)
                Question(
                    text = countryOfCapitalText(target),
                    optionsList = options,
                    correctIndex = options.indexOf(target.country),
                    category = Categories.CAPITAL_CITIES,
                    difficulty = target.difficulty,
                    explanation = explanation
                )
            }
        }
    }
}
