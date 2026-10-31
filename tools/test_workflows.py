"""Shell pieces of the workflows, run for real against throw-away git repositories."""
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent


def _function(workflow: str, name: str) -> str:
    text = (ROOT / ".github/workflows" / workflow).read_text()
    m = re.search(rf"^( *){name}\(\) \{{\n.*?^\1\}}\n", text, re.S | re.M)
    assert m, f"{name}() not found in {workflow}"
    return m.group(0)


def _git(*args, cwd):
    subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True)


def test_push_data_rebases_without_a_git_identity(tmp_path):
    """31-10-2026: the runner has no git name, so the rebase in push_data failed on every try and the
    1 PM result reached the data branch (app feed, github.io mirror) only 29 min later, by another run."""
    cfg = tmp_path / "gitconfig"
    cfg.write_text("[user]\n\tuseConfigOnly = true\n[init]\n\tdefaultBranch = data\n")  # never guess a name
    env = {k: v for k, v in os.environ.items()
           if not k.startswith(("GIT_AUTHOR_", "GIT_COMMITTER_")) and k != "EMAIL"}
    env.update(GIT_CONFIG_GLOBAL=str(cfg), GIT_CONFIG_NOSYSTEM="1", HOME=str(tmp_path))
    who = ["-c", "user.name=t", "-c", "user.email=t@t"]
    remote, public, other, work = (tmp_path / n for n in ("remote.git", "public", "other", "work"))
    work.mkdir()
    subprocess.run(["git", "init", "-q", "--bare", "-b", "data", str(remote)], check=True, env=env)
    for clone in (public, other):
        subprocess.run(["git", "clone", "-q", str(remote), str(clone)], check=True, env=env)
    (other / "jobs.txt").write_text("1")
    subprocess.run(["git", *who, "-C", str(other), "add", "-A"], check=True, env=env)
    subprocess.run(["git", *who, "-C", str(other), "commit", "-qm", "base"], check=True, env=env)
    subprocess.run(["git", "-C", str(other), "push", "-q", "origin", "data"], check=True, env=env)
    subprocess.run(["git", "-C", str(public), "pull", "-q", "origin", "data"], check=True, env=env)
    # jobs-publish pushes in between: our push is rejected and must be rebased
    (other / "jobs.txt").write_text("2")
    subprocess.run(["git", *who, "-C", str(other), "commit", "-qam", "jobs"], check=True, env=env)
    subprocess.run(["git", "-C", str(other), "push", "-q", "origin", "data"], check=True, env=env)
    (public / "result.json").write_text("{}")
    subprocess.run(["git", "-C", str(public), "add", "-A"], check=True, env=env)
    subprocess.run(["git", *who, "-C", str(public), "commit", "-qm", "results"], check=True, env=env)
    script = _function("results.yml", "push_data").replace("sleep $(( i * 3 ))", "true") + "push_data\n"
    r = subprocess.run(["bash", "-c", script], cwd=work, env=env, capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr
    log = subprocess.run(["git", "-C", str(remote), "log", "--format=%s", "data"], capture_output=True, text=True, env=env)
    assert log.stdout.split() == ["results", "jobs", "base"]
