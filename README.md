# Testcontainers on kubernetes (under construction)

A tool to run testcontainers tests in a kubernetes environment.

Requires Java 17 or higher.

## Install

### Gradle (Kotlin syntax)

Add the code below to your `build.gradle.kts`

``` kotlin
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("com.github.m4gshm:kubetestcontainers:0.1-rc1")
    testImplementation("org.testcontainers:postgresql:1.19.0")
}
```

## Usage example

To run locally, it is highly recommended to use Minikube or Kind.

``` java
package example;

import io.github.m4gshm.testcontainers.PostgresqlPod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.SQLException;

import static java.sql.DriverManager.getConnection;

public class JdbcTest {

    protected static JdbcDatabaseContainer<?> postgres = new PostgresqlPod()
            .withLocalPortForwardHost("localhost");

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

            try (var statement = connection.prepareStatement("insert into participant(id, name) values(?,?)")) {
                statement.setInt(1, 1);
                statement.setString(2, "Alice");
                statement.execute();
            }

            try (var statement = connection.prepareStatement("select name from participant where id=?")) {
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
