# `artifactory-event-api-parent`

Shared parent POM for the family of `artifactory-oss/<domain>-event-api/` Maven modules. Each domain (currently `index-event-api/`, `stats-event-api/`, future `<domain>-event-api/`) inherits its plugin pluginManagement, plugin versions, and JUnit/protoc dependency versions from this parent.

## Contract for adding a new event domain

To stand up a new event domain `xyz`, create `artifactory-oss/xyz-event-api/` with the structure below. Each per-domain pom shrinks to ~30 lines because all plugin executions live in this parent.

### 1. Top-level domain pom (`artifactory-oss/xyz-event-api/pom.xml`)

```xml
<parent>
    <groupId>org.artifactory</groupId>
    <artifactId>artifactory-event-api-parent</artifactId>
    <version>1.x-SNAPSHOT</version>
    <relativePath>../event-api-parent</relativePath>
</parent>

<artifactId>artifactory-xyz-event-api</artifactId>
<packaging>pom</packaging>
<version>1.x-SNAPSHOT</version>

<properties>
    <java.package.name>com.jfrog.artifactory.eventapi.xyz</java.package.name>
    <go.package.name>jfrog.com/jfrog-artifactory-xyz-event/xyzevent</go.package.name>
    <proto.module.artifactId>package-xyz-event-proto</proto.module.artifactId>
</properties>

<modules>
    <module>schema</module>
    <module>classes</module>
</modules>
```

The three properties are the only per-domain knobs: java + go package names (consumed by the inherited replacer-plugin token) and the proto module artifactId (consumed by the inherited maven-dependency-plugin `unpack-protos` execution).

The Java package convention is `com.jfrog.artifactory.eventapi.<domain>`, mirroring the Go-side `jfrog.com/jfrog-artifactory-<domain>-event/<domain>event`. (`index-event-api/` predates this convention and stays on `com.jfrog.artifactory.packageindexed`.)

### 2. Schema submodule (`artifactory-oss/xyz-event-api/schema/pom.xml`)

```xml
<parent>
    <groupId>org.artifactory</groupId>
    <artifactId>artifactory-xyz-event-api</artifactId>
    <version>1.x-SNAPSHOT</version>
</parent>

<artifactId>package-xyz-event-proto</artifactId>
<packaging>jar</packaging>

<properties>
    <openapi.inputSpec>${project.basedir}/src/main/resources/public/openapi/xyz_event_openapi_schema.yaml</openapi.inputSpec>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.openapitools</groupId>
            <artifactId>openapi-generator-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>com.google.code.maven-replacer-plugin</groupId>
            <artifactId>replacer</artifactId>
        </plugin>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-assembly-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

`openapi.inputSpec` cannot move to the parent because `${project.basedir}` resolves to the using project (the schema module), not the parent. It stays here. The four `<plugin>` declarations are stub activations — version + executions + configuration are inherited from the parent's `<pluginManagement>`.

Drop in `src/main/resources/public/openapi/xyz_event_openapi_schema.yaml` (the OpenAPI schema) and `src/assembly/zip.xml` (copy from a sibling — currently identical across domains).

### 3. Classes submodule (`artifactory-oss/xyz-event-api/classes/pom.xml`)

```xml
<parent>
    <groupId>org.artifactory</groupId>
    <artifactId>artifactory-xyz-event-api</artifactId>
    <version>1.x-SNAPSHOT</version>
</parent>

<artifactId>package-xyz-event-java</artifactId>
<packaging>jar</packaging>

<dependencies>
    <dependency>
        <groupId>org.artifactory</groupId>
        <artifactId>${proto.module.artifactId}</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>${protobuf.version}</version>
    </dependency>
    <!-- JUnit Jupiter test dependencies as needed -->
</dependencies>

<build>
    <plugins>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId></plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId></plugin>
        <plugin><groupId>io.github.ascopes</groupId><artifactId>protobuf-maven-plugin</artifactId></plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId></plugin>
    </plugins>
</build>
```

### 4. Go module subdirectory (`artifactory-oss/xyz-event-api/go/`)

Just `go.mod` — declares `module jfrog.com/jfrog-artifactory-xyz-event` and the `google.golang.org/protobuf` dependency. The proto-to-Go generation is driven by the shared `event-api-parent/scripts/generate-go-bindings.sh` (called from the reusable workflow). No per-domain shell script. To run locally:

```sh
cd artifactory-oss/xyz-event-api && mvnw clean install -Pdev -DskipTests   # populate ../schema/target/generated-proto/
artifactory-oss/event-api-parent/scripts/generate-go-bindings.sh artifactory-oss/xyz-event-api/go
```

The script reads the Go module path from the `go.mod`'s `module` line — no per-domain configuration anywhere.

### 5. CI workflow (`.github/workflows/xyz-event-api.yml`)

Thin caller of the reusable workflow at `.github/workflows/event-api-build.yml`. Copy from `index-event-api.yml` or `stats-event-api.yml` and substitute the four caller inputs (`service-name`, `working-directory`, `java-component-path`, `proto-zip-name`).

## Verification

From `artifactory-oss/xyz-event-api/`, run `mvnw clean install -Pdev` (drop `-DskipTests` if classes/ has unit tests). Expect BUILD SUCCESS. The generated `.proto` under `schema/target/generated-proto/models/` should carry `option java_package = "${your.java.package.name}"` and `option go_package = "${your.go.package.name}"` — verify these match what was set in the top-level pom.

## Why this parent exists

Before the extraction (PR #24106), each domain had ~272 lines of identical Maven and CI boilerplate. Adopting this parent shrinks each new domain to ~133 lines and centralises dependency-version bumps (one place to bump `protobuf.version`, `next-gen-ci-java@vX.Y`, JUnit, etc.). See PR #24106 for the full extraction rationale.
