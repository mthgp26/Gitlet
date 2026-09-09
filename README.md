# Gitlet — A Miniature Git, in Pure Java

Gitlet is a from-scratch, single-repository version control system that re-implements a meaningful subset of
real Git's behavior — **commit**, **branch**, **checkout**, **reset**, **log**, and three-way **merge** with
conflict resolution — using **zero third-party dependencies** (only the standard Java library).

It was designed as a deep exercise in file persistence, hashing, object graphs, and graph traversal, and is a
great talking point for systems/data-structures interviews.

---

## Highlights

- **Content-addressed storage.** Every file is stored once as an immutable *blob* keyed by its SHA-1 hash;
  identical content is deduplicated automatically.
- **Commits as immutable snapshots.** Each commit is a serialized object recording a `filename -> blobID` map,
  a timestamp, a message, and parent pointers. A commit's ID is the SHA-1 of its entire content, so history is
  tamper-evident and immutable.
- **Merge commits & DAG history.** Merging produces commits with **two parents**; history is traversed as a
  directed acyclic graph, not a linked list.
- **Three-way merge with conflict detection.** Computes the *latest common ancestor (LCA)* of two branches and
  resolves per-file: keep-current / take-given / delete / conflict (with standard `<<<<<<< HEAD ======= >>>>>>>`
  markers).
- **Custom persistence.** Objects are stored on disk via Java serialization in a `.gitlet/` directory, mirroring
  real Git's `.git/` layout (`objects/`, `refs/`, ...).
- **No runtime dependencies.** Builds and runs anywhere a JDK is installed.

## Supported commands

| Command | Description |
| --- | --- |
| `init` | Create a new repository in the current directory |
| `add <file>` | Stage a file for the next commit |
| `commit <msg>` | Commit all staged changes |
| `rm <file>` | Un-track a file (stage removal) |
| `log` / `global-log` | Show commit history |
| `find <msg>` | Find commits by message |
| `status` | Show branches, staged & removed files |
| `checkout [branch / file / commit -- file]` | Switch branches or restore files |
| `branch <name>` / `rm-branch <name>` | Create / delete branches |
| `reset <commit>` | Move current branch to a commit |
| `merge <branch>` | Merge a branch into the current branch (fast-forward + three-way merge + conflicts) |

Each command enforces the same **error semantics as Git** (e.g. refusing to overwrite untracked files, refusing
to merge with uncommitted changes, detecting "already up to date" / fast-forward cases).

---

## Architecture

```
src/main/java/gitlet/
├── Main.java          CLI entry point: parses args and dispatches commands
├── Repository.java    Top-level state: branches, HEAD, staging area; command logic & merge algorithm
├── Commit.java        Immutable commit: message, timestamp, filename->blob map, (up to two) parents
├── Blob.java          Content-addressed file snapshot (SHA-1 of its bytes)
├── Utils.java         File I/O, SHA-1 hashing, serialization helpers
└── GitletException.java / DumpObj.java / Dumpable.java   (diagnostics)
```

Key idea (mirroring real Git): **runtime objects never hold Java pointers to commits/blobs** — they reference
each other by SHA-1 strings, which is what makes on-disk persistence simple and correct.

### Merge algorithm (the interesting part)

Given the current commit `C`, the incoming branch head `G`, and their latest common ancestor `S`, each file is
classified by comparing its blobID in `S`, `C`, and `G` (`null` = deleted/absent):

- `C == G` → keep as-is (identical change, or both deleted)
- `C == S` and `G` changed → adopt `G`'s version (stage add), or delete it if `G` removed it
- `C` changed, `G == S` → keep `C`'s version
- `C` and `G` both changed differently → **conflict**, written with standard conflict markers

`findSplitPoint` performs a level-order BFS to find the common ancestor nearest to the branch heads.

---

## Build & run

Requires a JDK (17+) and, for convenience, [Maven](https://maven.apache.org) (a `./mvnw` wrapper is also
committed, so you can build without a global Maven install).

```bash
# Build — produces the runnable jar  target/gitlet.jar
./mvnw package          # (or: mvn package)

# Run — use it like git, from any directory
cd /some/scratch/dir
java -jar /path/to/gitlet/target/gitlet.jar init
java -jar /path/to/gitlet/target/gitlet.jar add wug.txt
java -jar /path/to/gitlet/target/gitlet.jar commit "initial"
java -jar /path/to/gitlet/target/gitlet.jar log
```

## Run the test suite

The repo ships a lightweight end-to-end test harness (`testing/tester.py`, needs `python3`).

```bash
make check            # compiles and runs all integration tests
```

Tests cover the full command surface, including merge failure cases, fast-forward merges, ancestor merges, and
conflict-marker output.

---

## What this was

Built as a deep-learning systems project. The merge component in particular required designing a correct LCA
search, per-file conflict classification, and merge-commit bookkeeping — a rewarding exercise in translating a
real tool's semantics into clean data structures.
