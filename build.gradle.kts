plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp").version("0.0.4")
    id("org.asciidoctor.jvm.convert") version "4.0.1"
}

group = "io.github.m4gshm"
version = "0.0.1-rc1"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.28")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("org.testcontainers:testcontainers:1.19.1")
    compileOnly("org.testcontainers:jdbc:1.19.1")
    compileOnly("org.testcontainers:postgresql:1.19.1")
    compileOnly("org.testcontainers:mongodb:1.19.0")

    implementation("io.fabric8:kubernetes-client:6.8.1")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.awaitility:awaitility:4.2.0")
    implementation("org.apache.commons:commons-lang3:3.13.0")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    modularity.inferModulePath.set(true)
}

tasks.asciidoctor {
    dependsOn(":test:classes")
    baseDirFollowsSourceFile()
    outputOptions {
        backends("docbook")
    }
}

tasks.create<Exec>("pandoc") {
    dependsOn("asciidoctor")
    group = "documentation"
    commandLine = "pandoc -f docbook -t gfm $buildDir/docs/asciidoc/readme.xml -o $rootDir/README.md".split(" ")
}

tasks.build {
    if (properties["no-pandoc"] == null) {
        dependsOn("pandoc")
    }
}

publishing {
    publications {
        create<MavenPublication>("java") {
            pom {
                description.set("Like test containers, but using Kubernetes")
                url.set("https://github.com/m4gshm/kubetestcontainers")
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
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/m4gshm/kubetestcontainers?tab=MIT-1-ov-file#readme")
                    }
                }
            }
            from(components["java"])
        }
    }
    repositories {
        maven("file://$rootDir/../m4gshm.github.io/maven2") {
            name = "GithubMavenRepo"
        }
    }
}

signing {
    val extension = extensions.getByName("publishing") as PublishingExtension
    sign(extension.publications)
}

nmcp {
    publishAllProjectsProbablyBreakingProjectIsolation {
        val ossrhUsername = project.properties["ossrhUsername"] as String?
        val ossrhPassword = project.properties["ossrhPassword"] as String?
        username.set(ossrhUsername)
        password.set(ossrhPassword)
        publicationType = "USER_MANAGED"
//        publicationType = "AUTOMATIC"
    }
}