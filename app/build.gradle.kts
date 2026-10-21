plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.luma.torch"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.android.adds"; minSdk = 29; targetSdk = 34; versionCode = 6; versionName = "1.0"
    }
    // שום ספרייה נייטיבית (.so) לא נארזת ב-APK; ה-AAR מכיל גם libonnxruntime.so של x86.
    // libsherpa-onnx-jni.so היא נטענת מהקושחה (lib/arm64 ליד ה-APK ב-priv-app),
    // ראה firmware/README-he.md. ה-AAR נשאר רק לקומפילציה של מחלקות ה-Java.
    packaging { jniLibs { excludes += "**/*.so" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // זיהוי דיבור אופליין. את הקובץ מורידים עם prepare-runtime.sh לפני הבנייה (הוא לא נכנס ל-Git).
    // המודל עצמו (~72MB) אינו חלק מהאפליקציה: הוא יושב בקושחה, ראה firmware/README-he.md
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))
}
