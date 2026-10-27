# Changelog

## Unreleased

No version bump: the SDK has never been released (no tags, nothing published), so it stays at 1.0.0.

### Fixed

- Refresh on a context whose initial load failed is a successful no-op and the context stays failed (refresh used to clear the failed state).
- The `Ready` event is logged before readiness is signalled, so a caller woken by `waitUntilReady` already sees it.
- A failed refresh logs an `Error` event.

### Added

- `Hashing.utf8Bytes` and `Hashing.base64UrlNoPadding`, the encoders used for unit hashing.

### BREAKING

- Units keep the type they were set with. `setUnit(type, Number)` and `ContextConfig.setUnit(type, Number)` are new; `getUnit` returns `Any?` and `getUnits`/`ContextConfig.getUnits` return `Map<String, Any>`. A numeric UID hashes as its rounded integer form, like JavaScript's `toFixed(0)` (`125.75` hashes as `"126"`). Units given in `ContextConfig` are validated the same way as `setUnit`.
- `close()` whose final publish fails now throws the publish error, keeps the events queued and leaves the context open so it can be retried. Before, the context was closed anyway.
- The `Publish` event is logged after the publisher delivers the batch, not before; a failed publish logs only `Error`.
- Reads before the context is ready or after close now throw `IllegalStateException`, matching the JavaScript SDK (`_checkReady`). Affected: `getTreatment`, `peekTreatment`, `getVariableValue`, `peekVariableValue`, `variableKeys`, `customFieldKeys`, `getCustomFieldValue`, `getCustomFieldValueType`. `experiments` only requires ready and stays available after close.
  - Old (this PR branch): returned defaults (`0`, the default value, empty collections, `null`).
  - New: throws, with the messages below.
- Error messages now match JavaScript casing and wording:
  - `"ABSmartly Context is not yet ready"` -> `"ABsmartly Context is not yet ready."`
  - `"ABSmartly Context is closed"` -> `"ABsmartly Context is finalized."`
  - `"ABSmartly Context is closing"` -> `"ABsmartly Context is finalizing."`
