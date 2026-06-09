---
name: "grill-me-with-docs"
description: "Run a docs-backed interrogation of a user's understanding by reading scoped documentation, asking pointed questions, checking answers against the source material, and recording gaps. Use when the user asks to be grilled, quizzed, challenged, or tested on docs, specs, runbooks, or project reference material."
---

# Grill Me With Docs

Use this skill to test whether a user can reason from documentation, not from
memory or vibes. The source material owns the answer key. If the docs are
ambiguous, stale, or contradicted by code, say that instead of inventing
certainty.

This primitive is authored locally from the requested workflow and does not copy
external first-party skill text.

## Operating Contract

- Establish the document scope before asking questions.
- Read the relevant docs before forming the question set.
- Ask questions that require applying the docs, not reciting headings.
- Check answers against specific source passages or repo evidence.
- Keep the session interactive: ask one question at a time unless the user asks
  for a written exam.
- Do not reveal the expected answer before the user has answered.
- Treat unclear docs as findings against the docs, not failures by the user.

## Workflow

1. Define the scope.
   Identify the docs, specs, runbooks, or repository paths to use as the answer
   source. If the user did not name them, inspect likely project docs and state
   the assumed scope.

2. Build the answer key.
   Read the scoped material and extract the behaviors, invariants, commands,
   edge cases, and decision rules that a competent reader should understand.

3. Ask the first question.
   Prefer scenario questions, failure analysis, "what would you run next",
   boundary cases, and contradiction checks. Keep each question answerable from
   the scoped docs.

4. Grade the answer.
   Classify the answer as correct, partially correct, unsupported, or
   contradicted. Cite the exact local file path or document section that supports
   the grade.

5. Tighten the follow-up.
   If the user is correct, increase difficulty or move to a new area. If the
   user misses something, ask a narrower follow-up that isolates the gap.

6. Record unresolved gaps.
   Distinguish user knowledge gaps from documentation defects, missing evidence,
   stale instructions, and code/docs drift.

## Completion Criteria

- The session used scoped documentation as the answer source.
- Each grade was tied to source evidence or an explicit documentation gap.
- Follow-up questions targeted real misunderstandings rather than generic quiz
  filler.
- Any doc defects or stale claims discovered during the grill were reported
  separately from the user's answer quality.
