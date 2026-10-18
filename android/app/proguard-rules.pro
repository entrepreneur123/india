# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.lottodost.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
