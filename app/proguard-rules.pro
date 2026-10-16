# R8 / release optimization rules

# --- Room: keep generated impl + entities/DAOs (referenced by generated code) ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.rainweaver.moneymgr.data.** { *; }
-dontwarn androidx.room.paging.**

# --- Keep annotations & signatures (Kotlin/Room reflection-free but safe) ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# --- Kotlin coroutines (ships consumer rules; keep service loader entries) ---
-dontwarn kotlinx.coroutines.**

# Keep native methods and enums (Compose/Lifecycle may use valueOf)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
