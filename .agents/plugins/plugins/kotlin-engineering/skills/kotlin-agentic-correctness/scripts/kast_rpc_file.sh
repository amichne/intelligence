#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: kast_rpc_file.sh --workspace-root PATH --request-file PATH --response-file PATH [--stderr-file PATH] [--kast-bin PATH]

Runs `kast rpc` with a JSON request file and writes structured stdout and stderr
to files. Stdout is reserved for callers and remains empty.
USAGE
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

workspace_root=""
request_file=""
response_file=""
stderr_file=""
kast_bin="${KAST_BIN:-kast}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workspace-root)
      [[ $# -ge 2 ]] || die "--workspace-root requires a path"
      workspace_root="$2"
      shift 2
      ;;
    --request-file)
      [[ $# -ge 2 ]] || die "--request-file requires a path"
      request_file="$2"
      shift 2
      ;;
    --response-file)
      [[ $# -ge 2 ]] || die "--response-file requires a path"
      response_file="$2"
      shift 2
      ;;
    --stderr-file)
      [[ $# -ge 2 ]] || die "--stderr-file requires a path"
      stderr_file="$2"
      shift 2
      ;;
    --kast-bin)
      [[ $# -ge 2 ]] || die "--kast-bin requires a path"
      kast_bin="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      die "unknown argument: $1"
      ;;
  esac
done

[[ -n "$workspace_root" ]] || die "--workspace-root is required"
[[ -n "$request_file" ]] || die "--request-file is required"
[[ -n "$response_file" ]] || die "--response-file is required"

if [[ -z "$stderr_file" ]]; then
  stderr_file="${response_file}.stderr.log"
fi

[[ -d "$workspace_root" ]] || die "workspace root is not a directory: $workspace_root"
[[ -f "$request_file" ]] || die "request file does not exist: $request_file"

command -v python3 >/dev/null 2>&1 || die "python3 is required"

if [[ "$kast_bin" != */* ]]; then
  resolved_kast="$(command -v "$kast_bin" || true)"
  [[ -n "$resolved_kast" ]] || die "kast binary not found on PATH: $kast_bin"
  kast_bin="$resolved_kast"
fi
[[ -x "$kast_bin" ]] || die "kast binary is not executable: $kast_bin"

mkdir -p -- "$(dirname -- "$response_file")" "$(dirname -- "$stderr_file")"

tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT

tmp_request="$tmp_dir/request.json"
tmp_response="$tmp_dir/response.json"
tmp_stderr="$tmp_dir/stderr.log"

python3 -m json.tool --compact "$request_file" "$tmp_request" \
  || die "request file is not valid JSON: $request_file"

args=(rpc --request-file "$tmp_request" --workspace-root "$workspace_root")

set +e
"$kast_bin" "${args[@]}" >"$tmp_response" 2>"$tmp_stderr"
status=$?
set -e

mv -- "$tmp_response" "$response_file"
mv -- "$tmp_stderr" "$stderr_file"

if [[ "$status" -ne 0 ]]; then
  printf 'kast_rpc_file: kast exited with status %s; response: %s; stderr: %s\n' \
    "$status" "$response_file" "$stderr_file" >&2
  exit "$status"
fi

python3 -m json.tool "$response_file" >/dev/null || die "response file is not valid JSON: $response_file"
printf 'kast_rpc_file: response: %s; stderr: %s\n' "$response_file" "$stderr_file" >&2
