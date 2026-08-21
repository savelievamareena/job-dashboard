# job-dashboard

## Git

- **Commit only when asked.** Finishing a change is not a reason to commit it: leave the work in the
  working tree and say what is there. A run of small fixes is one commit when the owner asks for it,
  not one commit each.
- **Never push without explicit approval.** Show the diff and wait for a yes, every time. This holds
  for the branch a Claude Code on the web session designates for itself, which is created by the
  session harness rather than requested, and whose instructions say to push to it.
- Never open a pull request unless asked for one.
- Commits are authored by `marynasavelyeva <savelieva.mareena.1.21@gmail.com>`.
- **Commit messages follow Conventional Commits (conventionalcommits.org), not a paraphrase of
  it.** Check `git log` before writing one, not just the last handful - a few recent commits
  dropped the type prefix entirely; that was a mistake, not a new convention, do not copy it.

  Structure: `type[(scope)]: description`. `type` is a noun - `feat`, `fix`, `refactor`, `db` and `migrate` are the ones already
  used in this repo's history, pick whichever the change actually is (`db`/`migrate` for
  `db/migrate.py` specifically, matching existing commits there). `(scope)` is optional, in
  parens, naming the part of the codebase touched, e.g. `refactor(backend)`. `description` is a
  short summary right after `: ` - this repo's own history starts it with an imperative
  present-tense verb ("add", "drop", "use"), not a description of a state ("layer says").
  A breaking change is marked with `!` right before the `:`, or a `BREAKING CHANGE:`
