# checks.jre-modules: jdeps over every jar the minimal image runs (the webapp's and Tomcat's) must not need a JDK
# module that the image's runtime lacks, except those excluded with `-<module>` in jre-modules.txt.
#
# module-info.class is removed from copies of the jars first: jdeps would otherwise treat each modular jar as a
# named module and stop at its first requirement that is not on a module path, and the image runs every jar on
# Tomcat's class path anyway. One jdeps run over all jars at once (--multi-release 25, missing classes ignored).
{
  lib,
  runCommand,
  jdk25_headless,
  zip,
  image,
}:
let
  inherit (image.passthru) parts;
  excluded = map (l: lib.removePrefix "-" (lib.trim (lib.head (lib.splitString "#" l)))) (
    lib.filter (lib.hasPrefix "-") (lib.splitString "\n" (lib.readFile ./jre-modules.txt))
  );
in
runCommand "check-jre-modules"
  {
    nativeBuildInputs = [
      jdk25_headless
      zip
    ];
  }
  ''
    mkdir jars
    cp ${parts.webapp.fork}/WEB-INF/lib/*.jar ${parts.webapp.thirdparty}/*.jar ${parts.tomcat}/lib/*.jar \
      ${parts.tomcat}/bin/*.jar jars/
    chmod u+w jars/*
    for j in jars/*.jar; do zip -q -d "$j" 'module-info.class' 'META-INF/versions/*/module-info.class' >/dev/null 2>&1 || true; done
    jdeps --list-deps --ignore-missing-deps --multi-release 25 -q jars/*.jar > deps.txt
    # Module names only (lines like "java.base/sun.nio.ch" name internal packages of a module).
    sed -n 's|^ *\(j[a-z]*\.[a-z.]*\).*$|\1|p' deps.txt | sort -u > needed
    ${parts.jre}/bin/java --list-modules | sed 's|@.*||' | sort -u > present
    printf '%s\n' ${lib.escapeShellArgs excluded} | sort -u > excluded
    comm -23 needed present | comm -23 - excluded > missing
    echo "jdeps needs: $(tr '\n' ' ' < needed)"
    echo "excluded:    $(tr '\n' ' ' < excluded)"
    [ ! -s missing ] || { echo "modules jdeps needs that the image's runtime lacks: $(tr '\n' ' ' < missing)"; exit 1; } >&2
    # an exclusion must still be needed by something, so stale lines are noticed
    comm -13 needed excluded > stale
    [ ! -s stale ] || { echo "excluded modules nothing needs any more: $(tr '\n' ' ' < stale)"; exit 1; } >&2
    { cat needed; echo; cat present; } > $out
  ''
