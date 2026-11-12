//! Test module for `lib.buildWasmPlugin` (see `nix/wasm-plugin.nix`).
//!
//! One ABI v1 step (`plugins/wasm-abi/abi-v1.schema.json`): reads an Envelope from stdin and writes one Reply to
//! stdout. An `execution` step answers 200 with the JSON body `{"hello":"<caller username>","step":<step>}`
//! (base64, as the ABI requires); realm hooks answer `not_mine`. An envelope that does not parse, or whose `abi` is
//! not 1, gets an execution result with status 400 and a base64 error text, so the reply is always ABI-shaped.
#![forbid(unsafe_code)]

use std::collections::BTreeMap;
use std::io::{self, Read, Write};
use std::process::ExitCode;

use serde::Deserialize;
use serde_json::{json, Value};

#[derive(Deserialize)]
struct Envelope {
    abi: u32,
    hook: String,
    #[serde(default)]
    request: Value,
    step: u64,
}

const B64: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/// Standard-alphabet, padded base64.
fn base64(input: &[u8]) -> String {
    let mut out = String::with_capacity(input.len().div_ceil(3) * 4);
    for chunk in input.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = (u32::from(b[0]) << 16) | (u32::from(b[1]) << 8) | u32::from(b[2]);
        for (i, shift) in [18u32, 12, 6, 0].into_iter().enumerate() {
            if i <= chunk.len() {
                out.push(char::from(B64[((n >> shift) & 63) as usize]));
            } else {
                out.push('=');
            }
        }
    }
    out
}

fn execution_reply(status: u16, body: &Value) -> Value {
    let mut headers = BTreeMap::new();
    headers.insert("content-type", vec!["application/json"]);
    json!({
        "abi": 1,
        "result": { "status": status, "headers": headers, "body": base64(body.to_string().as_bytes()) }
    })
}

/// The pure step function: envelope bytes in, reply out.
fn reply_for(input: &[u8]) -> Value {
    let envelope: Envelope = match serde_json::from_slice(input) {
        Ok(e) => e,
        Err(e) => return execution_reply(400, &json!({ "error": format!("bad envelope: {e}") })),
    };
    if envelope.abi != 1 {
        return execution_reply(400, &json!({ "error": format!("unsupported abi {}", envelope.abi) }));
    }
    match envelope.hook.as_str() {
        "execution" => {
            let user = envelope.request.pointer("/caller/username").and_then(Value::as_str).unwrap_or("");
            execution_reply(200, &json!({ "hello": user, "step": envelope.step }))
        }
        _ => json!({ "abi": 1, "result": { "outcome": "not_mine" } }),
    }
}

fn main() -> ExitCode {
    let mut input = Vec::new();
    if io::stdin().read_to_end(&mut input).is_err() {
        return ExitCode::FAILURE;
    }
    let reply = reply_for(&input);
    let mut stdout = io::stdout().lock();
    match writeln!(stdout, "{reply}").and_then(|()| stdout.flush()) {
        Ok(()) => ExitCode::SUCCESS,
        Err(_) => ExitCode::FAILURE,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64_matches_rfc4648_vectors() {
        assert_eq!(base64(b""), "");
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
        assert_eq!(base64(b"foobar"), "Zm9vYmFy");
    }

    #[test]
    fn realm_hooks_are_not_mine() {
        let r = reply_for(br#"{"abi":1,"hook":"realm.bearer","request":{},"config":null,"state":null,"now":0,"step":1,"effects":[]}"#);
        assert_eq!(r["result"]["outcome"], "not_mine");
    }

    #[test]
    fn garbage_gets_400() {
        assert_eq!(reply_for(b"not json")["result"]["status"], 400);
    }
}
