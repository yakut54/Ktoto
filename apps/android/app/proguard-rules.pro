# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson / JSON models
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class ru.yakut54.ktoto.data.model.** { *; }
-keep class ru.yakut54.ktoto.data.remote.dto.** { *; }

# Socket.IO
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Coil
-dontwarn coil.**

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* {
   <fields>;
}
