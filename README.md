# Testcontainers on Kubernetes (under construction)

A tool to run testcontainers tests in a kubernetes environment.

Requires Java 17 or higher.

Compatibility with Testcontainers 1.19.7.

## Implementations

- [PostgresqlPod](./src/main/java/io/github/m4gshm/testcontainers/PostgresqlPod.java)

- [MongoDBPod](./src/main/java/io/github/m4gshm/testcontainers/MongoDBPod.java)

- [GenericPod](./src/main/java/io/github/m4gshm/testcontainers/GenericPod.java)

## Install

### Gradle (Kotlin syntax)

Add the code below to your `build.gradle.kts`

``` kotlin
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.github.m4gshm:kubetestcontainers:0.0.1-rc1")
    testImplementation("org.testcontainers:postgresql:1.19.7")
}
```

## Usage example

To run locally, it is highly recommended to use
[Minikube](https://minikube.sigs.k8s.io) or
[Kind](https://kind.sigs.k8s.io).

``` java
package example;

import io.github.m4gshm.testcontainers.PostgresqlPod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;

public class JdbcTest {

    private final static JdbcDatabaseContainer<?> postgres = useKubernetes()
            ? new PostgresqlPod() : new PostgreSQLContainer<>();

    private static boolean useKubernetes() {
        return "kuber".equals(System.getProperty("testcontainers-engine", "kuber"));
    }

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @Test
    public void jdbcInsertSelectTest() throws SQLException {
        var url = postgres.getJdbcUrl();
        var username = postgres.getUsername();
        var password = postgres.getPassword();
        try (var connection = getConnection(url, username, password)) {
            try (var statement = connection.prepareStatement("""
                    create table participant (
                        id integer,
                        name text
                    );
                    """
            )) {
                statement.execute();
            }

            try (var statement = connection.prepareStatement(
                    "insert into participant(id, name) values(?,?)")) {
                statement.setInt(1, 1);
                statement.setString(2, "Alice");
                statement.execute();
            }

            try (var statement = connection.prepareStatement(
                    "select name from participant where id=?")) {
                statement.setInt(1, 1);
                try (var resultSet = statement.executeQuery()) {
                    Assertions.assertTrue(resultSet.next());
                    var name = resultSet.getString(1);
                    Assertions.assertEquals("Alice", name);
                }
            }
        }
    }
}
```
