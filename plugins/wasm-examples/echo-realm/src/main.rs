#![forbid(unsafe_code)]
//! `echo-realm`: an example guest for the WASM plugin runtime, ABI version 1
//! (`plugins/wasm-abi/abi-v1.schema.json`).
//!
//! # INSECURE: demonstration and test fixture only
//!
//! This module **trusts unsigned tokens**. It accepts any JWT-shaped bearer
//! token whose payload names the configured issuer and never checks the
//! signature, and it accepts the password `echo:<username>` for every user.
//! Anyone can forge credentials for it. It exists to show the ABI and to give
//! the host's tests a committed, reproducible module that exercises every
//! hook, both effects and plugin state. Never install it on a server that
//! holds anything of value.
//!
//! # Protocol
//!
//! The module is a WASI preview 1 command. For each step the host writes one
//! JSON envelope to stdin and runs `_start`; the module writes one JSON reply
//! to stdout and exits with status 0. A reply carrying `result` ends the call;
//! a reply carrying `effects` asks the host to perform them and run another
//! step whose envelope carries their results in `effects`, in order.
//!
//! # Behaviour
//!
//! Configuration comes from the envelope's `config` (the manifest's `config`):
//! `issuer`, `groups`, `fetch_url` and `mint_seconds`.
//!
//! * `realm.bearer`: the token must have three dot-separated segments whose
//!   first two are unpadded base64url JSON objects. A payload `iss` other than
//!   `config.issuer` is `not_mine`. Otherwise the payload `sub` decides:
//!   `ok:<user>` authenticates `<user>`; `fetch:<user>` first asks for an
//!   `http_get` of `config.fetch_url` and authenticates only when the response
//!   is a 200 whose trimmed body is `allow`; `reject:<x>` is rejected;
//!   `loop:<x>` spins forever (to show the host deadline); `trap:<x>` panics,
//!   which traps (to show fail-closed traps); anything else is `not_mine`.
//! * `realm.authenticate`: the password `echo:<username>` authenticates that
//!   user; anything else is `not_mine`.
//! * `execution`: `echoMint` asks for a `mint_access_token` effect, then
//!   answers 200 with the token as JSON, or 403 when minting failed. Any other
//!   execution name answers 404.
//!
//! Authenticated users get `config.groups` and `jit: true`.
//!
//! State is `{"calls": n}`: every reply carrying a `result` replaces it with
//! `n + 1`; replies asking for effects leave it unchanged.
//!
//! Malformed input never panics: a malformed realm envelope is `not_mine`,
//! and anything else malformed (including an unparseable envelope or an
//! unknown hook) is an execution result with status 400.
//!
//! Decisions live in the pure function [`decide`]; [`main`] only reads stdin
//! (at most [`MAX_STDIN_BYTES`]), writes one log line to stderr and the reply
//! to stdout. Tokens and passwords are never logged.

use std::io::{Read, Write};
use std::process::ExitCode;

use base64::engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD};
use base64::Engine;
use serde::Deserialize;
use serde_json::{json, Map, Value};

/// The ABI version this module speaks.
const ABI: u64 = 1;

/// The most stdin bytes read; a longer envelope is treated as malformed.
pub const MAX_STDIN_BYTES: u64 = 16 * 1024 * 1024;

/// Access-token lifetime requested when `config.mint_seconds` is absent.
const DEFAULT_MINT_SECONDS: u64 = 300;

/// What the imperative shell must do after a step has been decided.
#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    /// Write this JSON reply to stdout and exit with status 0.
    Reply(Value),
    /// Spin forever, so the host's step deadline has to interrupt the module.
    Hang,
    /// Panic, which traps under `panic = "abort"`.
    Trap,
}

/// The plugin configuration, read leniently from the envelope's `config`.
#[derive(Debug, Default, Clone, PartialEq, Deserialize)]
#[serde(default)]
pub struct Config {
    /// The only token issuer (`iss`) this realm claims.
    pub issuer: Option<String>,
    /// Groups given to every authenticated user.
    pub groups: Vec<String>,
    /// The URL fetched for `fetch:<user>` subjects.
    pub fetch_url: Option<String>,
    /// Lifetime of tokens minted by `echoMint`.
    pub mint_seconds: Option<u64>,
}

/// A `realm.bearer` request.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct BearerRequest {
    /// The bearer token presented by the client.
    pub token: String,
}

