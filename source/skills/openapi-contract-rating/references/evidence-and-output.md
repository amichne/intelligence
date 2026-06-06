# Evidence And Output

Use this reference when writing an OpenAPI rating or review result.

## Evidence To Seek

Prefer evidence in this order:

1. failing or passing local validation command;
2. generator test, generated client/server compilation, docs build, or lint
   output;
3. changed spec diff plus local repo rules;
4. published spec or previous checked-in spec as a compatibility baseline;
5. inspection-only reasoning when no command or baseline is available.

Name the evidence level in the response.

## Finding Format

Lead with findings. Each finding should include:

- severity;
- file and line when available;
- object under review;
- criterion violated;
- evidence;
- invalid or ambiguous state currently allowed;
- baseline considered;
- confidence;
- concrete fix;
- verification that would prove the fix.

## Scorecard Format

Use a compact table:

```text
Axis                               Score  Evidence
Boundary authority                 3      validator and generator test pass
Operation completeness             2      responses typed, headers missing
Schema invariants                  1      IDs and statuses are raw strings
Variant and subtype modeling       0      prose-only type field
Error/security/failure contracts   2      error envelope exists, codes loose
Self-description/examples          2      success examples only
Evolution/compatibility            unknown no baseline available
```

If more than two axes are `unknown`, do not give an overall rating. State that a
firm aggregate judgment is not justified.

## No-Finding Response

If no issue is justified, say so directly and include:

- boundaries reviewed;
- commands run;
- schema mechanisms that prevent invalid states;
- remaining uncertainty, such as missing invalid fixtures or unverified
  generator output.
