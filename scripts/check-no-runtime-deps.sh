#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marco Vanadia
# SPDX-License-Identifier: MIT
#
# Fails if the core module has any dependency outside test scope. The enforcer's
# bannedDependencies rule already rejects compile, provided, runtime and system
# dependencies, but it does not see optional ones; this listing does.
set -euo pipefail

cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

# "compile" resolves compile, provided and system scope; "runtime" adds runtime scope.
for scope in compile runtime; do
  mvn -B -ntp -q -pl state-machines dependency:list \
    -DincludeScope="$scope" -DoutputFile="$out/$scope.txt"
done

# Fail closed: Maven can exit 0 without writing a listing (for example when the
# dependency plugin is skipped), and a missing listing must not read as "no dependencies".
for scope in compile runtime; do
  if ! grep -q '^The following files have been resolved:' "$out/$scope.txt" 2>/dev/null; then
    echo "error: no dependency listing was produced for scope '$scope'" >&2
    exit 1
  fi
done

rc=0
found="$(grep -hE '^[[:space:]]+[^:[:space:]]+:[^:[:space:]]+:' "$out/compile.txt" "$out/runtime.txt")" || rc=$?
case "$rc" in
  0)
    echo "error: state-machines has dependencies outside test scope:" >&2
    echo "$found" | sort -u >&2
    exit 1
    ;;
  1)
    echo "OK: state-machines has no dependencies outside test scope."
    ;;
  *)
    echo "error: could not read the dependency listings (grep exit $rc)" >&2
    exit 1
    ;;
esac