/// A `realm.authenticate` request.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct AuthenticateRequest {
    /// The user name presented by the client.
    pub username: String,
    /// The password presented by the client.
    pub password: String,
}

/// The plugin realm that authenticated an execution's caller.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct CallerRealm {
    /// Epoch seconds after which the caller's authentication lapses, if any.
    #[serde(default)]
    pub expires_at: Option<i64>,
}

/// The caller of an execution.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Caller {
    /// The caller's user name.
    pub username: String,
    /// The plugin realm that authenticated the caller, if any.
    #[serde(default)]
    pub realm: Option<CallerRealm>,
}

/// An `execution` request (only the fields this module uses).
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct ExecutionRequest {
    /// The execution name.
    pub name: String,
    /// Who is calling.
    pub caller: Caller,
}

/// The hook being called, with its typed request.
#[derive(Debug, Clone, PartialEq)]
pub enum Hook {
    /// `realm.bearer`.
    Bearer(BearerRequest),
    /// `realm.authenticate`.
    Authenticate(AuthenticateRequest),
    /// `execution`.
    Execution(ExecutionRequest),
}

/// A validated envelope.
#[derive(Debug, Clone, PartialEq)]
pub struct Envelope {
    /// The hook and its request.
    pub hook: Hook,
    /// The plugin configuration.
    pub config: Config,
    /// `calls` from the current plugin state; 0 when absent.
    pub calls: u64,
    /// The step number, starting at 1.
    pub step: u64,
    /// Results of the effects the previous step asked for.
    pub effects: Vec<Value>,
}

/// Why an envelope could not be used.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Malformed {
    /// A realm hook whose envelope is malformed: answer `not_mine`.
    Realm,
    /// Anything else: answer status 400.
    Other,
}

/// Decides one step: the whole behaviour of the module, free of I/O.
///
/// `input` is the raw stdin; `None` means it was unreadable or too long.
pub fn decide(input: Option<&[u8]>) -> Action {
    let parsed = input.and_then(|bytes| serde_json::from_slice::<Value>(bytes).ok());
    match parsed.as_ref().map(parse_envelope) {
        Some(Ok(envelope)) => decide_envelope(&envelope),
        Some(Err(Malformed::Realm)) => {
            let calls = parsed.as_ref().map_or(0, state_calls);
            Action::Reply(result_reply(not_mine(), calls))
        }
        Some(Err(Malformed::Other)) => {
            let calls = parsed.as_ref().map_or(0, state_calls);
            Action::Reply(result_reply(status_only(400), calls))
        }
        None => Action::Reply(result_reply(status_only(400), 0)),
    }
}

/// The stderr line for a step: the hook (only if it is a known one) and step.
pub fn log_line(input: Option<&[u8]>) -> String {
    let parsed = input.and_then(|bytes| serde_json::from_slice::<Value>(bytes).ok());
    let hook = parsed
        .as_ref()
        .and_then(|v| v.get("hook"))
        .and_then(Value::as_str)
        .filter(|h| matches!(*h, "realm.bearer" | "realm.authenticate" | "execution"))
        .unwrap_or("unknown");
    let step = parsed
        .as_ref()
        .and_then(|v| v.get("step"))
        .and_then(Value::as_u64)
        .unwrap_or(0);
    format!("echo-realm: hook={hook} step={step}")
}

/// Reads `calls` from an envelope's `state`, defaulting to 0.
fn state_calls(envelope: &Value) -> u64 {
    envelope
        .get("state")
        .and_then(|s| s.get("calls"))
        .and_then(Value::as_u64)
        .unwrap_or(0)
}

