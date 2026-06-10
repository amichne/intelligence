# Good and Bad Tests

## Good Tests

**Integration-style**: Test through real interfaces, not mocks of internal parts.

```text
// GOOD: Tests observable behavior
given a valid cart and payment method
when checkout is requested through the public checkout interface
then the returned order is confirmed
```

Characteristics:

- Tests behavior users/callers care about
- Uses public API only
- Survives internal refactors
- Describes WHAT, not HOW
- One logical assertion per test

## Bad Tests

**Implementation-detail tests**: Coupled to internal structure.

```text
// BAD: Tests implementation details
when checkout is requested
then assert the internal payment collaborator received a specific call
```

Red flags:

- Mocking internal collaborators
- Testing private methods
- Asserting on call counts/order
- Test breaks when refactoring without behavior change
- Test name describes HOW not WHAT
- Verifying through external means instead of interface

```text
// BAD: Bypasses interface to verify
when createUser is called
then query the database table directly and assert the row exists

// GOOD: Verifies through interface
when createUser is called
then getUser returns the created user by public identifier
```
