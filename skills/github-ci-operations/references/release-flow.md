# GitHub Release Flow

Use this reference for tags, GitHub releases, release notes, artifacts, or
release workflows.

## Ground Rules

- A release is not just a tag. Track version files, changelog or generated
  notes, artifacts, validation, publication target, rollback path, and
  post-release verification.
- Ask before creating, deleting, moving, or force-updating tags.
- Ask before publishing packages, deploying, or changing protected
  environments.
- Prefer draft releases until artifacts and notes are verified.

## Useful Commands

```sh
gh release list --limit 10
gh release view <tag> --json tagName,name,isDraft,isPrerelease,url,targetCommitish
gh release create <tag> --draft --generate-notes --target <branch-or-sha>
gh release upload <tag> <artifact-path>
gh release edit <tag> --draft=false
```

## Planning Checklist

- Version source: package file, Gradle property, Cargo manifest, Homebrew tap,
  changelog, or tag-only.
- Validation: tests, build, packaging, checksums, docs, and smoke test.
- Artifacts: names, checksums, provenance, and upload path.
- Notes: generated notes plus human edits for breaking changes or migration.
- Automation: workflow trigger, permissions, secrets, environment, and
  concurrency.
- Rollback: previous release, package unpublish constraints, and hotfix path.

## Post-Release Verification

- Confirm the release URL and target SHA.
- Confirm artifacts download and checksums match.
- Confirm package registries, taps, docs, or deployment targets see the new
  version.
- Record any follow-up cleanup in the repo's durable tracking surface.
