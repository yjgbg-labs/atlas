# Atlas

A triple-store MCP server. Stores facts as `(subject, predicate, object)` triples in a local JSONL file.

## Install

```bash
curl -fsSL https://raw.githubusercontent.com/yjgbg-labs/atlas/master/install.sh | bash
```

Clones to `~/.local/share/atlas` and compiles. Data is stored at `~/.atlas/db.jsonl`.

### MCP Config

```json
{
  "atlas": {
    "command": "scala",
    "args": ["run", "/home/emma/.local/share/atlas"]
  }
}
```
```

## MCP Tools

### insert

Record a triple. Always creates a new entry.

| Parameter | Type | Description |
|-----------|------|-------------|
| subject | string | The subject |
| predicate | string | The predicate |
| object | string | The object |

### search

Full-text search across all triples. Space-separated keywords, OR matching.

| Parameter | Type | Description |
|-----------|------|-------------|
| keywords | string | Search keywords |

### retrieve

Get all triples of a subject, with their direct and indirect relations to other subjects.

| Parameter | Type | Description |
|-----------|------|-------------|
| subject | string | Subject name |

### delete

Delete a triple.

| Parameter | Type | Description |
|-----------|------|-------------|
| subject | string | Subject |
| predicate | string | Predicate |
| object | string | Object |

## Data Model

```json
{
  "subject": "Emma",
  "predicate": "prefers",
  "object": "FastAPI",
  "createdAt": "2026-05-13T..."
}
```

- When `object` matches another `subject` in the store, it forms a **relation** — `retrieve` follows these links.
- When `object` does not match any `subject`, it is a **property** — a terminal fact.

## Requirements

- Scala CLI (`scala`)
- JVM 21+
