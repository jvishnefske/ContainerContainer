# to-oci.py: convert the docker-archive stream of dockerTools.streamLayeredImage into an OCI image archive whose
# layers are zstd-compressed.
#
# usage: python3 -I to-oci.py <docker-archive.tar> <out.tar> <sizes.tsv> <zstd> <level>
#
# The output is an OCI image layout in one tar (oci-layout, index.json, blobs/sha256/...): the image config is the
# stream's config unchanged (its rootfs.diff_ids are the sha256 of the uncompressed layer tars, which the OCI spec
# requires), the manifest lists each layer as application/vnd.oci.image.layer.v1.tar+zstd with the digest and size
# of its compressed blob, and index.json names the image with org.opencontainers.image.ref.name and
# io.containerd.image.name. A manifest.json in docker-save format points at the same blobs, so `docker load` works
# with or without the containerd image store; both `podman load` and `skopeo copy oci-archive:...` read the layout.
#
# Everything is deterministic: JSON with sorted keys and no whitespace, tar entries in a fixed order with mtime 0,
# owner 0:0 and mode 0644, and zstd at a fixed level in multi-threaded mode (for a given level zstd writes the same
# bytes whatever the number of worker threads, so -T0 is safe; checked with 1, 4 and 16 workers on a 385 MB layer).
#
# sizes.tsv gets one line per layer: index, uncompressed bytes, compressed bytes, compressed digest, and the
# largest store path of the layer (or "customisation" for the last one).
import hashlib
import io
import json
import shutil
import subprocess
import sys
import tarfile
import tempfile
import os

ZSTD_LAYER = "application/vnd.oci.image.layer.v1.tar+zstd"
MANIFEST = "application/vnd.oci.image.manifest.v1+json"
CONFIG = "application/vnd.oci.image.config.v1+json"


def dumps(obj):
    return json.dumps(obj, sort_keys=True, separators=(",", ":")).encode()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main_path(layer_tar):
    """The largest /nix/store/<name> of a layer by file bytes, for the size report."""
    sizes = {}
    with tarfile.open(layer_tar) as t:
        for m in t:
            parts = m.name.lstrip("./").split("/")
            if len(parts) >= 3 and parts[0] == "nix" and parts[1] == "store":
                sizes[parts[2]] = sizes.get(parts[2], 0) + m.size
    return "/nix/store/" + max(sorted(sizes), key=sizes.get) if sizes else "customisation"


def main(archive, out, sizes, zstd, level):
    work = tempfile.mkdtemp(dir=os.environ.get("TMPDIR"))
    with tarfile.open(archive) as t:
        t.extractall(work, filter="data")
    with open(os.path.join(work, "manifest.json")) as f:
        [docker_manifest] = json.load(f)
    with open(os.path.join(work, docker_manifest["Config"]), "rb") as f:
        config_bytes = f.read()
    config = json.loads(config_bytes)
    diff_ids = config["rootfs"]["diff_ids"]
    if len(diff_ids) != len(docker_manifest["Layers"]):
        sys.exit("config diff_ids and manifest layers disagree")

    blobs = {}  # digest -> path of the blob file
    layers = []
    report = []
    for i, (layer, diff_id) in enumerate(zip(docker_manifest["Layers"], diff_ids)):
        src = os.path.join(work, layer)
        if "sha256:" + sha256_file(src) != diff_id:
            sys.exit(f"layer {layer} does not match its diff_id {diff_id}")
        dst = src + ".zst"
        subprocess.run([zstd, f"-{level}", "-T0", "-q", "--no-progress", "-c", src],
                       stdout=open(dst, "wb"), check=True)
        digest = "sha256:" + sha256_file(dst)
        size = os.path.getsize(dst)
        blobs[digest] = dst
        layers.append({"mediaType": ZSTD_LAYER, "digest": digest, "size": size})
        report.append(f"{i}\t{os.path.getsize(src)}\t{size}\t{digest}\t{main_path(src)}")

    config_digest = "sha256:" + hashlib.sha256(config_bytes).hexdigest()
    manifest = {
        "schemaVersion": 2,
        "mediaType": MANIFEST,
        "config": {"mediaType": CONFIG, "digest": config_digest, "size": len(config_bytes)},
        "layers": layers,
    }
    manifest_bytes = dumps(manifest)
    manifest_digest = "sha256:" + hashlib.sha256(manifest_bytes).hexdigest()
    [repo_tag] = docker_manifest["RepoTags"]
    tag = repo_tag.rsplit(":", 1)[1]
    index = {
        "schemaVersion": 2,
        "mediaType": "application/vnd.oci.image.index.v1+json",
        "manifests": [{
            "mediaType": MANIFEST,
            "digest": manifest_digest,
            "size": len(manifest_bytes),
            "annotations": {
                "io.containerd.image.name": repo_tag,
                "org.opencontainers.image.ref.name": tag,
            },
        }],
    }
    docker_save = [{
        "Config": "blobs/sha256/" + config_digest.split(":")[1],
        "RepoTags": [repo_tag],
        "Layers": ["blobs/sha256/" + l["digest"].split(":")[1] for l in layers],
    }]

    def add_bytes(t, name, data):
        info = tarfile.TarInfo(name)
        info.size = len(data)
        info.mode = 0o644
        t.addfile(info, io.BytesIO(data))

    def add_dir(t, name):
        info = tarfile.TarInfo(name)
        info.type = tarfile.DIRTYPE
        info.mode = 0o755
        t.addfile(info)

    with tarfile.open(out, "w", format=tarfile.USTAR_FORMAT) as t:
        add_bytes(t, "oci-layout", dumps({"imageLayoutVersion": "1.0.0"}))
        add_bytes(t, "index.json", dumps(index))
        add_bytes(t, "manifest.json", dumps(docker_save))
        add_dir(t, "blobs")
        add_dir(t, "blobs/sha256")
        add_bytes(t, "blobs/sha256/" + config_digest.split(":")[1], config_bytes)
        add_bytes(t, "blobs/sha256/" + manifest_digest.split(":")[1], manifest_bytes)
        for digest in sorted(blobs):
            info = tarfile.TarInfo("blobs/sha256/" + digest.split(":")[1])
            info.size = os.path.getsize(blobs[digest])
            info.mode = 0o644
            with open(blobs[digest], "rb") as f:
                t.addfile(info, f)
    with open(sizes, "w") as f:
        f.write("layer\tuncompressed\tzstd\tdigest\tcontent\n")
        f.write("\n".join(report) + "\n")
    shutil.rmtree(work)
    print(f"image {repo_tag}: manifest {manifest_digest}, config {config_digest}")


if __name__ == "__main__":
    if len(sys.argv) != 6:
        sys.exit("usage: to-oci.py <docker-archive.tar> <out.tar> <sizes.tsv> <zstd> <level>")
    main(*sys.argv[1:])
