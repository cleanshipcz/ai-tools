# AI Tools CLI

Runs the AI Tools engine against the manifests described by your `config.yml`.

## Usage

Run from the repository root (recommended so `config.yml` is found):

```bash
./ai-tools-engine/gradlew :cli:run
```

Specify an explicit working directory if needed:

```bash
./ai-tools-engine/gradlew :cli:run --args="--working-dir /path/to/ai-tools"
```

Or. e.g.

```bash
cd ai-tools-engine
./gradlew :cli:run -DLOG_FORMAT=TEXT --args="--working-dir /home/blaha/Documents/Projects/ai-tools"
```

## Options

- `--working-dir`: Root directory containing `config.yml` (and optional `config.local.yml`). Defaults to the current working directory.
