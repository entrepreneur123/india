"""Tests for the Claude Code hooks in .claude/hooks (run: python3 -m pytest -q tools/test_hooks.py)."""
import json
import pathlib
import subprocess
import sys

HOOKS = pathlib.Path(__file__).resolve().parents[1] / ".claude" / "hooks"


def guard(command: str) -> bool:
    """True when guard_bash.py denies the command."""
    out = subprocess.run([sys.executable, str(HOOKS / "guard_bash.py")], input=json.dumps({"tool_input": {"command": command}}),
                         capture_output=True, text=True).stdout
    return '"deny"' in out


def test_self_killing_commands_are_denied():
    assert guard("pkill -f readall.py")
    assert guard("ls; pkill -f x")
    assert guard("sudo killall python3")
    assert guard("x=$(pkill -f y)")


def test_text_that_only_mentions_them_passes():
    assert not guard('echo "the hook denies `pkill -f` here"')
    assert not guard("cat <<E\n- PreToolUse denies `pkill -f`\nE")
    assert not guard("ps aux | grep tesseract; kill 1234")


def test_pushes_go_only_to_the_work_branch():
    assert not guard("git push -q -u origin claude/gallant-lovelace-ynoa63")
    assert not guard("git push")
    assert guard("git push origin data")
    assert guard("git push origin HEAD:ops")
    assert guard("git push --force origin claude/gallant-lovelace-ynoa63")
    assert guard("git push origin +claude/gallant-lovelace-ynoa63")
