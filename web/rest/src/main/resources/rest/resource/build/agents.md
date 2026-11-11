# Agent Instructions: Updating Artifactory Build Info OpenAPI Specification

This document provides instructions for LLM agents on how to maintain the `openapi.yaml` file when making changes to the Build Info API codebase.

## Project Structure

### Overview
The Artifactory Build Info API is part of JFrog Artifactory - a universal artifact repository manager. Build Info is the open integration layer between CI servers and Artifactory, allowing collection and management of build metadata, artifacts, and dependencies.

```
artifactory-service/
├── artifactory-oss/
│   └── web/
│       └── rest/
│           ├── src/main/java/org/artifactory/rest/
│           │   └── resource/
│           │       └── build/
│           │           └── BuildResource.java    # Build Info REST endpoints
│           └── src/main/resources/rest/resource/build/
│               ├── openapi.yaml                  # OpenAPI 3.1.0 specification (MANUALLY MAINTAINED)
│               ├── openapi-updater-config.yaml   # Auto-updater configuration
│               └── agents.md                     # This file
└── build/pom.xml                                 # Main POM
```

### Build System
- **Build Tool**: Maven 3.x
- **Java Version**: 25
- **Project Version**: 7.x-SNAPSHOT

**Build Commands:**
- `mvn clean install -DskipTests` - Build without running tests
- `mvn clean install -pl artifactory-oss/web/rest` - Build only REST module

## API Resource Classes

### Location
Build Info REST API resource class:
```
artifactory-oss/web/rest/src/main/java/org/artifactory/rest/resource/ci/BuildResource.java
```

### Main Resource Class

**BuildResource.java** (`@Path("build")`)
- Primary resource for Build Info operations
- Base path: `/api/build`
- Handles: build retrieval, upload, deletion, promotion, append, rename, retention
- Endpoints:
  - `/build` - All Builds (GET), Build Upload (PUT)
  - `/build/{buildName}` - Build Runs (GET), Delete Builds (DELETE)
  - `/build/{buildName}/{buildNumber}` - Build Info (GET)
  - `/build/promote/{buildName}/{buildNumber}` - Build Promotion (POST)
  - `/build/append/{buildName}/{buildNumber}` - Build Append (POST)
  - `/build/delete` - Delete Multiple Builds (POST, since 6.13)
  - `/build/rename/{buildName}` - Build Rename (POST)
  - `/build/retention/{buildName}` - Control Build Retention (POST)

### Resource Class Pattern
All Build Info resource methods follow this pattern:
- Annotated with `@GET`, `@POST`, `@PUT`, or `@DELETE` (JAX-RS)
- Use `@Path` for sub-paths (e.g., `@Path("/{buildName: .+}/{buildNumber: .+}")`)
- Use `@PathParam` for path variables
- Use `@QueryParam` for query parameters
- Use `@Produces` and `@Consumes` for content types
- Return `Response` objects with appropriate status codes
- Use `@RolesAllowed` for security constraints

## OpenAPI Specification File

### Location
```
artifactory-oss/web/rest/src/main/resources/rest/resource/build/openapi.yaml
```

### Key Characteristics
- **Format**: OpenAPI 3.1.0
- **Base Path**: `/artifactory/api`
- **Maintenance**: Manually maintained (NOT auto-generated from annotations)
- **Source of Truth**: Official JFrog REST API documentation at https://jfrog.com/help/r/jfrog-rest-apis/builds

### Structure
- **Tags**: `Builds`, `Docker`
- **Paths**: Build Info endpoints under `/build/*`
- **Schemas**: Build Info JSON schema and related models in `components/schemas/`
- **Security**: Bearer token (`bearerAuth`) and Basic authentication (`basicAuth`)

## How to Update openapi.yaml

### When to Update
You MUST update `openapi.yaml` when:
1. Adding a new endpoint to `BuildResource.java` (excluding internal directory)
2. Modifying an existing endpoint (path, method, parameters, request/response)
3. Removing an endpoint
4. Changing request/response structures
5. Adding or modifying query parameters
6. Changing response status codes

**CRITICAL EXCLUSION**: 
- **DO NOT** update `openapi.yaml` for changes to resource classes in directories named `internal/`
- These are internal APIs and **NOT** part of the public OpenAPI specification
- The script automatically excludes files in the `internal/` directory

