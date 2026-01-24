# Shadow Master ProGuard Rules

# Keep Azure Speech SDK classes
-keep class com.microsoft.cognitiveservices.speech.** { *; }

# Keep Silero VAD classes
-keep class com.konovalov.vad.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep data classes for DataStore
-keep class com.shadowmaster.data.model.** { *; }

# Standard Android rules
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
