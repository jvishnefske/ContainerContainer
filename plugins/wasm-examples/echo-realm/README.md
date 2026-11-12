# echo-realm

An example WASM plugin for the plugin runtime, written in Rust for the `wasm32-wasip1` target with
`#![forbid(unsafe_code)]`. It speaks ABI version 1 (`plugins/wasm-abi/abi-v1.schema.json`): for each step the host
writes one JSON envelope to the module's stdin and the module writes one JSON reply to stdout. The module exercises
every hook, both effects and plugin state, and the Java tests run the committed build of it as a fixture.

## Insecure by design

echo-realm is a demonstration and a test fixture, not a security realm. It accepts any JWT-shaped bearer token whose
payload names the configured issuer and never checks the signature, so anyone can forge a token for it. It also
accepts the password `echo:<username>` for every user. Never install it on a server that holds anything of value.

## What it does

The configuration comes from the manifest's `config`: the issuer it claims, the groups it gives users, the URL it
fetches and the lifetime of the tokens it mints.

For bearer tokens, a token from another issuer is "not mine". Otherwise the token's subject decides the outcome. A
subject `ok:<user>` authenticates that user, with the token's `exp` as the expiry. A subject `fetch:<user>` first asks
the host for an HTTPS GET of the configured URL and authenticates the user only if the answer is a 200 whose body is
`allow`; otherwise it rejects. A subject `reject:<x>` is rejected. A subject `loop:<x>` spins forever, to show the
host's step deadline, and `trap:<x>` panics, to show that traps fail closed. Any other subject is "not mine".

For username and password logins, the password `echo:<username>` authenticates that user and anything else is "not
mine". Authenticated users always get the configured groups and are created on first login.

The `echoMint` execution asks the host to mint an access token for the caller, never outliving the caller's own
plugin-realm expiry, and answers 200 with the token, its lifetime, the caller's name and the call counter as JSON, or
403 with the host's error when minting was refused. Any other execution name answers 404.

The module keeps a call counter in its plugin state and increments it on every call that ends. Malformed input never
crashes it: a malformed realm request is "not mine" and anything else malformed answers 400. It logs one line per step
to stderr with the hook and step number, never a token or password.

## Building

Run `./build.sh` from this directory. It needs only Docker: it builds in the Rust image pinned by digest in the script,
runs the unit tests on the native target, builds the release module, and refreshes the fixture
`backend/core/src/test/resources/wasm/echo-realm.wasm` and its `echo-realm.wasm.sha256`. Commit both together with
any source change.

The build is bit-reproducible: the toolchain comes from the pinned image (and `rust-toolchain.toml` names the same
version), dependencies are locked by `Cargo.lock`, the source and Cargo home paths are remapped to fixed names, and
debug info and symbols are stripped.

## Reproducibility check in CI

The `wasm-example` CI job runs `bash plugins/wasm-examples/echo-realm/build.sh --check`, which runs the unit tests,
builds the module from source in a temporary directory and fails unless its sha256 equals the committed
`echo-realm.wasm.sha256`. A source change without a refreshed fixture, or a fixture that does not come from this
source, fails the job.

## Installing

Copy `echo-realm.wasm` (the fixture above) and `echo-realm.wasm.yaml` from this directory into the Artifactory
plugins directory, then load them with `POST /api/plugins/reload`, for example
`curl -u admin -X POST https://<server>/artifactory/api/plugins/reload`. The manifest's comments explain each field;
change `config`, the allowed hosts and the limits to suit your test setup.
