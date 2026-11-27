plugins { id("com.android.application") }

android {
    namespace = "com.rioagent.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rioagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "2.2.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies { }
