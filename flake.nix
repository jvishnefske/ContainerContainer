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

      # Image creation time: the commit time of the flake source (lastModifiedDate is YYYYMMDDHHMMSS).
      created =
        let
          d = self.lastModifiedDate or "19700101000001";
          s = start: len: builtins.substring start len d;
        in
        "${s 0 4}-${s 4 2}-${s 6 2}T${s 8 2}:${s 10 2}:${s 12 2}Z";

      # lib.mkImage { pkgs, plugins ? [ ], extraConfig ? { }, accessUrl ? ..., accessGrpc ? ..., tag ? version }:
      # the minimal from-scratch OCI image (nix/image.nix) around this flake's WAR for pkgs' system.
      mkImage =
        args:
        import ./nix/image.nix (
          {
            war = self.packages.${args.pkgs.stdenv.hostPlatform.system}.war;
            inherit
              version
              srcVersion
              sourceRev
              created
              ;
            license = ./LICENSE;
            rootWebapp = ./devenv/src/main/install/misc/tomcat/webapps/ROOT;
          }
          // args
        );

    in
    {
      lib = {
        inherit buildWasmPlugin mkImage version;
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
          # The whole webapp WAR, the input of the from-scratch OCI image (lib.mkImage).
          war = pkgs.runCommand "containercontainer-war-${version}" { } ''
            install -D -m 0644 ${jarsBuild}/war/artifactory.war "$out/artifactory.war"
          '';
          # The minimal image without plugins, and the release assets made from it.
          oci = mkImage { inherit pkgs; };
          release-oci = pkgs.callPackage ./nix/release-oci.nix { inherit oci; };
          # The public example plugins in mkImage form: the OCI registry (Groovy) and echo-realm (WASM: the
          # committed build of plugins/wasm-examples/echo-realm and its manifest). docker/it/run-min-it.sh runs it.
          echo-realm-plugin = pkgs.runCommand "echo-realm-plugin" { } ''
            install -D -m 0444 ${./backend/core/src/test/resources/wasm/echo-realm.wasm} "$out/echo-realm.wasm"
            install -D -m 0444 ${./plugins/wasm-examples/echo-realm/echo-realm.wasm.yaml} "$out/echo-realm.wasm.yaml"
          '';
          oci-example = mkImage {
            inherit pkgs;
            tag = "${version}-example";
            plugins = [
              ./plugins/oci/oci-registry.groovy
              echo-realm-plugin
            ];
          };
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
            oci
            release-oci
            echo-realm-plugin
            oci-example
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
          # The minimal image: its structure (contents allowlist, layer order, media types, non-root user), the
          # same for the plugin example, and the runtime's module set against jdeps.
          image-structure = pkgs.callPackage ./nix/image/check-image.nix {
            image = p.oci;
            inherit (p) war;
          };
          image-example-structure = pkgs.callPackage ./nix/image/check-image.nix {
            image = p.oci-example;
            inherit (p) war;
            plugins = [
              "oci-registry.groovy"
              "echo-realm.wasm"
              "echo-realm.wasm.yaml"
            ];
          };
          jre-modules = pkgs.callPackage ./nix/image/jre-modules-check.nix { image = p.oci; };
          # mkImage evaluates with every argument and rejects a .wasm without its manifest.
          image-plugin-rules =
            let
              failed = pkgs.testers.testBuildFailure (
                (mkImage {
                  inherit pkgs;
                  plugins = [ ./backend/core/src/test/resources/wasm/echo-realm.wasm ];
                }).passthru.parts.pluginsDir
              );
            in
            pkgs.runCommand "check-image-plugin-rules"
              {
                # Evaluating the stream's derivation path evaluates the whole image config, without building it.
                evaluated =
                  builtins.unsafeDiscardStringContext
                    (mkImage {
                      inherit pkgs;
                      plugins = [ p.echo-realm-plugin ];
                      extraConfig = {
                        Env = [ "JAVA_OPTIONS=-Xmx1g" ];
                        Labels."org.example.extra" = "yes";
                      };
                      accessUrl = "http://access.example:8040/access";
                      accessGrpc = "access.example:8045";
                      tag = "check";
                    }).passthru.stream.drvPath;
              }
              ''
                grep -q 'echo-realm.wasm has no echo-realm.wasm.yaml manifest' ${failed}/testBuildFailure.log
                touch $out
              '';
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
