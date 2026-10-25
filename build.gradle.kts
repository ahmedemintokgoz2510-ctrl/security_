// Root build.gradle.kts - Android plugin YOK
plugins {
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Root android config (opsiyonel)
android {
    namespace = "com.samsungsync.datatransfer"
    compileSdk = 35
}
