#!/usr/bin/env bash
# Generate Go bindings from Maven-generated proto files for any
# artifactory-oss/<domain>-event-api/ module.
#
# Driven by the reusable .github/workflows/event-api-build.yml (in the
# "Build and Publish Go Module" step). Can also be run locally from any
# event-api domain's go/ directory after a Maven build of the schema
# submodule has populated ../schema/target/generated-proto/.
#
# Usage:
#   generate-go-bindings.sh [<go-module-dir>]
#     <go-module-dir>: directory containing go.mod
#                      (defaults to $PWD)
#
# The script reads the Go module path from the go-module-dir's go.mod
# (the `module` line) and passes it to protoc-gen-go, so the per-domain
# go/ directory only carries go.mod + go.sum — no shell-script copy.
set -e

OUTPUT_DIR="${1:-$PWD}"
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd)"

if [[ ! -f "${OUTPUT_DIR}/go.mod" ]]; then
  echo "Error: ${OUTPUT_DIR}/go.mod not found." >&2
  echo "Pass an event-api domain's go/ directory as \$1 (or cd into it first)." >&2
  exit 1
fi

GO_MODULE=$(awk '/^module / {print $2; exit}' "${OUTPUT_DIR}/go.mod")
if [[ -z "${GO_MODULE}" ]]; then
  echo "Error: no 'module' declaration in ${OUTPUT_DIR}/go.mod" >&2
  exit 1
fi

PROTO_DIR="${OUTPUT_DIR}/../schema/target/generated-proto"
if [[ ! -d "${PROTO_DIR}" ]]; then
  echo "Error: proto files not found at ${PROTO_DIR}" >&2
  echo "Run Maven build first from $(dirname "${OUTPUT_DIR}"):" >&2
  echo "  mvnw clean install -Pdev -DskipTests" >&2
  exit 1
fi

# Install protoc-gen-go if not present (CI image typically already has it).
if ! command -v protoc-gen-go &> /dev/null; then
  go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
fi

echo "Generating Go bindings for module: ${GO_MODULE}"
echo "  Proto source: ${PROTO_DIR}"
echo "  Output dir:   ${OUTPUT_DIR}"

# find -print0 + xargs -0 to safely handle paths with special characters
find "${PROTO_DIR}" -name '*.proto' -print0 | xargs -0 protoc \
  --go_out="${OUTPUT_DIR}" \
  --go_opt=module="${GO_MODULE}" \
  --proto_path="${PROTO_DIR}"

echo "Generated files:"
find "${OUTPUT_DIR}" -name '*.pb.go' -type f