/// Validates a parsed envelope and types its request.
pub fn parse_envelope(value: &Value) -> Result<Envelope, Malformed> {
    let hook_name = value.get("hook").and_then(Value::as_str);
    let malformed = match hook_name {
        Some("realm.bearer" | "realm.authenticate") => Malformed::Realm,
        _ => Malformed::Other,
    };
    if value.get("abi").and_then(Value::as_u64) != Some(ABI) {
        return Err(malformed);
    }
    let request = value.get("request").cloned().ok_or(malformed)?;
    let hook = match hook_name {
        Some("realm.bearer") => serde_json::from_value(request).map(Hook::Bearer),
        Some("realm.authenticate") => serde_json::from_value(request).map(Hook::Authenticate),
        Some("execution") => serde_json::from_value(request).map(Hook::Execution),
        _ => return Err(malformed),
    }
    .map_err(|_| malformed)?;
    let config = match value.get("config") {
        None | Some(Value::Null) => Config::default(),
        Some(c) => serde_json::from_value(c.clone()).map_err(|_| malformed)?,
    };
    let step = value
        .get("step")
        .and_then(Value::as_u64)
        .filter(|s| *s >= 1)
        .ok_or(malformed)?;
    let effects = match value.get("effects") {
        Some(Value::Array(items)) => items.clone(),
        _ => return Err(malformed),
    };
    Ok(Envelope {
        hook,
        config,
        calls: state_calls(value),
        step,
        effects,
    })
}

/// Decides a step for a validated envelope.
pub fn decide_envelope(envelope: &Envelope) -> Action {
    match &envelope.hook {
        Hook::Bearer(request) => decide_bearer(envelope, request),
        Hook::Authenticate(request) => {
            let result = if request.password == format!("echo:{}", request.username) {
                authenticated(&request.username, &envelope.config, None)
            } else {
                not_mine()
            };
            Action::Reply(result_reply(result, envelope.calls))
        }
        Hook::Execution(request) => decide_execution(envelope, request),
    }
}

/// Decides a `realm.bearer` step.
fn decide_bearer(envelope: &Envelope, request: &BearerRequest) -> Action {
    let finish = |result: Value| Action::Reply(result_reply(result, envelope.calls));
    let Some(payload) = jwt_payload(&request.token) else {
        return finish(not_mine());
    };
    let issuer = payload.get("iss").and_then(Value::as_str);
    if issuer.is_none() || issuer != envelope.config.issuer.as_deref() {
        return finish(not_mine());
    }
    let expires_at = payload.get("exp").and_then(Value::as_i64);
    let subject = payload.get("sub").and_then(Value::as_str).unwrap_or("");
    let Some((kind, user)) = subject.split_once(':') else {
        return finish(not_mine());
    };
    match kind {
        "ok" => finish(authenticated(user, &envelope.config, expires_at)),
        "fetch" if envelope.step == 1 => match &envelope.config.fetch_url {
            Some(url) => Action::Reply(effects_reply(json!({"type": "http_get", "url": url}))),
            None => finish(rejected("fetch denied")),
        },
        "fetch" => {
            if fetch_allowed(envelope.effects.first()) {
                finish(authenticated(user, &envelope.config, expires_at))
            } else {
                finish(rejected("fetch denied"))
            }
        }
        "reject" => finish(rejected("rejected by echo-realm")),
        "loop" => Action::Hang,
        "trap" => Action::Trap,
        _ => finish(not_mine()),
    }
}

/// Whether an `http_get` effect result is an `ok` 200 whose trimmed body is `allow`.
fn fetch_allowed(result: Option<&Value>) -> bool {
    let Some(result) = result else {
        return false;
    };
    let is_get = result.get("type").and_then(Value::as_str) == Some("http_get");
    let ok = result.get("ok").and_then(Value::as_bool) == Some(true);
    let status = result.get("status").and_then(Value::as_u64) == Some(200);
    let body = result
        .get("body")
        .and_then(Value::as_str)
        .and_then(|b| STANDARD.decode(b).ok())
        .and_then(|bytes| String::from_utf8(bytes).ok());
    is_get && ok && status && body.as_deref().map(str::trim) == Some("allow")
}

/// Decides an `execution` step.
fn decide_execution(envelope: &Envelope, request: &ExecutionRequest) -> Action {
    let calls = envelope.calls;
    if request.name != "echoMint" {
        return Action::Reply(result_reply(status_only(404), calls));
    }
    if envelope.step == 1 {
        let not_after = request.caller.realm.as_ref().and_then(|r| r.expires_at);
        let expires_in = envelope.config.mint_seconds.unwrap_or(DEFAULT_MINT_SECONDS);
        return Action::Reply(effects_reply(json!({
            "type": "mint_access_token",
            "expires_in": expires_in,
            "not_after": not_after,
        })));
    }
    let minted = envelope
        .effects
        .first()
        .filter(|e| e.get("type").and_then(Value::as_str) == Some("mint_access_token"));
    let ok = minted.and_then(|e| e.get("ok")).and_then(Value::as_bool) == Some(true);
    let token = minted
        .and_then(|e| e.get("access_token"))
        .and_then(Value::as_str);
    let result = match (ok, token, minted) {
        (true, Some(token), Some(effect)) => json_response(
            200,
            &json!({
                "access_token": token,
                "expires_in": effect.get("expires_in").cloned().unwrap_or(Value::Null),
                "caller": request.caller.username,
                "calls": calls.saturating_add(1),
            }),
        ),
        _ => {
            let error = minted
                .and_then(|e| e.get("error"))
                .and_then(Value::as_str)
                .unwrap_or("mint_access_token failed");
            json_response(403, &json!({ "error": error }))
        }
    };
    Action::Reply(result_reply(result, calls))
}

