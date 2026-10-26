# dnsjava's compile-time generated-code marker is not a runtime dependency.
-dontwarn lombok.Generated
# SLF4J 1.7 explicitly falls back to its bundled no-op logger when no optional
# binding exists. DNS payloads must not be added to Android's shared log here.
-dontwarn org.slf4j.impl.StaticLoggerBinder
# The C probe engine calls this method by its JVM name through GetMethodID.
# Keep implementations as well as the callback contract, including SAM lambdas.
-keepclassmembers class * implements dev.broadband.toolbox.NativeCallback {
    public void onNativeEvent(java.lang.String, java.lang.String);
}
