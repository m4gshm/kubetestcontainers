plugins {
    `java-library`
}

repositories {
    mavenCentral()
//    mavenLocal()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    testImplementation("org.projectlombok:lombok:1.18.28")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.28")

//    testImplementation("com.github.m4gshm:kubetestcontainers:0.1-SNAPSHOT")
    testImplementation(project(":"))
    testImplementation("org.testcontainers:postgresql:1.19.0")
    testImplementation("org.testcontainers:mongodb:1.19.0")

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

    testImplementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    testImplementation("org.testcontainers:k3s:1.19.0")
}

tasks.test {
    useJUnitPlatform()
}