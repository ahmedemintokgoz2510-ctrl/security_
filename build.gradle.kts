// Root build.gradle.kts - Sadece plugin yönetimi, android config YOK
plugins {
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Root'ta android { } config YOK - sadece app/build.gradle.kts'de olmalı
