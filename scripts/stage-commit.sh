#!/usr/bin/env bash
set -euo pipefail

# Postbox Granular Git Staging & Commit Helper
# Facilitates staging individual lines, hunks, or files with clear descriptive messages.

VALID_TYPES="feat|fix|refactor|test|build|chore|docs|style|perf"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/stage-commit.sh -p [path...]            Interactive hunk/line staging (`git add -p`) + commit
  ./scripts/stage-commit.sh -m "<msg>" <path...>    Stage specific path(s) and commit with <msg>
  ./scripts/stage-commit.sh -i [path...]            Interactive guided per-file/hunk staging & commit prompt
  ./scripts/stage-commit.sh --remote [url] [name]   Inspect or configure git remote and push main
  ./scripts/stage-commit.sh --status                Show unstaged, staged, and recent commit summary
  ./scripts/stage-commit.sh -h | --help             Show this help message

Commit Message Guidelines:
  Prefer Conventional Commits: <type>(<scope>): <clear description>
  Valid types: feat, fix, refactor, test, build, chore, docs, style, perf
  Examples:
    feat(search): filter inbox emails by sender email address
    fix(contacts): trim whitespace before validating email input
    chore(gitignore): ignore generated npm tarballs

Interactive Patch Tips (when using -p):
  y = stage this hunk       n = skip this hunk
  s = split into smaller    e = manually edit lines to stage
EOF
}

die() {
  echo "Error: $*" >&2
  exit 1
}

ensure_git_repo() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not inside a Git repository."
}

validate_message() {
  local msg="$1"
  local trimmed
  trimmed="$(printf '%s' "$msg" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

  [ -n "$trimmed" ] || die "Commit message cannot be empty."
  [ "${#trimmed}" -ge 10 ] || die "Commit message is too short (${#trimmed} chars). Provide a clear description (>= 10 chars)."

  local lower
  lower="$(printf '%s' "$trimmed" | tr '[:upper:]' '[:lower:]')"
  case "$lower" in
    wip|update|updates|change|changes|fix|misc|test|commit|stuff|temp)
      die "Commit message '$trimmed' is too vague. Describe what changed and why."
      ;;
  esac
}

prompt_commit_message() {
  local suggested_scope="${1:-}"
  local msg_type=""
  local msg_scope=""
  local msg_summary=""

  echo ""
  echo "Enter commit details (types: feat, fix, refactor, test, build, chore, docs, style, perf):"
  read -r -p "  Type [feat]: " msg_type
  msg_type="${msg_type:-feat}"
  if ! printf '%s' "$msg_type" | grep -Eq "^(${VALID_TYPES})$"; then
    die "Invalid commit type '$msg_type'. Expected one of: ${VALID_TYPES//|/, }"
  fi

  if [ -n "$suggested_scope" ]; then
    read -r -p "  Scope [${suggested_scope}] (optional, '-' for none): " msg_scope
    if [ "$msg_scope" = "-" ]; then
      msg_scope=""
    elif [ -z "$msg_scope" ]; then
      msg_scope="$suggested_scope"
    fi
  else
    read -r -p "  Scope (optional): " msg_scope
  fi

  read -r -p "  Summary (imperative, clear description): " msg_summary
  local full_msg=""
  if [ -n "$msg_scope" ]; then
    full_msg="${msg_type}(${msg_scope}): ${msg_summary}"
  else
    full_msg="${msg_type}: ${msg_summary}"
  fi

  validate_message "$full_msg"
  COMMIT_MESSAGE="$full_msg"
}

verify_no_secrets_staged() {
  local staged_files
  staged_files="$(git diff --cached --name-only)"
  if printf '%s\n' "$staged_files" | grep -Eiq '(^\.env($|\.)|\.env$|\.jks$|\.keystore$|\.p12$|\.pfx$|\.pem$|\.key$|\.ppk$|\.secret$|service[-_]?account.*\.json$|-credentials\.json$|google-services.*\.json$|firebase_applet_config\.xml$|(^|/)id_(rsa|ed25519|ecdsa))'; then
    die "Blocked commit: staged file matches ignored key, certificate, .env, or service-account pattern."
  fi

  local added_lines
  added_lines="$(git diff --cached -U0 -- ':!scripts/stage-commit.sh' ':!.gitignore' ':!.dockerignore' | grep '^+[^+]' || true)"
  if printf '%s\n' "$added_lines" | grep -Eq '(-----BEGIN ([A-Z ]+)?PRIVATE KEY-----|"type"[[:space:]]*:[[:space:]]*"service_account"|"private_key_id"[[:space:]]*:[[:space:]]*"|AIza[0-9A-Za-z_-]{35}|ghp_[0-9A-Za-z]{36}|AKIA[0-9A-Z]{16})'; then
    die "Blocked commit: staged diff contains a private key, service-account JSON key, or API secret."
  fi
}

