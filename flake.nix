{
  description = "ContainerContainer: Artifactory OSS 7.161.26 fork (AGPL-3.0) - jars and binary release";

  inputs = {
    # nixos-26.05 release branch, locked in flake.lock. Provides jdk25 (OpenJDK 25), maven 3.9 and dockerTools.
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    # Prebuilt Rust toolchains with the wasm32-wasip1 standard library (nixpkgs' rustc ships host targets only).
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
    }:
    let
      inherit (nixpkgs) lib;
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: lib.genAttrs systems (system: f system (pkgsFor system));
      pkgsFor =
        system:
        import nixpkgs {
          inherit system;
          overlays = [ rust-overlay.overlays.default ];
        };

      # Release version: the single source is nix/version.txt (<upstream release>-cc.<n>); the release tag is v<version>.
      version = lib.fileContents ./nix/version.txt;
      # The upstream sources call release 7.161.26 "7.161.15" internally; the jars carry that version.
      srcVersion = "7.161.15";
      sourceRev = self.rev or self.dirtyRev or "unknown";

      # Maven inputs only: changes to docs, workflows, docker/ or nix/ must not rebuild the jars.
      mavenSrc = lib.cleanSourceWith {
        name = "containercontainer-src";
        src = ./.;
        filter =
          path: type:
          let
            rel = lib.removePrefix (toString ./. + "/") (toString path);
            top = lib.head (lib.splitString "/" rel);
            base = baseNameOf path;
          in
          !(
            type == "directory"
            && lib.elem base [
              "target"
              "node_modules"
            ]
          )
          && (
            lib.elem top [
              "pom.xml"
              "lombok.config"
              "LICENSE"
              "base"
              "bridge"
              "storage"
              "support"
              "backend"
              "web"
              "bins"
              "coverage"
              "lifecycle"
              "devenv"
              "build-handler"
            ]
            || rel == ".github"
            || rel == ".github/maven"
            || rel == ".github/maven/settings.xml"
          );
      };

      wasmToolchain =
        pkgs:
        pkgs.rust-bin.stable."1.95.0".minimal.override {
          targets = [ "wasm32-wasip1" ];
        };

      buildWasmPlugin =
        args: import ./nix/wasm-plugin.nix ({ rustToolchain = wasmToolchain args.pkgs; } // args);

    in
    {
      lib = {
        inherit buildWasmPlugin version;
        # Hook for a from-scratch image: a later lib.mkImage { pkgs, plugins ? [ ] } builds a layered OCI image
        # (dockerTools.buildLayeredImage, no fromImage) from packages.war, a jlink'd JRE 25 and Tomcat, adding
        # plugin files (Groovy, buildWasmPlugin outputs) to its plugins directory. Not implemented yet.
      };

      packages = forAllSystems (
        system: pkgs:
        let
          jarsBuild = pkgs.callPackage ./nix/jars.nix {
            src = mavenSrc;
            inherit version srcVersion;
            stockListing = ./nix/stock-lib.txt;
            mvnHash = lib.fileContents ./nix/mvn-hash.txt;
          };
          jars = pkgs.callPackage ./nix/manifest.nix {
            inherit
              jarsBuild
              version
              srcVersion
              sourceRev
              ;
          };
          release = pkgs.callPackage ./nix/release.nix {
            inherit jars version sourceRev;
            sourceDateEpoch = self.lastModified or 1;
            license = ./LICENSE;
            abiSchema = ./plugins/wasm-abi/abi-v1.schema.json;
          };
          # The whole webapp WAR, the input for a future from-scratch OCI image (see lib above).
          war = pkgs.runCommand "containercontainer-war-${version}" { } ''
            install -D -m 0644 ${jarsBuild}/war/artifactory.war "$out/artifactory.war"
          '';
          wasm-hello = buildWasmPlugin {
            inherit pkgs;
            pname = "wasm-hello";
            src = ./nix/tests/wasm-hello;
            cargoLock = ./nix/tests/wasm-hello/Cargo.lock;
          };
        in
        {
          inherit
            jarsBuild
            jars
            war
            release
            wasm-hello
            ;
          default = release;
        }
      );

      checks = forAllSystems (
        system: pkgs:
        let
          p = self.packages.${system};
        in
        {
          jars-vs-stock = pkgs.callPackage ./nix/checks/jars-vs-stock.nix { inherit (p) jars; };
          release-deterministic = p.release.passthru.deterministic;
          wasm-hello = pkgs.callPackage ./nix/checks/wasm-hello.nix {
            plugin = p.wasm-hello;
            schema = ./plugins/wasm-abi/abi-v1.schema.json;
          };
          # The imports check must fail a module whose imports are not all allowed (here: WASI not allowed).
          wasm-imports-check-rejects =
            let
              failed = pkgs.testers.testBuildFailure (
                p.wasm-hello.overrideAttrs (_: {
                  pname = "wasm-hello-no-wasi";
                  allowedImportModules = "env";
                })
              );
            in
            pkgs.runCommand "check-wasm-imports-check-rejects" { } ''
              grep -q 'disallowed import: wasi_snapshot_preview1' ${failed}/testBuildFailure.log
              touch $out
            '';
        }
      );

      devShells = forAllSystems (
        system: pkgs: {
          default = pkgs.mkShell {
            packages = [
              pkgs.jdk25
              (pkgs.maven.override { jdk_headless = pkgs.jdk25; })
              (wasmToolchain pkgs)
              pkgs.wasm-tools
              pkgs.wasmtime
              pkgs.jq
            ];
            JAVA_HOME = pkgs.jdk25.home;
          };
        }
      );

      formatter = forAllSystems (_: pkgs: pkgs.nixfmt);
    };
}
