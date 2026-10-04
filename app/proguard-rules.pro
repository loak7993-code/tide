# ─────────────────────────────────────────────────────────────────────────────
# Tide — R8 / ProGuard configuration
#
# R8 runs in full mode (AGP 8+ default). Full mode does not keep unused
# library rules, so anything below that survives shrinking must be stated
# explicitly. The rules here are grouped by the failure they prevent.
# ─────────────────────────────────────────────────────────────────────────────

# ── kotlinx.serialization ────────────────────────────────────────────────────
# The plugin generates a `Companion.serializer()` and resolves nested
# serializers reflectively through the companion. Shrinking a `@Serializable`
# class renames it out from under that lookup.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class app.tide.launcher.data.**$$serializer { *; }
-keep,includedescriptorclasses class app.tide.launcher.data.**$Companion { *; }
-keep,includedescriptorclasses class app.tide.launcher.data.** { *; }

# ── DataStore ────────────────────────────────────────────────────────────────
# Preferences DataStore ships a protobuf schema whose generated classes are
# only reached through generated code; the generic entry point survives, the
# descriptors do not.
-keep class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# ── Enums persisted by name ──────────────────────────────────────────────────
# Theme and layout settings round-trip through DataStore as strings. Enum
# constant names are the wire format, so renaming one silently resets a user's
# setting on upgrade.
-keepclassmembers enum app.tide.launcher.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries;
}

# ── Compose ──────────────────────────────────────────────────────────────────
# Compose ships its own consumer rules, but `ui-test-manifest` and the tooling
# artifacts pull in classes the optimizer cannot see a use for.
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** {
    <init>();
}

# ── Kotlin metadata ──────────────────────────────────────────────────────────
# Coroutine state machines and Compose lambdas rely on @Metadata for
# debugging and for `when` exhaustiveness after optimization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ── Platform ─────────────────────────────────────────────────────────────────
# Palette reads color data reflectively off a Bitmap on some OEM builds.
-dontwarn androidx.palette.**

# Parcelables crossing process boundaries in launcher intents.
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep line numbers so a stack trace from a minified release is still readable,
# while hiding the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Diagnostics ──────────────────────────────────────────────────────────────
# Strip verbose logging from release builds without touching the call sites.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}