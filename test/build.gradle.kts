plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
//    mavenLocal()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

//    testImplementation("com.github.m4gshm:kubetestcontainers:0.1-SNAPSHOT")
    testImplementation(project(":"))
    val testcontainersVer = "1.19.7"
    testImplementation("org.testcontainers:jdbc:$testcontainersVer")
    testImplementation("org.testcontainers:postgresql:$testcontainersVer")
    testImplementation("org.testcontainers:mongodb:$testcontainersVer")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")//for org.testcontainers:postgresql:1.19.7

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.springframework.boot:spring-boot-test:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.1.3")
    testImplementation("org.springframework:spring-test:6.0.11")
    testImplementation("org.springframework:spring-orm:6.0.11")
    testImplementation("org.postgresql:postgresql:42.6.0")
    testImplementation("redis.clients:jedis:5.0.0")
    testImplementation("io.fabric8:kubernetes-client-api:6.8.1")

    testImplementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showCauses = true
        showStackTraces = true

        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

//        debug {
//            events = testLogging.events
//            exceptionFormat = testLogging.exceptionFormat
//        }
        info {
            events = testLogging.events
            exceptionFormat = testLogging.exceptionFormat
        }
    }
}