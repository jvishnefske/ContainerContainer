# Package Index Event API

This project generates Protocol Buffer (`.proto`) files from OpenAPI specifications and packages them into a ZIP archive for easy distribution and consumption.

## 🏗️ Build Process

### 1. Generate Proto Files and ZIP
```bash
# From the index-event-api directory
mvnw clean package
```

This command:
- Generates `.proto` files from the OpenAPI specification
- Creates a JAR file (primary artifact)
- Creates a ZIP file containing the generated resources

**Output files:**
- `target/package-index-event-proto-1.0.0-SNAPSHOT.jar` - Main JAR artifact
- `target/package-index-event-proto-1.0.0-SNAPSHOT-resources.zip` - Resources ZIP

### 2. What's in the ZIP?
The ZIP contains:
- Generated `.proto` files from OpenAPI
- Any additional resources from the project
- Clean file structure without base directories

### 3. Upgrade Version
The version is HARD-CODED in the `pom.xml` file.
To upgrade the version of the generated artifacts, update the `<version>` tag in the `pom.xml` file.



