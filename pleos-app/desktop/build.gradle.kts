plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/kotlin/kr/tunnelfree/app/domain")
        resources.srcDir("../app/src/main/assets")
    }
}

application {
    mainClass.set("kr.tunnelfree.desktop.MainKt")
}
