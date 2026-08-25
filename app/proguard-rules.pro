# Android components referenced from AndroidManifest.xml.
-keep class com.mazha0309.miaoassistant.service.GlobalInputAccessibilityService { *; }
-keep class com.mazha0309.miaoassistant.keepalive.KeepAliveService { *; }
-keep class com.mazha0309.miaoassistant.keepalive.BootReceiver { *; }

# Shizuku starts this class by its component name in a separate user-service process.
-keep class com.mazha0309.miaoassistant.privileged.InputInjectorUserService { public <init>(); public <init>(android.content.Context); *; }
-keep class com.mazha0309.miaoassistant.privileged.ClipboardShell { public static void main(java.lang.String[]); }
-keep class com.mazha0309.miaoassistant.privileged.ShellClipboardBridge { *; }
