# Index Event API Classes

## Overview
This module contains Java classes generated from Protocol Buffer definitions.

## Build Process

1. **Proto File Source**
   - Proto files are sourced from `package-index-event-proto` dependency

2. **Code Generation**
   - Protocol Buffer compiler processes `.proto` files
   - Generates corresponding Java source files

3. **Compilation**
   - Generated Java sources are compiled into `.class` files

4. **Packaging**
   - Final JAR artifact contains only compiled classes
   - Original proto files and generated sources are not included

## Dependencies
- Requires `package-index-event-proto` for Protocol Buffer definitions
- Uses Protocol Buffer compiler for Java code generation

## Build Output
The build process produces a JAR containing only the compiled classes needed for the Index Event API.