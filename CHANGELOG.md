# Changelog

## Unreleased

No version bump: the SDK has never been released (no tags, nothing published), so it stays at 1.0.0.

### BREAKING

- Reads before the context is ready or after close now throw `IllegalStateException`, matching the JavaScript SDK (`_checkReady`). Affected: `getTreatment`, `peekTreatment`, `getVariableValue`, `peekVariableValue`, `variableKeys`, `experiments`, `customFieldKeys`, `getCustomFieldValue`, `getCustomFieldValueType`.
  - Old (this PR branch): returned defaults (`0`, the default value, empty collections, `null`).
  - New: throws, with the messages below.
- Error messages now match JavaScript casing and wording:
  - `"ABSmartly Context is not yet ready"` -> `"ABsmartly Context is not yet ready."`
  - `"ABSmartly Context is closed"` -> `"ABsmartly Context is finalized."`
  - `"ABSmartly Context is closing"` -> `"ABsmartly Context is finalizing."`
