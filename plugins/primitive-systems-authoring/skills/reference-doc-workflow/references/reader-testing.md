# Reader Testing

Use reader testing to check whether a document works without the drafting
conversation. The test should answer whether a fresh reader can find the right
information, trust the claims, and take the intended action.

## Test Questions

Generate questions from the reader's likely jobs:

- What problem or decision is this document about?
- What should I do after reading it?
- Which rules are mandatory and which are guidance?
- What source owns this fact or contract?
- What commands, checks, or evidence prove the claim?
- What context does the document assume I already know?
- What would I misunderstand if I only read this document?

## Fresh-Reader Pass

Use a subagent when one is available. Give it only the document content and the
test questions. Ask it to report:

- answers it can find directly;
- answers it has to infer;
- ambiguous terms or missing definitions;
- unsupported claims;
- contradictions;
- likely next questions from a real reader.

When no subagent is available, perform the same pass yourself, but explicitly
ignore conversation context that is not in the document.

## Fix Loop

For each issue:

1. Identify the section that should carry the missing information.
2. Add the smallest text, link, example, or validation command that closes the
   gap.
3. Remove any workaround text that only makes sense to the original authors.
4. Re-run the affected reader question.

The document is ready when the reader can answer the core questions from the
document alone.