### Update Process

#### Step 1: Identify the Endpoint in BuildResource.java
- Locate the Java method (e.g., `getAllBuilds()`, `promoteBuild()`)
- Note the `@Path` annotation value (e.g., `"/{buildName}/{buildNumber}"`)
- Note the class-level path: `@Path("build")`
- Identify the HTTP method: `@GET`, `@POST`, `@PUT`, or `@DELETE`

#### Step 2: Map Java to OpenAPI

**Path Mapping:**
- Class path + method path = final path
- Example: `@Path("build")` + `@Path("/promote/{buildName}/{buildNumber}")` 
  → OpenAPI path: `/build/promote/{buildName}/{buildNumber}`
- **Important**: Remove regex patterns from path parameters (e.g., `{buildName: .+}` → `{buildName}`)

**Method Mapping:**
- `@GET` → `get:`
- `@POST` → `post:`
- `@PUT` → `put:`
- `@DELETE` → `delete:`

**Operation ID:**
- Extract from method name (e.g., `getBuildInfo` → `operationId: getBuildInfo`)

#### Step 3: Map Parameters

**Path Parameters:**
Extract from `@PathParam` annotations:

**Java:**
```java
@GET
@Path("/{buildName: .+}/{buildNumber: .+}")
public Response getBuildInfo(
        @PathParam("buildName") String buildName,
        @PathParam("buildNumber") String buildNumber)
```

**OpenAPI:**
```yaml
parameters:
  - name: buildName
    in: path
    required: true
    schema:
      type: string
    description: Build name
  - name: buildNumber
    in: path
    required: true
    schema:
      type: string
    description: Build number
```

**Query Parameters:**
Extract from `@QueryParam` annotations:

**Java:**
```java
@QueryParam("started") String buildStarted,
@QueryParam("diff") String diffNumber,
@QueryParam("project") String projectKey
```

**OpenAPI:**
```yaml
parameters:
  - name: started
    in: query
    required: false
    schema:
      type: string
      format: date-time
    description: The timestamp of when the build started
  - name: diff
    in: query
    required: false
    schema:
      type: string
    description: The number of an older build to compare against
  - name: project
    in: query
    required: false
    schema:
      type: string
    description: The project associated with the build
```

#### Step 4: Map Request Body

For POST/PUT operations with request bodies:

**Java:**
```java
@POST
@Consumes(APPLICATION_JSON)
public Response promoteBuild(BuildPromotionRequest request)
```

**OpenAPI:**
```yaml
requestBody:
  required: true
  description: Build promotion request
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/BuildPromotionRequest'
```

#### Step 5: Map Responses

**Java:**
```java
@GET
@Produces({BuildRestConstants.MT_BUILD_INFO, APPLICATION_JSON})
public Response getBuildInfo(...)
```

**OpenAPI:**
```yaml
responses:
  '200':
    description: Successfully retrieved build info
    content:
      application/vnd.org.jfrog.build.BuildInfo+json:
        schema:
          $ref: '#/components/schemas/BuildInfo'
      application/json:
        schema:
          $ref: '#/components/schemas/BuildInfo'
  '404':
    description: Not Found - The specified build does not exist
  '401':
    description: Bad Credentials - Authentication failed
  '403':
    description: Permission Denied - Insufficient permissions
```

#### Step 6: Add Security Information

**CRITICAL**: All endpoints must include security in **TWO places**:

**1. In the description (markdown):**
```yaml
description: |
  Endpoint description here.
  
  **Since**: 2.2.0
  
  **Security**: Requires a privileged user. From version 6.6, requires read permission for the build.
```

**2. In the security section:**
```yaml
security:
  - bearerAuth: []
  - basicAuth: []
```

### Security Requirements by Operation Type

**Read Operations (GET):**
```
Requires a privileged user (can be anonymous). 
From version 6.6, requires read permission for the build or basic read.
```

**Upload/Deploy (PUT/POST create):**
```
Requires a privileged user. 
From version 6.6, requires deploy permission for the build, and delete permission for overriding existing build info artifact.
```

**Delete Operations:**
```
Requires a privileged user. 
From version 6.6, requires delete permission for the Build.
```

**Promotion:**
```
Requires Deploy permission for the build.
```

**Append:**
```
Requires Deploy and Delete permissions for the specified build.
```

