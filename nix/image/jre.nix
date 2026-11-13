# The image's Java runtime: jlink of nixpkgs' headless OpenJDK 25 with the modules in jre-modules.txt.
#
# Headless, so java.desktop carries no X11 or CUPS libraries (and libjsound, the only ALSA user, is removed). Debug symbols, headers and man pages are left
# out; the module image is stored uncompressed (zip-0), which the zstd layer compression packs better than
# jlink's own zip. Only bin/java and bin/keytool are kept. The JDK's native code carries RUNPATH entries into the
# JDK's own lib directories; they are rewritten to this runtime's, so it does not refer back to the full JDK
# (disallowedReferences enforces that).
#
# checks.jre-modules (nix/image/jre-modules-check.nix) runs jdeps over every jar of the image and fails when a jar
# needs a module that is neither in this runtime nor excluded in jre-modules.txt.
{
  lib,
  runCommand,
  jdk25_headless,
  binutils,
  patchelf,
  modules,
}:
let
  jdkHome = "${jdk25_headless}/lib/openjdk";
in
runCommand "containercontainer-jre-${jdk25_headless.version}"
  {
    nativeBuildInputs = [
      jdk25_headless
      binutils # objcopy, for --strip-debug
      patchelf
    ];
    disallowedReferences = [ jdk25_headless ];
    passthru = { inherit modules; };
    meta.license = jdk25_headless.meta.license;
  }
  ''
    jlink \
      --module-path ${jdkHome}/jmods \
      --add-modules ${lib.concatStringsSep "," modules} \
      --strip-debug --no-header-files --no-man-pages --compress=zip-0 \
      --output "$out"
    find "$out/bin" -mindepth 1 ! -name java ! -name keytool -delete
    chmod -R u+w "$out"
    # Java Sound's native part is the only user of ALSA; a server never plays sound.
    rm "$out/lib/libjsound.so"
    find "$out" -type f | while read -r f; do
      rpath="$(patchelf --print-rpath "$f" 2>/dev/null)" || continue
      case "$rpath" in
        *${jdk25_headless}*)
          patchelf --set-rpath \
            "$(printf '%s' "$rpath" | sed -e "s|${jdkHome}/lib|$out/lib|g" -e "s|${jdk25_headless}/lib|$out/lib|g")" "$f"
          ;;
      esac
    done
    "$out/bin/java" -version
  ''
