# JFrog Artifactory OSS

**JFrog Artifactory OSS** JFrog’s Artifactory open source project was created to speed up development cycles using binary repositories. It’s the world’s most advanced repository manager, creating a single place for teams to manage all their binary artifacts efficiently.

---

## 🛠️ Getting Started

### Prerequisites
- **Java** 25
- **Maven** 3.6+

---

## 🔧 Build the Project

```bash
mvn clean install
```

---

## ✅ Running Unit Tests

### Run Tests for Specific Modules

```bash
mvn test -pl base/api,base/base-rest
```

### Additional Options

- **Single Module**
  ```bash
  mvn test -pl base/api
  ```

- **Specific Test Class**
  ```bash
  mvn test -pl base/api -Dtest=YourTestClassName
  ```

---

## 🧪 Running PIT Mutation Tests

PIT mutation testing checks how effective your tests are by mutating code and checking if tests detect the changes.

### Basic Mutation Test Command

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage -Dthreads=4 -pl base/api,base/base-rest
```

### Advanced Usage

- **More parallelism**
  ```bash
  mvn test-compile org.pitest:pitest-maven:mutationCoverage -Dthreads=8 -pl base/api
  ```

- **Target specific packages**
  ```bash
  mvn test-compile org.pitest:pitest-maven:mutationCoverage -DtargetClasses=org.artifactory.* -pl base/api
  ```

---

## 📊 View PIT Reports

After running mutation tests, open the report in your browser:

```text
target/pit-reports/index.html
```

---