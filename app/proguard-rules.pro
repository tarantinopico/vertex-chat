# ProGuard / R8 configuration for Vertex Chat

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Kotlinx Serialization generated serializers
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep WorkManager Workers
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp & Security Crypto rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.crypto.tink.**
