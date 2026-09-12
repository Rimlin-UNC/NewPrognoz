# Правила R8 для «Метео-Аналитик» (minifyEnabled = true).

# ---------- Retrofit ----------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---------- OkHttp ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Moshi (codegen: @JsonClass(generateAdapter = true)) ----------
# Сгенерированные адаптеры находятся автоматически; DTO достаточно оставить
# с сохранёнными именами полей для читаемых логов.
-keepnames @com.squareup.moshi.JsonClass class *

# ---------- Coroutines ----------
-dontwarn kotlinx.coroutines.debug.**

# ---------- Приложение ----------
# Модели данных используются Room/энсамблем только напрямую, обфускация безопасна.
