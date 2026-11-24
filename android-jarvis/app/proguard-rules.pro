-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class com.jarvis.assistant.ai.** { *; }
-keep,includedescriptorclasses class com.jarvis.assistant.actions.** { *; }
