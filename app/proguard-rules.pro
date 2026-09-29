# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.streetblocks.app.**$$serializer { *; }
-keepclassmembers class com.streetblocks.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.streetblocks.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.streetblocks.app.data.model.** { *; }
