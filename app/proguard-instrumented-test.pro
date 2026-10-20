# Added ONLY when instrumented tests run against the release build (-PtestBuildType=release).
# The test APK links against classes inside the minified app (the Kotlin standard library the
# AndroidX test runner uses, and the app classes the tests call), so their names must not be
# obfuscated or stripped. PdfBox shrinking and keep rules are exactly as shipped.
-dontobfuscate
-keep class kotlin.** { *; }
-keep class io.github.fortunatechick.attendease.parser.** { *; }
-keep class io.github.fortunatechick.attendease.data.PdfTextExtractor { *; }
-keep class io.github.fortunatechick.attendease.data.PdfTextExtractor$* { *; }
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class io.github.fortunatechick.attendease.calc.** { *; }
-keep class io.github.fortunatechick.attendease.Formats { *; }
-keep class io.github.fortunatechick.attendease.R$* { *; }
