#!/usr/bin/env bash
# Packages each Lambda consumer (lambda/email-consumer, lambda/sms-consumer)
# with its dependencies into build/<name>, which Terraform zips and deploys.
# Wheels are pinned to Linux x86_64 so the package runs on Lambda even when
# it is built on macOS or Windows.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

for NAME in email-consumer sms-consumer; do
  SRC="$ROOT/lambda/$NAME"
  OUT="$ROOT/build/$NAME"

  rm -rf "$OUT"
  mkdir -p "$OUT"

  python3 -m pip install \
    --quiet \
    --requirement "$SRC/requirements.txt" \
    --target "$OUT" \
    --platform manylinux2014_x86_64 \
    --implementation cp \
    --python-version 3.12 \
    --only-binary=:all:

  cp "$SRC/handler.py" "$OUT/"
  echo "Lambda package ready: $OUT"
done
