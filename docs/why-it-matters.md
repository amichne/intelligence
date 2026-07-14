# Why It Matters

Provider-neutral authoring is useful only when conversion remains separate from
runtime installation and configuration.

Intelligence keeps that boundary explicit:

| Concern | Owner |
|---|---|
| Reusable marketplace source | `amichne/slopsentral/source/` |
| Source and target contracts | `schemas/` |
| Deterministic conversion | `intelligence project` |
| Harness registration and installation | The target harness or its operator |
| Marketplace publication | External release automation, outside the projector |

The small boundary makes generated output replaceable. A harness adapter can
change without turning source authoring into provider-specific configuration.
