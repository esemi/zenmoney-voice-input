# keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class dev.esemi.zmvoice.**$$serializer { *; }
-keepclassmembers class dev.esemi.zmvoice.** {
    *** Companion;
}
-keepclasseswithmembers class dev.esemi.zmvoice.** {
    kotlinx.serialization.KSerializer serializer(...);
}
