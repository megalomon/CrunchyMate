// Sicherheits-Mindestversionen für den Plugin-Classpath (AGP, Kotlin, ktlint-Plugin und deren
// transitive Abhängigkeiten). Die Versionen stehen im Versionskatalog (Präfix "sec"); der
// buildscript-Block sieht den Katalog nicht, deshalb wird die TOML-Datei direkt gelesen.
buildscript {
    val catalogVersions =
        file("gradle/libs.versions.toml")
            .readLines()
            .takeWhile { it.trim() != "[libraries]" }
            .mapNotNull { Regex("""^(sec\w+)\s*=\s*"([^"]+)"""").find(it)?.destructured }
            .associate { (name, version) -> name to version }

    fun securityVersion(name: String) = requireNotNull(catalogVersions[name]) { "Version $name fehlt im Katalog" }

    dependencies {
        constraints {
            val bouncycastle = securityVersion("secBouncycastle")
            classpath("org.bouncycastle:bcprov-jdk18on:$bouncycastle")
            classpath("org.bouncycastle:bcpkix-jdk18on:$bouncycastle")
            classpath("com.google.guava:guava:${securityVersion("secGuava")}")
            classpath("org.bitbucket.b_c:jose4j:${securityVersion("secJose4j")}")
            classpath("org.jdom:jdom2:${securityVersion("secJdom2")}")
            classpath("org.apache.commons:commons-lang3:${securityVersion("secCommonsLang3")}")
            classpath("org.apache.httpcomponents:httpclient:${securityVersion("secHttpclient")}")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}
