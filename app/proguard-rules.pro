# Quizzer release (R8) rules. Most libraries (Room, Moshi codegen, Retrofit, OkHttp, WorkManager,
# Compose) ship their own consumer rules; only app-specific additions go here.

# Readable stack traces in Play Console crash reports (upload mapping.txt with each release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi uses KSP-generated adapters for every JSON class. KotlinJsonAdapterFactory is only a
# reflective fallback; keep Kotlin metadata so it still works if a type without an adapter appears.
-keep class kotlin.Metadata { *; }

# JSON model classes (bundled questions, mistakes list, Open Trivia DB responses).
-keep class com.example.data.api.** { *; }
-keep class com.example.data.repository.BundledQuestionJson { *; }
-keep class com.example.data.repository.MistakeEntry { *; }
