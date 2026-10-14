package com.example.data.repository

import com.example.data.model.Question
import java.util.Locale
import kotlin.random.Random

object FlagQuestionsProvider {

    data class CountryFlag(
        val name: String, 
        val flag: String, 
        val continent: String,
        val difficulty: String // "easy", "medium", "hard"
    ) {
        /** Primary continent, so "Europe/Asia" countries are grouped with Europe for distractors. */
        val region: String get() = continent.substringBefore('/')
    }

    val countries = listOf(
        // --- EASY DIFFICULTIES (Well-known countries / iconic flags) ---
        CountryFlag("United States", "🇺🇸", "Americas", "easy"),
        CountryFlag("United Kingdom", "🇬🇧", "Europe", "easy"),
        CountryFlag("Canada", "🇨🇦", "Americas", "easy"),
        CountryFlag("Australia", "🇦🇺", "Oceania", "easy"),
        CountryFlag("Japan", "🇯🇵", "Asia", "easy"),
        CountryFlag("Germany", "🇩🇪", "Europe", "easy"),
        CountryFlag("France", "🇫🇷", "Europe", "easy"),
        CountryFlag("Italy", "🇮🇹", "Europe", "easy"),
        CountryFlag("Brazil", "🇧🇷", "Americas", "easy"),
        CountryFlag("India", "🇮🇳", "Asia", "easy"),
        CountryFlag("South Africa", "🇿🇦", "Africa", "easy"),
        CountryFlag("China", "🇨🇳", "Asia", "easy"),
        CountryFlag("Mexico", "🇲🇽", "Americas", "easy"),
        CountryFlag("Spain", "🇪🇸", "Europe", "easy"),
        CountryFlag("Russia", "🇷🇺", "Europe/Asia", "easy"),
        CountryFlag("South Korea", "🇰🇷", "Asia", "easy"),
        CountryFlag("Singapore", "🇸🇬", "Asia", "easy"),
        CountryFlag("New Zealand", "🇳🇿", "Oceania", "easy"),
        CountryFlag("Switzerland", "🇨🇭", "Europe", "easy"),
        CountryFlag("Netherlands", "🇳🇱", "Europe", "easy"),
        CountryFlag("Sweden", "🇸🇪", "Europe", "easy"),
        CountryFlag("Norway", "🇳🇴", "Europe", "easy"),
        CountryFlag("Denmark", "🇩🇰", "Europe", "easy"),
        CountryFlag("Finland", "🇫🇮", "Europe", "easy"),
        CountryFlag("Ireland", "🇮🇪", "Europe", "easy"),
        CountryFlag("Austria", "🇦🇹", "Europe", "easy"),
        CountryFlag("Belgium", "🇧🇪", "Europe", "easy"),
        CountryFlag("Greece", "🇬🇷", "Europe", "easy"),
        CountryFlag("Portugal", "🇵🇹", "Europe", "easy"),
        CountryFlag("Turkey", "🇹🇷", "Europe/Asia", "easy"),
        CountryFlag("Egypt", "🇪🇬", "Africa/Asia", "easy"),
        CountryFlag("Saudi Arabia", "🇸🇦", "Asia", "easy"),
        CountryFlag("United Arab Emirates", "🇦🇪", "Asia", "easy"),
        CountryFlag("Argentina", "🇦🇷", "Americas", "easy"),
        CountryFlag("Cuba", "🇨🇺", "Americas", "easy"),
        CountryFlag("Thailand", "🇹🇭", "Asia", "easy"),
        CountryFlag("Philippines", "🇵🇭", "Asia", "easy"),
        CountryFlag("Ukraine", "🇺🇦", "Europe", "easy"),
        CountryFlag("Poland", "🇵🇱", "Europe", "easy"),
        CountryFlag("Israel", "🇮🇱", "Asia", "easy"),

        // --- MEDIUM DIFFICULTIES (Moderately known sovereign states) ---
        CountryFlag("Chile", "🇨🇱", "Americas", "medium"),
        CountryFlag("Colombia", "🇨🇴", "Americas", "medium"),
        CountryFlag("Peru", "🇵🇪", "Americas", "medium"),
        CountryFlag("Vietnam", "🇻🇳", "Asia", "medium"),
        CountryFlag("Malaysia", "🇲🇾", "Asia", "medium"),
        CountryFlag("Indonesia", "🇮🇩", "Asia", "medium"),
        CountryFlag("Iceland", "🇮🇸", "Europe", "medium"),
        CountryFlag("Kenya", "🇰🇪", "Africa", "medium"),
        CountryFlag("Nigeria", "🇳🇬", "Africa", "medium"),
        CountryFlag("Morocco", "🇲🇦", "Africa", "medium"),
        CountryFlag("Jamaica", "🇯🇲", "Americas", "medium"),
        CountryFlag("Costa Rica", "🇨🇷", "Americas", "medium"),
        CountryFlag("Czechia", "🇨🇿", "Europe", "medium"),
        CountryFlag("Hungary", "🇭🇺", "Europe", "medium"),
        CountryFlag("Romania", "🇷🇴", "Europe", "medium"),
        CountryFlag("Croatia", "🇭🇷", "Europe", "medium"),
        CountryFlag("Slovakia", "🇸🇰", "Europe", "medium"),
        CountryFlag("Bulgaria", "🇧🇬", "Europe", "medium"),
        CountryFlag("Serbia", "🇷🇸", "Europe", "medium"),
        CountryFlag("Algeria", "🇩🇿", "Africa", "medium"),
        CountryFlag("Tunisia", "🇹🇳", "Africa", "medium"),
        CountryFlag("Ethiopia", "🇪🇹", "Africa", "medium"),
        CountryFlag("Ghana", "🇬🇭", "Africa", "medium"),
        CountryFlag("Iran", "🇮🇷", "Asia", "medium"),
        CountryFlag("Iraq", "🇮🇶", "Asia", "medium"),
        CountryFlag("Pakistan", "🇵🇰", "Asia", "medium"),
        CountryFlag("Bangladesh", "🇧🇩", "Asia", "medium"),
        CountryFlag("Sri Lanka", "🇱🇰", "Asia", "medium"),
        CountryFlag("Nepal", "🇳🇵", "Asia", "medium"),
        CountryFlag("Jordan", "🇯🇴", "Asia", "medium"),
        CountryFlag("Lebanon", "🇱🇧", "Asia", "medium"),
        CountryFlag("Kuwait", "🇰🇼", "Asia", "medium"),
        CountryFlag("Qatar", "🇶🇦", "Asia", "medium"),
        CountryFlag("Oman", "🇴🇲", "Asia", "medium"),
        CountryFlag("Venezuela", "🇻🇪", "Americas", "medium"),
        CountryFlag("Ecuador", "🇪🇨", "Americas", "medium"),
        CountryFlag("Bolivia", "🇧🇴", "Americas", "medium"),
        CountryFlag("Uruguay", "🇺🇾", "Americas", "medium"),
        CountryFlag("Paraguay", "🇵🇾", "Americas", "medium"),
        CountryFlag("Panama", "🇵🇦", "Americas", "medium"),
        CountryFlag("Guatemala", "🇬🇹", "Americas", "medium"),
        CountryFlag("Honduras", "🇭🇳", "Americas", "medium"),
        CountryFlag("El Salvador", "🇸🇻", "Americas", "medium"),
        CountryFlag("Bahamas", "🇧🇸", "Americas", "medium"),
        CountryFlag("Dominican Republic", "🇩🇴", "Americas", "medium"),
        CountryFlag("Madagascar", "🇲🇬", "Africa", "medium"),
        CountryFlag("Senegal", "🇸🇳", "Africa", "medium"),
        CountryFlag("Ivory Coast", "🇨🇮", "Africa", "medium"),
        CountryFlag("Cameroon", "🇨🇲", "Africa", "medium"),
        CountryFlag("Zimbabwe", "🇿🇼", "Africa", "medium"),

        // --- HARD DIFFICULTIES (More obscure, less recognizable / small countries / territory flags) ---
        CountryFlag("Andorra", "🇦🇩", "Europe", "hard"),
        CountryFlag("Liechtenstein", "🇱🇮", "Europe", "hard"),
        CountryFlag("San Marino", "🇸🇲", "Europe", "hard"),
        CountryFlag("Monaco", "🇲🇨", "Europe", "hard"),
        CountryFlag("Malta", "🇲🇹", "Europe", "hard"),
        CountryFlag("Cyprus", "🇨🇾", "Europe", "hard"),
        CountryFlag("Luxembourg", "🇱🇺", "Europe", "hard"),
        CountryFlag("Estonia", "🇪🇪", "Europe", "hard"),
        CountryFlag("Latvia", "🇱🇻", "Europe", "hard"),
        CountryFlag("Lithuania", "🇱🇹", "Europe", "hard"),
        CountryFlag("Moldova", "🇲🇩", "Europe", "hard"),
        CountryFlag("Belarus", "🇧🇾", "Europe", "hard"),
        CountryFlag("Albania", "🇦🇱", "Europe", "hard"),
        CountryFlag("North Macedonia", "🇲🇰", "Europe", "hard"),
        CountryFlag("Montenegro", "🇲🇪", "Europe", "hard"),
        CountryFlag("Bosnia and Herzegovina", "🇧🇦", "Europe", "hard"),
        CountryFlag("Georgia", "🇬🇪", "Europe/Asia", "hard"),
        CountryFlag("Armenia", "🇦🇲", "Asia", "hard"),
        CountryFlag("Azerbaijan", "🇦🇿", "Asia/Europe", "hard"),
        CountryFlag("Kazakhstan", "🇰🇿", "Asia", "hard"),
        CountryFlag("Uzbekistan", "🇺🇿", "Asia", "hard"),
        CountryFlag("Kyrgyzstan", "🇰🇬", "Asia", "hard"),
        CountryFlag("Tajikistan", "🇹🇯", "Asia", "hard"),
        CountryFlag("Turkmenistan", "🇹🇲", "Asia", "hard"),
        CountryFlag("Mongolia", "🇲🇳", "Asia", "hard"),
        CountryFlag("Bhutan", "🇧🇹", "Asia", "hard"),
        CountryFlag("Myanmar", "🇲🇲", "Asia", "hard"),
        CountryFlag("Cambodia", "🇰🇭", "Asia", "hard"),
        CountryFlag("Laos", "🇱🇦", "Asia", "hard"),
        CountryFlag("Brunei", "🇧🇳", "Asia", "hard"),
        CountryFlag("Timor-Leste", "🇹🇱", "Asia", "hard"),
        CountryFlag("Maldives", "🇲🇻", "Asia", "hard"),
        CountryFlag("Papua New Guinea", "🇵🇬", "Oceania", "hard"),
        CountryFlag("Fiji", "🇫🇯", "Oceania", "hard"),
        CountryFlag("Solomon Islands", "🇸🇧", "Oceania", "hard"),
        CountryFlag("Vanuatu", "🇻🇺", "Oceania", "hard"),
        CountryFlag("Samoa", "🇼🇸", "Oceania", "hard"),
        CountryFlag("Tonga", "🇹🇴", "Oceania", "hard"),
        CountryFlag("Kiribati", "🇰🇮", "Oceania", "hard"),
        CountryFlag("Tuvalu", "🇹🇻", "Oceania", "hard"),
        CountryFlag("Nauru", "🇳🇷", "Oceania", "hard"),
        CountryFlag("Palau", "🇵🇼", "Oceania", "hard"),
        CountryFlag("Micronesia", "🇫🇲", "Oceania", "hard"),
        CountryFlag("Marshall Islands", "🇲🇭", "Oceania", "hard"),
        CountryFlag("Seychelles", "🇸🇨", "Africa", "hard"),
        CountryFlag("Mauritius", "🇲🇺", "Africa", "hard"),
        CountryFlag("Comoros", "🇰🇲", "Africa", "hard"),
        CountryFlag("Cape Verde", "🇨🇻", "Africa", "hard"),
        CountryFlag("Sao Tome and Principe", "🇸🇹", "Africa", "hard"),
        CountryFlag("Equatorial Guinea", "🇬🇶", "Africa", "hard"),
        CountryFlag("Gabon", "🇬🇦", "Africa", "hard"),
        CountryFlag("Republic of the Congo", "🇨🇬", "Africa", "hard"),
        CountryFlag("Democratic Republic of the Congo", "🇨🇩", "Africa", "hard"),
        CountryFlag("Central African Republic", "🇨🇫", "Africa", "hard"),
        CountryFlag("South Sudan", "🇸🇸", "Africa", "hard"),
        CountryFlag("Sudan", "🇸🇩", "Africa", "hard"),
        CountryFlag("Eritrea", "🇪🇷", "Africa", "hard"),
        CountryFlag("Djibouti", "🇩🇯", "Africa", "hard"),
        CountryFlag("Somalia", "🇸🇴", "Africa", "hard"),
        CountryFlag("Rwanda", "🇷🇼", "Africa", "hard"),
        CountryFlag("Burundi", "🇧🇮", "Africa", "hard"),
        CountryFlag("Uganda", "🇺🇬", "Africa", "hard"),
        CountryFlag("Tanzania", "🇹🇿", "Africa", "hard"),
        CountryFlag("Malawi", "🇲🇼", "Africa", "hard"),
        CountryFlag("Zambia", "🇿🇲", "Africa", "hard"),
        CountryFlag("Angola", "🇦🇴", "Africa", "hard"),
        CountryFlag("Mozambique", "🇲🇿", "Africa", "hard"),
        CountryFlag("Botswana", "🇧🇼", "Africa", "hard"),
        CountryFlag("Namibia", "🇳🇦", "Africa", "hard"),
        CountryFlag("Lesotho", "🇱🇸", "Africa", "hard"),
        CountryFlag("Eswatini", "🇸🇿", "Africa", "hard"),
        CountryFlag("Belize", "🇧🇿", "Americas", "hard"),
        CountryFlag("Nicaragua", "🇳🇮", "Americas", "hard"),
        CountryFlag("Barbados", "🇧🇧", "Americas", "hard"),
        CountryFlag("Dominica", "🇩🇲", "Americas", "hard"),
        CountryFlag("Grenada", "🇬🇩", "Americas", "hard"),
        CountryFlag("Saint Lucia", "🇱🇨", "Americas", "hard"),
        CountryFlag("Saint Vincent and the Grenadines", "🇻🇨", "Americas", "hard"),
        CountryFlag("Antigua and Barbuda", "🇦🇬", "Americas", "hard"),
        CountryFlag("Saint Kitts and Nevis", "🇰🇳", "Americas", "hard"),
        CountryFlag("Trinidad and Tobago", "🇹🇹", "Americas", "hard"),
        CountryFlag("Guyana", "🇬🇾", "Americas", "hard"),
        CountryFlag("Suriname", "🇸🇷", "Americas", "hard"),
        CountryFlag("Guinea", "🇬🇳", "Africa", "hard"),
        CountryFlag("Guinea-Bissau", "🇬🇼", "Africa", "hard"),
        CountryFlag("Sierra Leone", "🇸🇱", "Africa", "hard"),
        CountryFlag("Liberia", "🇱🇷", "Africa", "hard"),
        CountryFlag("Mali", "🇲🇱", "Africa", "hard"),
        CountryFlag("Burkina Faso", "🇧🇫", "Africa", "hard"),
        CountryFlag("Niger", "🇳🇪", "Africa", "hard"),
        CountryFlag("Chad", "🇹🇩", "Africa", "hard"),
        CountryFlag("Mauritania", "🇲🇷", "Africa", "hard"),
        CountryFlag("Yemen", "🇾🇪", "Asia", "hard"),
        CountryFlag("Syria", "🇸🇾", "Asia", "hard"),
        CountryFlag("Afghanistan", "🇦🇫", "Asia", "hard")
    )