**Rename:**
```
Requires deploy and delete permission for the build.
```

**Retention:**
```
Requires delete permission for the build.
```

#### Step 7: Use Official Content Types

Always use official JFrog MIME types in addition to `application/json`:

- `application/vnd.org.jfrog.build.Builds+json` - For list of builds
- `application/vnd.org.jfrog.build.BuildsByName+json` - For build runs
- `application/vnd.org.jfrog.build.BuildInfo+json` - For build info

**Example:**
```yaml
responses:
  '200':
    description: Successfully retrieved builds
    content:
      application/vnd.org.jfrog.build.Builds+json:
        schema:
          $ref: '#/components/schemas/AllBuildsResponse'
      application/json:
        schema:
          $ref: '#/components/schemas/AllBuildsResponse'
```

#### Step 8: Update Schemas Section

When adding new request/response models, create schema entries in `components/schemas/`:

**Example:**
```yaml
BuildPromotionRequest:
  type: object
  required:
    - ciUser
    - timestamp
  properties:
    status:
      type: string
      description: The new status of the build
    ciUser:
      type: string
      description: The user that invoked promotion from the CI server
    timestamp:
      type: string
      format: date-time
      description: Promotion time (ISO8601 format)
    targetRepo:
      type: string
      description: Target repository for promotion
```

### Type Mapping
- `String` → `type: string`
- `Integer`, `Long` → `type: integer`
- `Boolean` → `type: boolean`
- `Date`, timestamp → `type: string, format: date-time`
- `List<T>` → `type: array` with `items:`
- `Map<String, String>` → `type: object, additionalProperties: true`
- Custom class → `$ref: '#/components/schemas/ClassName'`

#### Step 9: Preserve Official Documentation

**CRITICAL**: When updating, preserve:
- Official JFrog documentation descriptions (word-for-word)
- "Since" version numbers
- Security requirements
- Examples from official documentation
- Response status code descriptions

**Source**: https://jfrog.com/help/r/jfrog-rest-apis/builds

### Example: Adding a New Endpoint

**Java Code:**
```java
@GET
@Path("/{buildName: .+}/{buildNumber: .+}/health")
@Produces(APPLICATION_JSON)
public Response getBuildHealth(
        @PathParam("buildName") String buildName,
        @PathParam("buildNumber") String buildNumber,
        @QueryParam("buildRepo") String buildRepo) {
    // Implementation
}
```

**OpenAPI Update:**
```yaml
/build/{buildName}/{buildNumber}/health:
  get:
    tags:
      - Builds
    summary: Get Build Health Status
    description: |
      Returns health status information for a specific build.
      
      **Since**: 7.x
      
      **Security**: Requires read permission for the build.
    operationId: getBuildHealth
    security:
      - bearerAuth: []
      - basicAuth: []
    parameters:
      - name: buildName
        in: path
        required: true
        schema:
          type: string
        description: Build name
      - name: buildNumber
        in: path
        required: true
        schema:
          type: string
        description: Build number
      - name: buildRepo
        in: query
        required: false
        schema:
          type: string
        description: Optional build repository name
    responses:
      '200':
        description: Build health status retrieved successfully
        content:
          application/json:
            schema:
              type: object
              properties:
                buildName:
                  type: string
                buildNumber:
                  type: string
                status:
                  type: string
      '404':
        description: Not Found - Build not found
      '401':
        description: Bad Credentials - Authentication failed
      '403':
        description: Permission Denied - Insufficient permissions
```

### Common Patterns

#### Pattern 1: List Endpoints
```java
@GET
public Response getAll(@QueryParam("projectKey") String projectKey)
```
→ Returns array of items in `200` response with official MIME type

#### Pattern 2: Get Single Item
```java
@GET
@Path("/{buildName}/{buildNumber}")
public Response getById(@PathParam("buildName") String name, ...)
```
→ Returns single item in `200` response, `404` if not found

#### Pattern 3: Create/Upload
```java
@PUT
@Consumes(APPLICATION_JSON)
public Response uploadBuild(BuildInfo buildInfo)
```
→ Returns `200` or `204`, `400` for validation errors

#### Pattern 4: Delete Item
```java
@DELETE
@Path("/{buildName}")
public Response delete(@PathParam("buildName") String name, ...)
```
→ Returns `200` with message on success, `404` if not found

