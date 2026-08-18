#!/usr/bin/env python3
"""Publish the Promptpad MediaPipe model to a public Hugging Face repo."""

import argparse
import hashlib
import json
import os
import shlex
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TASK = ROOT / "ml" / "export" / "prompt-pad-gemma3-270m.task"
DEFAULT_REPO = "findethics-labs/prompt-pad-gemma3-270m"


def load_dotenv(path: Path) -> dict[str, str]:
    values = {}
    if not path.is_file():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, separator, value = line.partition("=")
        if separator and key.strip():
            parts = shlex.split(value.strip()) if value.strip() else []
            values[key.strip()] = parts[0] if parts else ""
    return values


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def confirm() -> None:
    try:
        answer = input("Type PUBLISH to create/update the public HF model repo: ")
    except EOFError as error:
        raise SystemExit("Publish cancelled: confirmation was not provided.") from error
    if answer.strip() != "PUBLISH":
        raise SystemExit("Publish cancelled: confirmation did not match PUBLISH.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task", type=Path, default=DEFAULT_TASK)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--public", action="store_true",
                        help="required safety gate: create/use a public model repo")
    parser.add_argument("--yes", action="store_true",
                        help="skip the interactive PUBLISH confirmation")
    args = parser.parse_args()

    if not args.public:
        parser.error("--public is required; the model repo must be public for tokenless downloads")

    task = args.task if args.task.is_absolute() else ROOT / args.task
    if not task.is_file():
        print(f"Model file not found: {task}", file=sys.stderr)
        return 2

    token = os.environ.get("HF_TOKEN") or load_dotenv(ROOT / ".env").get("HF_TOKEN")
    if not token:
        print("HF_TOKEN is missing. Put a fresh token in .env or the environment.", file=sys.stderr)
        return 2

    size = task.stat().st_size
    model_hash = sha256(task)
    print(f"File:       {task}")
    print(f"Size:       {size:,} bytes")
    print(f"SHA-256:    {model_hash}")
    print(f"HF repo:    {args.repo}")
    print("Visibility: public")
    if not args.yes:
        confirm()

    try:
        from huggingface_hub import HfApi
    except ImportError as error:
        print("huggingface_hub is required; install it in the ML environment.", file=sys.stderr)
        return 2

    api = HfApi(token=token)
    api.create_repo(args.repo, repo_type="model", private=False, exist_ok=True)
    api.update_repo_settings(args.repo, private=False, repo_type="model")
    model_commit = api.upload_file(
        path_or_fileobj=str(task),
        path_in_repo=task.name,
        repo_id=args.repo,
        repo_type="model",
        commit_message=f"Publish {task.name}",
    )
    commit_sha = getattr(model_commit, "oid", None) or getattr(model_commit, "commit_oid", None)
    if not commit_sha:
        raise RuntimeError("Hugging Face upload did not return a commit SHA")

    manifest = {
        "version": commit_sha,
        "url": f"https://huggingface.co/{args.repo}/resolve/{commit_sha}/{task.name}",
        "sha256": model_hash,
    }
    manifest_bytes = (json.dumps(manifest, indent=2) + "\n").encode("utf-8")
    with tempfile.NamedTemporaryFile(prefix="prompt-pad-manifest-", suffix=".json") as stream:
        stream.write(manifest_bytes)
        stream.flush()
        manifest_commit = api.upload_file(
            path_or_fileobj=stream.name,
            path_in_repo="model-manifest.json",
            repo_id=args.repo,
            repo_type="model",
            commit_message=f"Publish manifest for {task.name}",
        )

    manifest_url = f"https://huggingface.co/{args.repo}/resolve/main/model-manifest.json"
    print(f"Model commit:    {commit_sha}")
    print(f"Manifest commit: {getattr(manifest_commit, 'oid', 'unknown')}")
    print(f"Manifest URL:    {manifest_url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