    fun flagToCountryText(country: CountryFlag) = "Which country belongs to this flag?\n\n${country.flag}"
    fun countryToFlagText(country: CountryFlag) = "Identify the correct flag for the country:\n\n${country.name}"

    /**
     * @param lastSeen returns when a question key (see [QuestionPicker.keyOf]) was last shown, or null.
     *   Countries you haven't been asked about recently are picked first, so repeated flag quizzes
     *   cycle through the whole pool instead of re-drawing the same flags.
     */
    fun generateFlagQuiz(
        mode: String,
        difficulty: String,
        count: Int,
        lastSeen: (String) -> Long? = { null },
        random: Random = Random.Default
    ): List<Question> {
        val targetPool = when (difficulty.lowercase(Locale.ROOT)) {
            "easy", "medium", "hard" -> countries.filter { it.difficulty == difficulty.lowercase(Locale.ROOT) }
            else -> countries // "Mixed"
        }
        val sourcePool = if (targetPool.size < 4) countries else targetPool

        val qty = if (count == 999) 40 else count
        val targets = QuestionPicker.pickFresh(
            candidates = sourcePool,
            count = qty,
            lastSeen = { country ->
                listOfNotNull(
                    lastSeen(QuestionPicker.keyOf(flagToCountryText(country))),
                    lastSeen(QuestionPicker.keyOf(countryToFlagText(country)))
                ).maxOrNull()
            },
            random = random
        )

        return targets.map { target ->
            val currentMode = when (mode) {
                "flag_to_country", "country_to_flag" -> mode
                else -> if (random.nextBoolean()) "flag_to_country" else "country_to_flag"
            }

            // Distractors come from the same continent when possible, so the answer isn't obvious
            // just from geography (e.g. three European flags and one Pacific one).
            val sameRegion = countries.filter { it.name != target.name && it.region == target.region }
            val distractorPool = if (sameRegion.size >= 3) sameRegion else countries.filter { it.name != target.name }
            val distractors = distractorPool.shuffled(random).take(3)

            if (currentMode == "flag_to_country") {
                val options = (distractors.map { it.name } + target.name).shuffled(random)
                Question(
                    text = flagToCountryText(target),
                    optionsList = options,
                    correctIndex = options.indexOf(target.name),
                    category = "Flags: Flag to Country",
                    difficulty = target.difficulty,
                    explanation = "This is the national flag of ${target.name} (${target.continent})."
                )
            } else {
                val options = (distractors.map { it.flag } + target.flag).shuffled(random)
                Question(
                    text = countryToFlagText(target),
                    optionsList = options,
                    correctIndex = options.indexOf(target.flag),
                    category = "Flags: Country to Flag",
                    difficulty = target.difficulty,
                    explanation = "${target.flag} belongs to ${target.name} (${target.continent})."
                )
            }
        }
    }
}