#### Pattern 5: POST Operations (Promote, Append, Delete, etc.)
```java
@POST
@Path("/promote/{buildName}/{buildNumber}")
@Consumes(APPLICATION_JSON)
public Response promoteBuild(BuildPromotionRequest request)
```
→ Returns `200` with response body, various error codes

### Validation Checklist

Before completing an update, verify:

- [ ] All new endpoints are documented in `openapi.yaml`
- [ ] Paths match Java `@Path` annotations (without regex patterns like `.+`)
- [ ] HTTP methods match Java annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`)
- [ ] Operation IDs match Java method names
- [ ] All path parameters are documented with correct types
- [ ] All query parameters are documented
- [ ] Request body schemas exist and are correct (for POST/PUT)
- [ ] Response schemas exist and are correct
- [ ] Response status codes match Java code
- [ ] **Security description included in description field**
- [ ] **Security section with bearerAuth/basicAuth present**
- [ ] **"Since" version included (if known)**
- [ ] Official JFrog MIME types used (e.g., `application/vnd.org.jfrog.build.BuildInfo+json`)
- [ ] All schemas in `components/schemas/` are properly defined
- [ ] Tags correctly assigned (`Builds`, `Docker`)
- [ ] Descriptions match official JFrog documentation terminology
- [ ] No internal APIs are documented
- [ ] Indentation is consistent (2 spaces throughout)

### Important Notes

1. **Manual Maintenance**: The `openapi.yaml` file is manually maintained. Do NOT attempt to auto-generate it from annotations.

2. **Official Documentation**: Always reference the official JFrog REST API documentation at https://jfrog.com/help/r/jfrog-rest-apis/builds for:
   - Exact descriptions and summaries
   - Security requirements
   - "Since" version numbers
   - Request/response examples
   - Parameter descriptions

3. **Base Path**: The OpenAPI base path is `/artifactory/api`, defined in the `servers` section. Don't include it in individual path definitions.

4. **JAX-RS Patterns**: Path parameters with regex like `{buildName: .+}` - the `.+` is a regex pattern for matching, not part of the parameter name. Use only `{buildName}` in OpenAPI.

5. **Consistency**: Maintain consistency with existing OpenAPI structure, naming conventions, and response formats.

6. **Security**: ALL endpoints must have:
   - Security description in the `description` field (markdown)
   - `security` section specifying both `bearerAuth` and `basicAuth`

7. **Content Types**: Always include official JFrog MIME types plus `application/json` fallback:
   ```yaml
   content:
     application/vnd.org.jfrog.build.Builds+json:
       schema: ...
     application/json:
       schema: ...
   ```

8. **Indentation**: Use consistent 2-space indentation throughout. Do NOT mix 2-space and 4-space indentation or use tabs.

9. **Response Terminology**: Use official JFrog terminology:
   - `401` → "Bad Credentials" (not "Unauthorized")
   - `403` → "Permission Denied" (not "Forbidden")

10. **Build Info Schema**: The `BuildInfo` schema must conform to the official Build Info JSON schema with required fields: `version`, `name`, `number`, `started`.

## File Reference

### Key Files to Check When Updating

1. **API Resource:**
   - `artifactory-oss/web/rest/src/main/java/org/artifactory/rest/resource/ci/BuildResource.java`

2. **OpenAPI Spec:**
   - `artifactory-oss/web/rest/src/main/resources/rest/resource/build/openapi.yaml`

3. **Configuration:**
   - `artifactory-oss/web/rest/src/main/resources/rest/resource/build/openapi-updater-config.yaml`

4. **Official Documentation:**
   - https://jfrog.com/help/r/jfrog-rest-apis/builds

## Summary

When making API changes to BuildResource.java:
1. Update the Java resource class
2. **IMMEDIATELY** update `openapi.yaml` to reflect the changes
3. Add security information in both description and security section
4. Use official JFrog MIME types
5. Preserve official JFrog documentation content
6. Ensure consistency between code and OpenAPI spec
7. Verify all schemas, parameters, and responses are correctly documented
8. Use consistent 2-space indentation
9. Exclude any internal APIs

The OpenAPI specification is a critical part of the API contract and must be kept in sync with the BuildResource.java implementation and official JFrog documentation at all times.
