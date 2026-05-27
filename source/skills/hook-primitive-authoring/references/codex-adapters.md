# Codex Hook Adapters

Use this reference when editing `source/hooks/codex/*.hooks.json`.

## Shape

Codex adapter files should describe provider events and call a hook
implementation from the materialized plugin payload. The authored adapter lives
under `source/hooks/codex/`, but command paths use runtime payload paths such as
`hooks/example.py`:

```json
{
  "$schema": "https://json.schemastore.org/codex-hooks.json",
  "description": "Short behavior description.",
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "python3 hooks/example.py --repo .",
            "timeout": 30,
            "statusMessage": "Checking example policy"
          }
        ]
      }
    ]
  }
}
```

## Rules

- Keep command strings short and rooted in canonical hook scripts.
- Set realistic timeouts.
- Use `statusMessage` to describe what is being checked.
- Do not inline complex shell pipelines when a script would be clearer and more
  testable.
- If the provider event payload matters, the implementation should tolerate
  missing or malformed input and fail with an actionable message.

## Common Events

- `UserPromptSubmit`: initialize turn or prompt-scoped state.
- `PostToolUse`: record tool effects or changed paths.
- `Stop`: enforce end-of-turn checks, summarize state, or add context.

Check the existing adapter files before adding a new event so the repo's hook
behavior remains predictable.
