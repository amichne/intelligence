# Evaluation Scaffold

Use this reference when a skill needs evidence that it improves behavior or
when overlapping skills are being consolidated.

## Durable Assets

- `evals/catalog.json`: canonical eval cases.
- `evals/pain_points.jsonl`: intake queue for new failures or follow-up
  corrections.
- `evals/files/`: fixtures referenced by catalog cases.
- `history/progression.json`: accepted benchmark and promotion history.

Transient benchmark workspaces, review HTML, raw model outputs, and scratch
feedback should live outside the packaged skill.

## Case Lifecycle

- `candidate`: newly added and not yet trusted.
- `holdout`: stable enough to block obvious regressions.
- `core`: permanent non-regression gate.
- `retired`: kept for history but no longer active.

Promote cases only after successful accepted runs. Do not claim improvement by
removing hard cases.

## Benchmark Shape

For each case, compare the candidate skill against an explicit baseline:

- `with_skill` against `without_skill`;
- `candidate_skill` against `old_skill`;
- `consolidated_skill` against one or more legacy siblings;
- named model variants when model portability matters.

Keep per-case metadata with the run artifacts so aggregate reports can join
outputs back to stable case ids.

## Consolidation Proof

A consolidated skill is stronger only when evidence shows it covers the useful
legacy behavior with less overlap. The proof should show:

- the legacy skills or sources being replaced;
- the union of cases or representative prompts;
- the candidate result compared with each baseline;
- any tolerated regression and why it is acceptable;
- the retained provenance for future cleanup.