commit_staged() {
  local msg="$1"
  if git diff --cached --quiet; then
    die "No changes are currently staged for commit."
  fi

  verify_no_secrets_staged
  validate_message "$msg"

  echo ""
  echo "Staged changes to be committed:"
  git diff --cached --stat
  echo ""
  git commit -m "$msg"
  push_commit
}

push_commit() {
  if [ "${NO_PUSH:-0}" = "1" ]; then
    return 0
  fi

  local remote=""
  remote="$(git config --get "branch.$(git symbolic-ref --short HEAD 2>/dev/null || echo main).remote" 2>/dev/null || true)"
  if [ -z "$remote" ] && git remote get-url origin >/dev/null 2>&1; then
    remote="origin"
  fi

  if [ -n "$remote" ]; then
    echo "Pushing commit to remote '$remote'..."
    git push -u "$remote" HEAD
  else
    echo "No git remote configured; commit saved locally on $(git rev-parse --short HEAD)."
  fi
}

configure_remote() {
  local remote_url="${1:-}"
  local remote_name="${2:-origin}"

  if [ -z "$remote_url" ]; then
    echo "=== Configured Git Remotes ==="
    if [ -z "$(git remote -v)" ]; then
      echo "No git remote configured yet."
      echo "Run: $0 --remote <url> [remote_name]"
      return 0
    fi
    git remote -v
    return 0
  fi

  if git remote get-url "$remote_name" >/dev/null 2>&1; then
    git remote set-url "$remote_name" "$remote_url"
    echo "Updated remote '$remote_name' -> $remote_url"
  else
    git remote add "$remote_name" "$remote_url"
    echo "Added remote '$remote_name' -> $remote_url"
  fi

  local current_branch
  current_branch="$(git symbolic-ref --short HEAD 2>/dev/null || echo main)"
  echo "Pushing branch '$current_branch' to '$remote_name'..."
  git push -u "$remote_name" "$current_branch"
}

show_status() {
  echo "=== Working Tree Status ==="
  git status -sb
  echo ""
  echo "=== Recent Commits ==="
  git log -n 10 --oneline || true
}

run_patch_mode() {
  local message=""
  local paths=()

  while [ "$#" -gt 0 ]; do
    case "$1" in
      -m|--message)
        shift
        [ "$#" -gt 0 ] || die "Missing value for -m/--message"
        message="$1"
        ;;
      *)
        paths+=("$1")
        ;;
    esac
    shift
  done

  if [ "${#paths[@]}" -gt 0 ]; then
    git add -p -- "${paths[@]}"
  else
    git add -p
  fi

  if git diff --cached --quiet; then
    echo "No hunks staged; aborting without creating a commit."
    exit 0
  fi

  if [ -z "$message" ]; then
    prompt_commit_message
    message="$COMMIT_MESSAGE"
  fi

  commit_staged "$message"
}

run_interactive_mode() {
  local targets=("$@")
  local files=()

  if [ "${#targets[@]}" -gt 0 ]; then
    while IFS= read -r line; do
      [ -n "$line" ] && files+=("$line")
    done < <(git status --porcelain -- "${targets[@]}" | awk '{print $NF}')
  else
    while IFS= read -r line; do
      [ -n "$line" ] && files+=("$line")
    done < <(git status --porcelain | awk '{print $NF}')
  fi

  if [ "${#files[@]}" -eq 0 ]; then
    echo "Working tree is clean. Nothing to stage or commit."
    exit 0
  fi

  for file in "${files[@]}"; do
    echo ""
    echo "------------------------------------------------------------"
    echo "File: $file"
    if git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
      echo "Staging hunks interactively for tracked file '$file'..."
      git add -p -- "$file"
    else
      local ans=""
      read -r -p "Untracked file '$file'. Stage it for commit? [y/N]: " ans
      case "$ans" in
        y|Y|yes|YES)
          git add -- "$file"
          ;;
        *)
          echo "Skipped '$file'."
          continue
          ;;
      esac
    fi

    if ! git diff --cached --quiet; then
      local base_scope
      base_scope="$(basename "$file" | sed 's/\.[^.]*$//' | tr '[:upper:]' '[:lower:]')"
      prompt_commit_message "$base_scope"
      commit_staged "$COMMIT_MESSAGE"
    fi
  done
}

main() {
  ensure_git_repo

  [ "$#" -gt 0 ] || {
    usage
    exit 1
  }

  case "$1" in
    -h|--help)
      usage
      ;;
    --remote)
      shift
      configure_remote "$@"
      ;;
    --status)
      show_status
      ;;
    -p|--patch)
      shift
      run_patch_mode "$@"
      ;;
    -i|--interactive)
      shift
      run_interactive_mode "$@"
      ;;
    -m|--message)
      shift
      [ "$#" -ge 2 ] || die "Usage: $0 -m \"<message>\" <path...>"
      local msg="$1"
      shift
      git add -A -- "$@"
      commit_staged "$msg"
      ;;
    *)
      die "Unknown option '$1'. Run '$0 --help' for usage."
      ;;
  esac
}

main "$@"
