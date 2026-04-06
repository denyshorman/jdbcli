#!/usr/bin/env bash
# jdbcli installer — Linux / macOS
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/denyshorman/jdbcli/main/install/install.sh | bash
#
# Environment overrides (optional):
#   JDBCLI_INSTALL_DIR  — directory for the jdbcli wrapper script  (default: ~/.local/bin)
#   JDBCLI_JAR_DIR      — directory where jdbcli.jar is stored     (default: ~/.local/share/jdbcli)

set -euo pipefail

GITHUB_REPO="denyshorman/jdbcli"
ASSET_NAME="jdbcli.jar"
INSTALL_DIR="${JDBCLI_INSTALL_DIR:-$HOME/.local/bin}"
JAR_DIR="${JDBCLI_JAR_DIR:-$HOME/.local/share/jdbcli}"
JAR_PATH="$JAR_DIR/$ASSET_NAME"
WRAPPER_PATH="$INSTALL_DIR/jdbcli"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

info() { echo -e "${GREEN}[jdbcli]${NC} $*"; }
warn() { echo -e "${YELLOW}[jdbcli]${NC} $*"; }
die() { echo -e "${RED}[jdbcli] ERROR:${NC} $*" >&2; exit 1; }

need_cmd() { command -v "$1" &>/dev/null || die "Required command not found: $1"; }

check_java() {
    need_cmd java
    local version
    version=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
    [ -n "$version" ] || die "Could not determine Java version."
    [ "$version" -ge 21 ] || die "Java $version found, but jdbcli requires Java 21+. Install from https://adoptium.net"
    info "Java $version detected."
}

fetch_latest_tag() {
    need_cmd curl
    local tag
    tag=$(curl -fsSL "https://api.github.com/repos/$GITHUB_REPO/releases/latest" \
          | grep '"tag_name"' | head -1 \
          | sed 's/.*"tag_name": *"\([^"]*\)".*/\1/')
    [ -n "$tag" ] || die "Could not fetch latest release tag from GitHub."
    echo "$tag"
}

main() {
    info "Installing jdbcli..."

    check_java

    local tag
    tag=$(fetch_latest_tag)
    local download_url="https://github.com/$GITHUB_REPO/releases/download/$tag/$ASSET_NAME"

    info "Downloading $tag..."

    mkdir -p "$JAR_DIR" "$INSTALL_DIR"
    curl -fsSL --progress-bar "$download_url" -o "$JAR_PATH"

    cat > "$WRAPPER_PATH" <<EOF
#!/usr/bin/env sh
exec java -XX:TieredStopAtLevel=1 -Xss256k -Xms8m -jar "$JAR_PATH" "\$@"
EOF
    chmod +x "$WRAPPER_PATH"

    info "jdbcli $tag installed."
    info "  JAR:     $JAR_PATH"
    info "  Wrapper: $WRAPPER_PATH"

    if [[ ":${PATH}:" != *":${INSTALL_DIR}:"* ]]; then
        warn ""
        warn "$INSTALL_DIR is not in your PATH."
        warn "Add the following line to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
        warn ""
        warn "  export PATH=\"\$PATH:$INSTALL_DIR\""
        warn ""
        warn "Then restart your shell or run:  source ~/.bashrc"
    else
        info ""
        info "Run:  jdbcli --help"
    fi
}

main
