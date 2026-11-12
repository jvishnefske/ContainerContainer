# checks.wasm-hello: the module lib.buildWasmPlugin built (its imports check already ran in checkPhase) runs under
# wasmtime with only stdin/stdout, and its replies validate against plugins/wasm-abi/abi-v1.schema.json.
{
  runCommand,
  wasmtime,
  jq,
  python3,
  plugin,
  schema,
}:
runCommand "check-wasm-hello"
  {
    nativeBuildInputs = [
      wasmtime
      jq
      (python3.withPackages (ps: [ ps.jsonschema ]))
    ];
  }
  ''
    export HOME="$TMPDIR"
    exec_env='{"abi":1,"hook":"execution","request":{"name":"hello","method":"GET","params":{},"headers":{},"body":"","caller":{"username":"alice","admin":false,"anonymous":false,"realm":null}},"config":null,"state":null,"now":1,"step":1,"effects":[]}'
    realm_env='{"abi":1,"hook":"realm.authenticate","request":{"realm":"r","username":"u","password":"p"},"config":{},"state":null,"now":1,"step":1,"effects":[]}'

    printf '%s' "$exec_env" | wasmtime run ${plugin}/wasm-hello.wasm > exec.json
    printf '%s' "$realm_env" | wasmtime run ${plugin}/wasm-hello.wasm > realm.json
    printf 'garbage' | wasmtime run ${plugin}/wasm-hello.wasm > bad.json

    jq -e '.abi == 1 and .result.status == 200' exec.json
    [ "$(jq -r .result.body exec.json | base64 -d | jq -c .)" = '{"hello":"alice","step":1}' ]
    jq -e '.result.outcome == "not_mine"' realm.json
    jq -e '.result.status == 400' bad.json

    for f in exec.json realm.json bad.json; do
      python3 -I -c 'import json, sys, jsonschema
    schema = json.load(open(sys.argv[1]))
    jsonschema.validate(json.load(open(sys.argv[2])), {**schema, "oneOf": [{"$ref": "#/$defs/Reply"}]})
    print(sys.argv[2], "is a valid ABI v1 Reply")' ${schema} "$f"
    done
    mkdir -p "$out"; cp exec.json realm.json bad.json "$out/"
  ''