/// Decodes the payload of a JWT-shaped token, without checking its signature.
///
/// Returns `None` unless the token has exactly three segments and the first
/// two are unpadded base64url encodings of JSON objects.
pub fn jwt_payload(token: &str) -> Option<Map<String, Value>> {
    let mut segments = token.split('.');
    let (header, payload, _signature) = (segments.next()?, segments.next()?, segments.next()?);
    if segments.next().is_some() {
        return None;
    }
    decode_object(header)?;
    decode_object(payload)
}

/// Decodes one unpadded base64url segment holding a JSON object.
fn decode_object(segment: &str) -> Option<Map<String, Value>> {
    let bytes = URL_SAFE_NO_PAD.decode(segment).ok()?;
    match serde_json::from_slice(&bytes).ok()? {
        Value::Object(map) => Some(map),
        _ => None,
    }
}

/// An `authenticated` realm result.
fn authenticated(user: &str, config: &Config, expires_at: Option<i64>) -> Value {
    json!({
        "outcome": "authenticated",
        "username": user,
        "groups": config.groups,
        "jit": true,
        "expires_at": expires_at,
    })
}

/// A `not_mine` realm result.
fn not_mine() -> Value {
    json!({ "outcome": "not_mine" })
}

/// A `rejected` realm result.
fn rejected(reason: &str) -> Value {
    json!({ "outcome": "rejected", "reason": reason })
}

/// An execution result with no headers and no body.
fn status_only(status: u16) -> Value {
    json!({ "status": status })
}

/// An execution result with a JSON body.
fn json_response(status: u16, body: &Value) -> Value {
    json!({
        "status": status,
        "headers": { "content-type": ["application/json"] },
        "body": STANDARD.encode(body.to_string()),
    })
}

/// A reply ending the call, replacing the state with `calls + 1`.
fn result_reply(result: Value, calls: u64) -> Value {
    json!({
        "abi": ABI,
        "result": result,
        "state": { "calls": calls.saturating_add(1) },
    })
}

/// A reply asking the host for one effect, keeping the state.
fn effects_reply(effect: Value) -> Value {
    json!({ "abi": ABI, "effects": [effect] })
}

