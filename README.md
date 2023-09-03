# Testcontainers on kubernetes (under construction)

A tool to run testcontainers tests in a kubernetes environment.

Goal is Postgresql and Redis support.

- to build run `./gradlew publishToMavenLocal`
- add next code in your `gradle.kts` script:
    ```gradle
    repositories {
        mavenLocal()
    }
    dependencies {
        testImplementation("com.github.m4gshm:kubetestcontainers:1.0-SNAPSHOT")
        testImplementation("org.testcontainers:postgresql:1.19.0")
    }
    ```
- start experimenting.

See tests as an example [here](./src/test/java/com/github/m4gshm/testcontainers).

And don't forget to install [Minikube](https://kubernetes.io/ru/docs/tasks/tools/install-minikube/).