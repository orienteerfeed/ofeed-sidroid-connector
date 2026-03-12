# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Avoid R8 minification/reflection issues when using Gson to parse Json.
-keep class com.orienteerfeed.ofeed_sidroid_connector.ResultsService$OFeedEvent { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.ResultsService$OFeedResults { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.ResultsService$OFeedData { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.ResultsService$OResultsEvent { *; }

-keep class com.orienteerfeed.ofeed_sidroid_connector.GetEventName$OFeedEvent { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.GetEventName$OFeedResults { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.GetEventName$OFeedData { *; }
-keep class com.orienteerfeed.ofeed_sidroid_connector.GetEventName$OResultsEvent { *; }