/// Reads stdin, decides the step, logs one line and writes the reply.
fn main() -> ExitCode {
    let mut input = Vec::new();
    let read = std::io::stdin()
        .lock()
        .take(MAX_STDIN_BYTES + 1)
        .read_to_end(&mut input);
    let within_limit = u64::try_from(input.len()).is_ok_and(|n| n <= MAX_STDIN_BYTES);
    let bytes = (read.is_ok() && within_limit).then_some(input.as_slice());
    // A failed log write must not change the reply.
    let _ = writeln!(std::io::stderr(), "{}", log_line(bytes));
    match decide(bytes) {
        Action::Reply(reply) => {
            let mut out = std::io::stdout().lock();
            let written = serde_json::to_writer(&mut out, &reply)
                .map_err(std::io::Error::from)
                .and_then(|()| out.flush());
            if written.is_ok() {
                ExitCode::SUCCESS
            } else {
                ExitCode::FAILURE
            }
        }
        Action::Hang => loop {
            std::hint::black_box(());
        },
        Action::Trap => panic!("echo-realm: trap requested by token subject"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config() -> Value {
        json!({
            "issuer": "echo-it",
            "groups": ["readers"],
            "fetch_url": "https://stub.wasm-it.test:8443/allow",
            "mint_seconds": 300,
        })
    }

    fn envelope(hook: &str, request: Value, state: Value, step: u64, effects: Value) -> Vec<u8> {
        json!({
            "abi": 1,
            "hook": hook,
            "request": request,
            "config": config(),
            "state": state,
            "now": 1_700_000_000,
            "step": step,
            "effects": effects,
            "unknown_field": "ignored",
        })
        .to_string()
        .into_bytes()
    }

    fn token(payload: Value) -> String {
        let header = URL_SAFE_NO_PAD.encode(r#"{"alg":"none","typ":"JWT"}"#);
        let payload = URL_SAFE_NO_PAD.encode(payload.to_string());
        format!("{header}.{payload}.c2ln")
    }

    fn bearer(payload: Value, step: u64, effects: Value) -> Action {
        let request = json!({"realm": "echo", "token": token(payload)});
        decide(Some(&envelope(
            "realm.bearer",
            request,
            Value::Null,
            step,
            effects,
        )))
    }

    fn reply(action: Action) -> Value {
        match action {
            Action::Reply(v) => v,
            other => panic!("expected a reply, got {other:?}"),
        }
    }

    fn result(action: Action) -> Value {
        reply(action)["result"].clone()
    }

    fn body(result: &Value) -> Value {
        let bytes = STANDARD.decode(result["body"].as_str().unwrap()).unwrap();
        serde_json::from_slice(&bytes).unwrap()
    }

    #[test]
    fn bearer_ok_authenticates_with_expiry() {
        let r = reply(bearer(
            json!({"iss": "echo-it", "sub": "ok:alice", "exp": 1_700_000_600}),
            1,
            json!([]),
        ));
        assert_eq!(
            r,
            json!({
                "abi": 1,
                "result": {"outcome": "authenticated", "username": "alice", "groups": ["readers"],
                           "jit": true, "expires_at": 1_700_000_600},
                "state": {"calls": 1},
            })
        );
    }

    #[test]
    fn bearer_ok_without_exp_has_null_expiry() {
        let r = result(bearer(
            json!({"iss": "echo-it", "sub": "ok:bob"}),
            1,
            json!([]),
        ));
        assert_eq!(r["expires_at"], Value::Null);
        assert_eq!(r["username"], "bob");
    }

    #[test]
    fn bearer_other_issuer_is_not_mine() {
        let r = result(bearer(
            json!({"iss": "someone-else", "sub": "ok:alice"}),
            1,
            json!([]),
        ));
        assert_eq!(r, json!({"outcome": "not_mine"}));
        let r = result(bearer(json!({"sub": "ok:alice"}), 1, json!([])));
        assert_eq!(r, json!({"outcome": "not_mine"}));
    }

    #[test]
    fn bearer_unknown_subject_is_not_mine() {
        for sub in ["alice", "admin:alice", ""] {
            let r = result(bearer(json!({"iss": "echo-it", "sub": sub}), 1, json!([])));
            assert_eq!(r, json!({"outcome": "not_mine"}), "sub {sub}");
        }
    }

    #[test]
    fn bearer_reject_is_rejected() {
        let r = result(bearer(
            json!({"iss": "echo-it", "sub": "reject:x"}),
            1,
            json!([]),
        ));
        assert_eq!(
            r,
            json!({"outcome": "rejected", "reason": "rejected by echo-realm"})
        );
    }

    #[test]
    fn bearer_loop_and_trap() {
        assert_eq!(
            bearer(json!({"iss": "echo-it", "sub": "loop:x"}), 1, json!([])),
            Action::Hang
        );
        assert_eq!(
            bearer(json!({"iss": "echo-it", "sub": "trap:x"}), 1, json!([])),
            Action::Trap
        );
    }

    #[test]
    fn bearer_fetch_asks_for_http_get_without_state() {
        let r = reply(bearer(
            json!({"iss": "echo-it", "sub": "fetch:carol"}),
            1,
            json!([]),
        ));
        assert_eq!(
            r,
            json!({"abi": 1, "effects": [{"type": "http_get", "url": "https://stub.wasm-it.test:8443/allow"}]})
        );
    }

    #[test]
    fn bearer_fetch_allow_authenticates() {
        let effect = json!([{"type": "http_get", "ok": true, "status": 200,
                             "body": STANDARD.encode("allow\n")}]);
        let r = result(bearer(
            json!({"iss": "echo-it", "sub": "fetch:carol", "exp": 5}),
            2,
            effect,
        ));
        assert_eq!(r["outcome"], "authenticated");
        assert_eq!(r["username"], "carol");
        assert_eq!(r["expires_at"], 5);
    }

    #[test]
    fn bearer_fetch_denied_cases() {
        let cases = [
            json!([{"type": "http_get", "ok": true, "status": 200, "body": STANDARD.encode("deny")}]),
            json!([{"type": "http_get", "ok": true, "status": 403, "body": STANDARD.encode("allow")}]),
            json!([{"type": "http_get", "ok": false, "error": "host not allowed"}]),
            json!([{"type": "http_get", "ok": true, "status": 200, "body": "not base64!"}]),
            json!([]),
        ];
        for effects in cases {
            let r = result(bearer(
                json!({"iss": "echo-it", "sub": "fetch:carol"}),
                2,
                effects.clone(),
            ));
            assert_eq!(
                r,
                json!({"outcome": "rejected", "reason": "fetch denied"}),
                "{effects}"
            );
        }
    }

    #[test]
    fn bearer_malformed_tokens_are_not_mine() {
        let good = URL_SAFE_NO_PAD.encode(r#"{"iss":"echo-it","sub":"ok:a"}"#);
        let padded = STANDARD.encode(r#"{"iss":"echo-it","sub":"ok:a"}"#);
        let header = URL_SAFE_NO_PAD.encode("{}");
        let array = URL_SAFE_NO_PAD.encode("[1]");
        let tokens = [
            String::new(),
            "abc".to_string(),
            format!("{header}.{good}"),
            format!("{header}.{good}.s.extra"),
            format!("{array}.{good}.s"),
            format!("{header}.{padded}=.s"),
            format!("{header}.{array}.s"),
            format!("!!.{good}.s"),
        ];
        for t in tokens {
            let request = json!({"realm": "echo", "token": t});
            let r = result(decide(Some(&envelope(
                "realm.bearer",
                request,
                Value::Null,
                1,
                json!([]),
            ))));
            assert_eq!(r, json!({"outcome": "not_mine"}), "token {t}");
        }
    }

    #[test]
    fn authenticate_password_rule() {
        let request = json!({"realm": "echo", "username": "dave", "password": "echo:dave"});
        let r = result(decide(Some(&envelope(
            "realm.authenticate",
            request,
            Value::Null,
            1,
            json!([]),
        ))));
        assert_eq!(
            r,
            json!({"outcome": "authenticated", "username": "dave", "groups": ["readers"],
                   "jit": true, "expires_at": null})
        );
        let request = json!({"realm": "echo", "username": "dave", "password": "echo:eve"});
        let r = result(decide(Some(&envelope(
            "realm.authenticate",
            request,
            Value::Null,
            1,
            json!([]),
        ))));
        assert_eq!(r, json!({"outcome": "not_mine"}));
    }

    fn execution(name: &str, realm: Value) -> Value {
        json!({
            "name": name, "method": "POST", "params": {}, "headers": {}, "body": "",
            "caller": {"username": "alice", "admin": false, "anonymous": false, "realm": realm},
        })
    }

    #[test]
    fn mint_step_one_requests_effect() {
        let realm = json!({"plugin": "echo-realm", "realm": "echo", "expires_at": 1_700_000_600});
        let input = envelope(
            "execution",
            execution("echoMint", realm),
            json!({"calls": 3}),
            1,
            json!([]),
        );
        assert_eq!(
            reply(decide(Some(&input))),
            json!({"abi": 1, "effects": [{"type": "mint_access_token", "expires_in": 300,
                                          "not_after": 1_700_000_600}]})
        );
        let input = envelope(
            "execution",
            execution("echoMint", Value::Null),
            Value::Null,
            1,
            json!([]),
        );
        assert_eq!(
            reply(decide(Some(&input)))["effects"][0]["not_after"],
            Value::Null
        );
    }

    #[test]
    fn mint_step_two_success() {
        let effects = json!([{"type": "mint_access_token", "ok": true, "access_token": "tok", "expires_in": 250}]);
        let input = envelope(
            "execution",
            execution("echoMint", Value::Null),
            json!({"calls": 3}),
            2,
            effects,
        );
        let r = reply(decide(Some(&input)));
        assert_eq!(r["state"], json!({"calls": 4}));
        assert_eq!(r["result"]["status"], 200);
        assert_eq!(
            r["result"]["headers"],
            json!({"content-type": ["application/json"]})
        );
        assert_eq!(
            body(&r["result"]),
            json!({"access_token": "tok", "expires_in": 250, "caller": "alice", "calls": 4})
        );
    }

    #[test]
    fn mint_step_two_failure_is_403() {
        let effects = json!([{"type": "mint_access_token", "ok": false, "error": "not allowed"}]);
        let input = envelope(
            "execution",
            execution("echoMint", Value::Null),
            Value::Null,
            2,
            effects,
        );
        let r = result(decide(Some(&input)));
        assert_eq!(r["status"], 403);
        assert_eq!(body(&r), json!({"error": "not allowed"}));
    }

    #[test]
    fn unknown_execution_is_404() {
        let input = envelope(
            "execution",
            execution("other", Value::Null),
            json!({"calls": 9}),
            1,
            json!([]),
        );
        assert_eq!(
            reply(decide(Some(&input))),
            json!({"abi": 1, "result": {"status": 404}, "state": {"calls": 10}})
        );
    }

    #[test]
    fn state_counter_defaults_to_zero() {
        for state in [Value::Null, json!({}), json!({"calls": "x"}), json!([1])] {
            let input = envelope(
                "execution",
                execution("other", Value::Null),
                state,
                1,
                json!([]),
            );
            assert_eq!(reply(decide(Some(&input)))["state"], json!({"calls": 1}));
        }
    }

    #[test]
    fn malformed_envelopes() {
        let bad_realm = json!({"abi": 1, "hook": "realm.bearer", "request": {"realm": "echo"},
                               "config": config(), "state": null, "now": 0, "step": 1, "effects": []});
        assert_eq!(
            result(decide(Some(bad_realm.to_string().as_bytes()))),
            json!({"outcome": "not_mine"})
        );
        let wrong_abi = json!({"abi": 2, "hook": "realm.authenticate", "request": {}, "step": 1, "effects": []});
        assert_eq!(
            result(decide(Some(wrong_abi.to_string().as_bytes()))),
            json!({"outcome": "not_mine"})
        );
        let bad_exec = json!({"abi": 1, "hook": "execution", "request": {"name": "echoMint"},
                              "config": null, "state": null, "now": 0, "step": 1, "effects": []});
        assert_eq!(
            result(decide(Some(bad_exec.to_string().as_bytes()))),
            json!({"status": 400})
        );
        let unknown_hook =
            json!({"abi": 1, "hook": "realm.other", "request": {}, "step": 1, "effects": []});
        assert_eq!(
            result(decide(Some(unknown_hook.to_string().as_bytes()))),
            json!({"status": 400})
        );
        assert_eq!(result(decide(Some(b"not json"))), json!({"status": 400}));
        assert_eq!(result(decide(Some(b""))), json!({"status": 400}));
        assert_eq!(result(decide(None)), json!({"status": 400}));
    }

    #[test]
    fn missing_config_means_nothing_is_mine() {
        let input = json!({"abi": 1, "hook": "realm.bearer",
                           "request": {"realm": "echo", "token": token(json!({"iss": "echo-it", "sub": "ok:a"}))},
                           "config": null, "state": null, "now": 0, "step": 1, "effects": []});
        assert_eq!(
            result(decide(Some(input.to_string().as_bytes()))),
            json!({"outcome": "not_mine"})
        );
    }

    #[test]
    fn log_line_never_contains_secrets() {
        let request = json!({"realm": "echo", "username": "dave", "password": "echo:dave"});
        let line = log_line(Some(&envelope(
            "realm.authenticate",
            request,
            Value::Null,
            1,
            json!([]),
        )));
        assert_eq!(line, "echo-realm: hook=realm.authenticate step=1");
        let t = token(json!({"iss": "echo-it", "sub": "ok:a"}));
        let request = json!({"realm": "echo", "token": t});
        let line = log_line(Some(&envelope(
            "realm.bearer",
            request,
            Value::Null,
            2,
            json!([]),
        )));
        assert_eq!(line, "echo-realm: hook=realm.bearer step=2");
        assert_eq!(
            log_line(Some(br#"{"hook":"evil\ninjected"}"#)),
            "echo-realm: hook=unknown step=0"
        );
        assert_eq!(log_line(None), "echo-realm: hook=unknown step=0");
    }
}
