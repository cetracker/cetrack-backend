#!/usr/bin/env bash
# Build the anonymized MyTourbook Derby test fixture.
# Produces tourbook.tar.bz2 in the same directory as this script.
#
# Requirements:
#   - Java 11+
#   - Apache Derby tools (ij). Default: /opt/db-derby-10.16.1.1-bin/lib/
#     Override with DERBY_LIB env var.
#
# Usage:
#   bash build-fixture.sh
#   DERBY_LIB=/path/to/derby/lib bash build-fixture.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

: "${DERBY_LIB:=/opt/db-derby-10.16.1.1-bin/lib}"
if [ ! -f "$DERBY_LIB/derbytools.jar" ]; then
    echo "ERROR: derbytools.jar not found in DERBY_LIB=$DERBY_LIB" >&2
    echo "Set DERBY_LIB to a Derby installation containing derby.jar, derbyshared.jar, derbytools.jar" >&2
    exit 1
fi
DERBY_CP="$DERBY_LIB/derby.jar:$DERBY_LIB/derbyshared.jar:$DERBY_LIB/derbytools.jar"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

DB_PATH="$TMPDIR/tourbook"
CONN_URL="jdbc:derby:$DB_PATH;create=true;user=user"
SHUTDOWN_URL="jdbc:derby:$DB_PATH;shutdown=true"

echo "==> Creating Derby DB at $DB_PATH"

# Build an ij command file (heredoc avoids quoting issues with embedded paths)
cat > "$TMPDIR/build.ij" <<IJEOF
CONNECT '$CONN_URL';
RUN '$SCRIPT_DIR/schema.sql';
RUN '$SCRIPT_DIR/data.sql';
DISCONNECT;
CONNECT '$SHUTDOWN_URL';
IJEOF

# Run ij; the final CONNECT with ;shutdown=true raises XJ015 (normal) → ignore exit code.
set +e
java -cp "$DERBY_CP" org.apache.derby.tools.ij "$TMPDIR/build.ij" 2>&1 \
    | grep -v "^$" \
    | grep -v "XJ015\|08006\|Shutdown: true"
IJ_EXIT=$?
set -e

if [ ! -d "$DB_PATH/seg0" ]; then
    echo "ERROR: Derby DB not created (ij exit=$IJ_EXIT). Check output above." >&2
    exit 1
fi
echo "==> DB created OK"

# Strip Derby runtime cruft to minimise archive size
rm -f "$DB_PATH"/*.lck 2>/dev/null || true
rm -rf "$DB_PATH/tmp" 2>/dev/null || true

# Archive layout: tourbook/ at the root (matching CE-0036's unpack path).
# NOTE: the real production archive from CE-0035 nests as derby-database/tourbook/
# (different root name and one extra level). CE-0036's unpack logic must locate the
# directory containing service.properties to handle both layouts.
cd "$TMPDIR"
tar cjf "$SCRIPT_DIR/tourbook.tar.bz2" tourbook/

SIZE=$(du -sh "$SCRIPT_DIR/tourbook.tar.bz2" | cut -f1)
echo "==> tourbook.tar.bz2 written ($SIZE)"
