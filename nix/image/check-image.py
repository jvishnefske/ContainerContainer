# check-image.py: structure test of a minimal image archive built by nix/image.nix (checks.image-structure).
#
# usage: python3 -I check-image.py <image.oci.tar> <expect.json>
#
# expect.json (written by nix/image/check-image.nix):
#   layers        per layer, in order, a name fragment one of its store paths must contain
#                 ("customisation" for the last layer, which must hold no store paths)
#   files         absolute paths that must exist in the image (files, directories or symlinks)
#   jars          the exact set of jar file names the image may contain (the WAR's, Tomcat's, the JDBC drivers)
#   elfPrefixes   store path prefixes ELF files may live under (the closure of the JRE and busybox, from nixpkgs)
#   forbidden     regexes (search) no path in the image may match (JFrog services the stock image has)
#   user, uid, var
#                 the image config's User, and the directory (the volume) that must be owned by uid
#   noAccess      the "no Access configuration, no default credential" rules (embedded access, design IA1):
#                   env          variable names the image config's Env must not set (ACCESS_URL, ACCESS_GRPC, ...)
#                   launcher     store path prefixes whose files (the launcher, the config layer) must not contain
#                                any of launcherPatterns (the Access URL overrides, join key handling)
#                   scan         store path prefixes whose files, and the entries of their jars, must not contain
#                                any of credentialPatterns (default administrator credentials)
#                 The image config itself (Env, Labels, Entrypoint, Cmd) must not contain credentialPatterns either.
#
# Also checks the archive itself: oci-layout and index.json, every blob's sha256 matches its name, every layer is
# application/vnd.oci.image.layer.v1.tar+zstd, the config's diff_ids are the sha256 of the uncompressed layers, and
# docker's manifest.json names the same config and layers. Prints a summary and exits non-zero on the first failure.
import hashlib
import io
import json
import re
import subprocess
import sys
import tarfile
import zipfile

ZSTD_LAYER = "application/vnd.oci.image.layer.v1.tar+zstd"


def fail(msg):
    sys.exit(f"image check failed: {msg}")


