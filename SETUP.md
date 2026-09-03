# Setup

Same toolchain as Labs 1 and 2. Nothing new to install. JUnit comes in through Maven.

## 1. Java and Maven

- A JDK, version 21 or newer. Check with `java --version`.
- Maven 3.8 or newer. Check with `mvn --version`.

## 2. Build and run the tests

From this directory:

```
mvn test
```

Everything should be green. The first run downloads JUnit, so it may take a moment.

To watch the service work, run the demo script:

```
mvn compile
java -cp target/classes edu.cmu.cs214.roomreserve.ReservationApp
```

## 3. Editor

Any editor or IDE that imports a Maven project.
