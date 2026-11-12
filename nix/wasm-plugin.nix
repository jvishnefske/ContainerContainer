# lib.buildWasmPlugin: builds a Rust crate for wasm32-wasip1 and installs $out/<pname>.wasm (plus
# $out/<pname>.wasm.yaml when `manifest` is given), the file pair the plugins directory takes.
#
# Toolchain: a prebuilt rustc from oxalica/rust-overlay (pinned in flake.lock) with the wasm32-wasip1 standard
# library; that target is self-contained (bundled wasi-libc and rust-lld), so no C cross toolchain is involved.
# Crate dependencies are vendored from `cargoLock` by nixpkgs' importCargoLock, so the build runs offline.
#
# checkPhase validates the module with wasm-tools and fails unless every import comes from a module listed in
# `allowedImports` (default: wasi_snapshot_preview1 only), which is the ABI v1 contract (design.md W2).
{
  pkgs,
  src,
  cargoLock,
  pname,
  version ? "0.1.0",
  # Name of the binary target cargo produces, when it differs from pname.
  binName ? pname,
  # Optional manifest file installed next to the module as <pname>.wasm.yaml.
  manifest ? null,
  allowedImports ? [ "wasi_snapshot_preview1" ],
  cargoBuildFlags ? [ ],
  rustToolchain,
}:
pkgs.stdenv.mkDerivation {
  inherit pname version src;

  # Space-separated module names the imports check accepts.
  allowedImportModules = toString allowedImports;
  cargoDeps = pkgs.rustPlatform.importCargoLock { lockFile = cargoLock; };
  nativeBuildInputs = [
    rustToolchain
    pkgs.rustPlatform.cargoSetupHook
    pkgs.wasm-tools
  ];

  buildPhase = ''
    runHook preBuild
    cargo build --release --offline --locked --target wasm32-wasip1 ${pkgs.lib.escapeShellArgs cargoBuildFlags}
    runHook postBuild
  '';

  doCheck = true;
  checkPhase = ''
    runHook preCheck
    wasm="target/wasm32-wasip1/release/${binName}.wasm"
    wasm-tools validate "$wasm"
    wasm-tools print "$wasm" \
      | sed -n 's/^ *(import "\([^"]*\)" "\([^"]*\)".*/\1 \2/p' > imports.txt
    echo "imports of ${binName}.wasm:"; sed 's/^/  /' imports.txt
    allowed=" $allowedImportModules "
    bad=0
    while read -r module name; do
      case "$allowed" in *" $module "*) ;; *) echo "disallowed import: $module.$name" >&2; bad=1 ;; esac
    done < imports.txt
    [ "$bad" = 0 ] || { echo "${binName}.wasm imports outside: $allowedImportModules" >&2; exit 1; }
    runHook postCheck
  '';

  installPhase = ''
    runHook preInstall
    install -D -m 0644 "target/wasm32-wasip1/release/${binName}.wasm" "$out/${pname}.wasm"
    install -m 0644 imports.txt "$out/${pname}.imports.txt"
    ${pkgs.lib.optionalString (
      manifest != null
    ) ''install -m 0644 ${manifest} "$out/${pname}.wasm.yaml"''}
    runHook postInstall
  '';

  dontFixup = true;
}
