# kotlinx.serialization: keep generated serializers for event payloads / settings.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.watchthetime.** {
    *** Companion;
}
-keepclasseswithmembers class com.watchthetime.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.watchthetime.**$$serializer { *; }
