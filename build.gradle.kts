import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.susshi"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localRubyMine = System.getenv("RUBYMINE_HOME")
        if (localRubyMine != null) local(localRubyMine) else rubymine("2026.1.3")
        bundledPlugin("org.jetbrains.plugins.ruby")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

intellijPlatform {
    pluginConfiguration {
        name = "Rails View"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
    signing {
        certificateChainFile = file("chain.crt").takeIf { it.exists() }
            ?: file(System.getenv("CERTIFICATE_CHAIN") ?: "chain.crt")
        privateKeyFile = file("private.pem").takeIf { it.exists() }
            ?: file(System.getenv("PRIVATE_KEY") ?: "private.pem")
        password = System.getenv("PRIVATE_KEY_PASSWORD") ?: ""
    }
    publishing {
        token = System.getenv("PUBLISH_TOKEN") ?: ""
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
