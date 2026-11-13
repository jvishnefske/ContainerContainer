# slim-webapp.py: turn the exploded fork WAR into the two webapp trees of the minimal image.
#
# usage: python3 -I slim-webapp.py <exploded-war> <rules.json> <fork-out> <third-party-out>
#
#   <fork-out>/          the webapp docBase: WEB-INF/web.xml and WEB-INF/lib/<the fork's own jars>
#   <third-party-out>/   every other WEB-INF/lib jar (mounted at /WEB-INF/lib by the Tomcat context)
# Anything in WEB-INF/lib that is not a jar (the WAR has a bc/ directory of readme files for the BouncyCastle
# flavour switch, which the image does not use) is left out.
#
# rules.json (written by nix/image/webapp.nix):
#   forkJar      regex (full match) naming the fork's own jars; they go to <fork-out>
#   drop         jar file names left out of the image; each must exist in the WAR, so a stale rule fails
#   uiJar        the jar whose web UI static assets are stripped
#   uiPrefixes   entry name prefixes removed from uiJar (the Angular UI, images and micro-frontends)
#   nativeKeep   regexes (search); a native library entry (.so .dll .dylib .jnilib .a) is kept only if one matches
#
# Every other jar is rewritten with the same entries in the same order but STORED (uncompressed), so the zstd
# layer compression sees the raw class files and compresses them far better than deflate-inside-zstd. Entry
# names, timestamps and attributes are kept; nothing depends on the time of the build. Signed jars (META-INF/*.SF)
# are copied byte for byte, since their signatures cover the entries that are present.
#
# Prints one line per changed jar to stdout, for the build log.
import json
import os
import re
import shutil
import sys
import zipfile

NATIVE = re.compile(r"\.(so|dll|dylib|jnilib|a)$")
SIGNATURE = re.compile(r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$")


def is_signed(names):
    return any(SIGNATURE.match(n) for n in names)


def keep_entry(name, ui_prefixes, native_keep):
    if any(name.startswith(p) for p in ui_prefixes):
        return False
    if NATIVE.search(name) and not any(r.search(name) for r in native_keep):
        return False
    return True


def rewrite(src, dst, ui_prefixes, native_keep):
    """Write dst with the kept entries of src, all STORED. Returns (entries dropped, bytes dropped)."""
    dropped = dropped_bytes = 0
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_STORED) as zout:
        for info in zin.infolist():
            if not keep_entry(info.filename, ui_prefixes, native_keep):
                dropped += 1
                dropped_bytes += info.file_size
                continue
            out = zipfile.ZipInfo(info.filename, info.date_time)
            out.external_attr = info.external_attr
            out.create_system = 3
            out.compress_type = zipfile.ZIP_STORED
            zout.writestr(out, zin.read(info))
    return dropped, dropped_bytes


def main(war, rules_file, fork_out, third_out):
    with open(rules_file) as f:
        rules = json.load(f)
    fork_jar = re.compile(rules["forkJar"])
    drop = set(rules["drop"])
    native_keep = [re.compile(r) for r in rules["nativeKeep"]]
    lib = os.path.join(war, "WEB-INF", "lib")
    jars = sorted(n for n in os.listdir(lib) if n.endswith(".jar") and os.path.isfile(os.path.join(lib, n)))
    for other in sorted(set(os.listdir(lib)) - set(jars)):
        print(f"left out (not a jar): WEB-INF/lib/{other}")

    missing = sorted(drop - set(jars))
    if missing:
        sys.exit(f"drop rules name jars the WAR does not have: {missing}")
    if rules["uiJar"] not in jars:
        sys.exit(f"no UI jar {rules['uiJar']} in the WAR")

    os.makedirs(os.path.join(fork_out, "WEB-INF", "lib"))
    os.makedirs(third_out)
    shutil.copyfile(os.path.join(war, "WEB-INF", "web.xml"), os.path.join(fork_out, "WEB-INF", "web.xml"))

    forks = 0
    for jar in jars:
        if jar in drop:
            print(f"dropped {jar}")
            continue
        src = os.path.join(lib, jar)
        is_fork = bool(fork_jar.fullmatch(jar))
        forks += is_fork
        dst = os.path.join(fork_out, "WEB-INF", "lib", jar) if is_fork else os.path.join(third_out, jar)
        with zipfile.ZipFile(src) as z:
            signed = is_signed(z.namelist())
        if signed:
            shutil.copyfile(src, dst)
            print(f"signed, copied unchanged: {jar}")
            continue
        prefixes = rules["uiPrefixes"] if jar == rules["uiJar"] else []
        n, size = rewrite(src, dst, prefixes, native_keep)
        if n:
            print(f"stripped {n} entries ({size // 1024} KiB) from {jar}")
    if forks == 0:
        sys.exit("no fork jars matched forkJar")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit("usage: slim-webapp.py <exploded-war> <rules.json> <fork-out> <third-party-out>")
    main(*sys.argv[1:])
