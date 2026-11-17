plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories { mavenCentral() }

dependencies {
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    implementation("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // PDF generation and manipulation
    implementation("org.apache.pdfbox:pdfbox:3.0.4")

    // Security and cryptography
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.bouncycastle:bcutil-jdk18on:1.80")

    // QR code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // Metrics and monitoring
    implementation("io.micrometer:micrometer-core")

    // Testing dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.8.4")
}

tasks.bootJar { 
    archiveFileName.set("app.jar") 
}

// Configure test task
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)

    // Disable class data sharing
    jvmArgs("-Xshare:off")
    
    // Increase heap size for tests
    minHeapSize = "256m"
    maxHeapSize = "1g"
    
    // Show standard out/err from tests
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Classes excluded from coverage, shared by the report and the verification gate
val coverageExcludes = listOf(
    "**/Application.class",
    "**/FontTester.class",  // dev-only @Profile("fonttest") runner
    "**/config/**",
    "**/model/**",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }

    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } })
    )

    violationRules {
        // Whole project: lines and branches
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
        }

        // Every core service and controller class individually
        rule {
            element = "CLASS"
            includes = listOf("com.kousen.cert.service.*", "com.kousen.cert.controller.*")

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// Fail `./gradlew check` (and therefore `build`) when coverage drops below the rules above.
// Note: the full suite includes a Testcontainers/PostgreSQL test, so Docker must be running.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}