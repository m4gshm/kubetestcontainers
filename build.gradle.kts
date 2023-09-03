plugins {
    `java-library`
    `maven-publish`
}

group = "com.github.m4gshm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    compileOnly("org.projectlombok:lombok:1.18.28")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.28")

    implementation("org.testcontainers:testcontainers:1.19.0")
    compileOnly("org.testcontainers:jdbc:1.19.0")
    compileOnly("org.testcontainers:postgresql:1.19.0")

    implementation("io.fabric8:kubernetes-client:6.8.1")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.awaitility:awaitility:4.2.0")
    implementation("org.apache.commons:commons-lang3:3.13.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:jdbc:1.19.0")
    testImplementation("org.testcontainers:postgresql:1.19.0")

    testImplementation("org.springframework.boot:spring-boot-test:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa:3.1.3")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.1.3")
    testImplementation("org.springframework:spring-test:6.0.11")
    testImplementation("org.springframework:spring-orm:6.0.11")
    testImplementation("org.postgresql:postgresql:42.6.0")

    testImplementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    modularity.inferModulePath.set(true)
}

publishing {
    publications {
        create<MavenPublication>("java") {
            pom {
                properties.put("maven.compiler.target", "${java.targetCompatibility}")
                properties.put("maven.compiler.source", "${java.sourceCompatibility}")
                developers {
                    developer {
                        id.set("m4gshm")
                        name.set("Bulgakov Alexander")
                        email.set("mfourgeneralsherman@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/m4gshm/kubetestcontainers.git")
                    developerConnection.set("scm:git:https://github.com/m4gshm/kubetestcontainers.git")
                    url.set("https://github.com/m4gshm/kubetestcontainers")
                }
            }
            from(components["java"])
        }
    }
}