def main(archive, expect_file):
    with open(expect_file) as f:
        expect = json.load(f)
    with tarfile.open(archive) as t:
        blobs = {}
        for m in t.getmembers():
            if m.isfile():
                blobs[m.name] = t.extractfile(m).read()
    layout = json.loads(blobs["oci-layout"])
    if layout != {"imageLayoutVersion": "1.0.0"}:
        fail(f"oci-layout {layout}")
    for name, data in blobs.items():
        if name.startswith("blobs/sha256/") and hashlib.sha256(data).hexdigest() != name.rsplit("/", 1)[1]:
            fail(f"blob {name} does not match its digest")
    index = json.loads(blobs["index.json"])
    [desc] = index["manifests"]
    manifest = json.loads(blobs["blobs/sha256/" + desc["digest"].split(":")[1]])
    config = json.loads(blobs["blobs/sha256/" + manifest["config"]["digest"].split(":")[1]])
    docker = json.loads(blobs["manifest.json"])
    if docker[0]["Config"] != "blobs/sha256/" + manifest["config"]["digest"].split(":")[1]:
        fail("manifest.json names another config")
    if docker[0]["Layers"] != ["blobs/sha256/" + l["digest"].split(":")[1] for l in manifest["layers"]]:
        fail("manifest.json names other layers")

    paths = {}  # path -> TarInfo (last layer wins)
    contents = {}  # path -> bytes, for regular files under the noAccess scan and launcher prefixes
    scanned = tuple(p + "/" for p in expect["noAccess"]["scan"] + expect["noAccess"]["launcher"])
    elf = []
    if len(manifest["layers"]) != len(expect["layers"]):
        fail(f"{len(manifest['layers'])} layers, expected {len(expect['layers'])}")
    for i, (layer, diff_id, want) in enumerate(zip(manifest["layers"], config["rootfs"]["diff_ids"], expect["layers"])):
        if layer["mediaType"] != ZSTD_LAYER:
            fail(f"layer {i} has media type {layer['mediaType']}")
        raw = subprocess.run(["zstd", "-d", "-q", "-c"], input=blobs["blobs/sha256/" + layer["digest"].split(":")[1]],
                             stdout=subprocess.PIPE, check=True).stdout
        if "sha256:" + hashlib.sha256(raw).hexdigest() != diff_id:
            fail(f"layer {i} does not match diff_id {diff_id}")
        stores = set()
        with tarfile.open(fileobj=io.BytesIO(raw)) as lt:
            for m in lt.getmembers():
                p = "/" + m.name.lstrip("./")
                paths[p.rstrip("/") or "/"] = m
                parts = p.split("/")
                if len(parts) > 3 and parts[1] == "nix" and parts[2] == "store":
                    stores.add(parts[3])
                if m.isfile() and m.size >= 4 and lt.extractfile(m).read(4) == b"\x7fELF":
                    elf.append(p)
                if m.isfile() and p.startswith(scanned):
                    contents[p] = lt.extractfile(m).read()
        if want == "customisation":
            if stores:
                fail(f"layer {i} (customisation) holds store paths {sorted(stores)}")
        elif not any(want in s for s in stores):
            fail(f"layer {i} holds {sorted(stores)}, none of them a {want}")
        print(f"layer {i}: {want} ok ({layer['size']} bytes zstd, {len(raw)} bytes tar)")

    for p in expect["files"]:
        if p not in paths:
            fail(f"missing {p}")
    forbidden = [re.compile(r) for r in expect["forbidden"]]
    for p in paths:
        for r in forbidden:
            if r.search(p):
                fail(f"forbidden path {p} (matches {r.pattern})")
    jars = sorted({p.rsplit("/", 1)[1] for p, m in paths.items() if p.endswith(".jar") and m.isfile()})
    unexpected = sorted(set(jars) - set(expect["jars"]))
    missing = sorted(set(expect["jars"]) - set(jars))
    if unexpected or missing:
        fail(f"jar set differs: unexpected {unexpected}, missing {missing}")
    for p in elf:
        if not any(p.startswith(prefix + "/") for prefix in expect["elfPrefixes"]):
            fail(f"ELF file {p} outside the JRE and busybox closure")
    if config["config"].get("User") != expect["user"]:
        fail(f"image user {config['config'].get('User')}, expected {expect['user']}")
    var = paths.get(expect["var"])
    if var is None or not var.isdir() or var.uid != expect["uid"] or var.gid != expect["uid"]:
        fail(f"{expect['var']} is not a directory owned by {expect['uid']}")
    if expect["var"] not in config["config"].get("Volumes", {}):
        fail(f"{expect['var']} is not a volume")
    check_no_access(expect["noAccess"], config["config"], contents)
    print(f"{len(paths)} paths, {len(jars)} jars, {len(elf)} ELF files, user {expect['user']}: ok")


def check_no_access(rules, image_config, contents):
    """No Access endpoint, join key or default administrator credential anywhere in the image's config or files."""
    env = {e.split("=", 1)[0] for e in image_config.get("Env", [])}
    bad_env = sorted(env & set(rules["env"]))
    if bad_env:
        fail(f"image config sets {bad_env}")
    credentials = [re.compile(r.encode()) for r in rules["credentialPatterns"]]
    launcher = [re.compile(r.encode()) for r in rules["launcherPatterns"]]
    config_text = json.dumps({k: image_config.get(k) for k in ("Env", "Labels", "Entrypoint", "Cmd")}).encode()
    for r in credentials:
        if r.search(config_text):
            fail(f"image config contains {r.pattern.decode()}")
    launcher_prefixes = tuple(p + "/" for p in rules["launcher"])
    scanned = 0
    for path, data in contents.items():
        blobs = [(path, data)]
        if path.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                blobs = [(f"{path}!{n}", z.read(n)) for n in z.namelist() if not n.endswith("/")]
        for name, blob in blobs:
            scanned += 1
            for r in credentials:
                if r.search(blob):
                    fail(f"{name} contains the default credential pattern {r.pattern.decode()}")
            if path.startswith(launcher_prefixes):
                for r in launcher:
                    if r.search(blob):
                        fail(f"{name} contains Access configuration {r.pattern.decode()}")
    print(f"no Access configuration: env clean, {scanned} files and jar entries free of default credentials: ok")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: check-image.py <image.oci.tar> <expect.json>")
    main(*sys.argv[1:])
