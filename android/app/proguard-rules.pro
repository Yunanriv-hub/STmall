# fkTM —— 协议层不做混淆，避免字节级逻辑被优化改写（安全靠凭证加密，不靠混淆）
-keep class com.fktm.app.protocol.** { *; }
-keep class com.fktm.app.ble.** { *; }
-keep class com.fktm.app.credential.** { *; }

# Android components can be instantiated by the framework from the manifest.
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }

# Shizuku starts the UserService from an external process and resolves this
# concrete implementation by name. Keep the service and its AIDL stubs intact.
-keep class rikka.shizuku.** { *; }
-keep class com.fktm.app.ILogcatGrab { *; }
-keep class com.fktm.app.ILogcatGrab$* { *; }

# kotlinx.serialization uses generated serializers and requires annotations and
# generic signatures in this app. The serializer calls are explicit, but these
# attributes prevent release-only decoding failures after optimization.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Preserve NFC/BLE model and callback names used by diagnostics or reflection.
-keep class com.fktm.app.nfc.** { *; }
-keep class com.fktm.app.ujing.ble.** { *; }
-keep class com.fktm.app.ujing.net.ModelsKt$* { *; }
