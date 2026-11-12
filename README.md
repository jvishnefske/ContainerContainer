# ContainerContainer

[![ci](https://github.com/jvishnefske/ContainerContainer/actions/workflows/ci.yml/badge.svg)](https://github.com/jvishnefske/ContainerContainer/actions/workflows/ci.yml)
[![integration](https://github.com/jvishnefske/ContainerContainer/actions/workflows/integration.yml/badge.svg)](https://github.com/jvishnefske/ContainerContainer/actions/workflows/integration.yml)

A fork of JFrog Artifactory OSS 7.161.26, built from JFrog's published AGPL-3.0 sources (the first commit is the
unmodified release). It is not affiliated with or endorsed by JFrog. The fork adds:

- A Groovy user-plugin engine (`download`, `storage`, `executions` and `realms` blocks) in place of the no-op OSS stub.
- Plugin-defined repository package types, riding on Generic repositories, with download, upload, index and full
  HTTP `api` handlers whose permissions are enforced in Java before plugin code runs.
- An OCI / Docker registry written as a plugin (`plugins/oci/`), passing the OCI distribution-spec v1.1.0
  conformance suite.
- OIDC token federation as a plugin (`plugins/oidc/`), targeting an Auth0 issuer: JWT bearer and JWT-as-password
  login, claim-to-user mapping, and exchange for short-lived Artifactory tokens.

Requirements and the tests that demonstrate each one are in `design.md`. Every commit is built and unit-tested by
the `ci` workflow; the `integration` workflow builds an overlay image (the stock
`releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26` image with the fork's jars swapped in) and runs the live
scripts in `docker/it` against throwaway containers. The fork is distributed under the GNU Affero General Public
License v3; see `LICENSE`.

The upstream README follows.

---

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