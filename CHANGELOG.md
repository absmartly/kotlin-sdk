# Changelog

## Unreleased

No version bump: the SDK has never been released (no tags, nothing published), so it stays at 1.0.0.

### Fixed

- Refresh on a context whose initial load failed is a successful no-op and the context stays failed (refresh used to clear the failed state).
- The `Ready` event is logged before readiness is signalled, so a caller woken by `waitUntilReady` already sees it.
- A failed refresh logs an `Error` event.

### Added

- Assignment rules (`Experiment.assignmentRules`): the first matching `assign` or `split` rule sets the variant ahead of the audience, traffic and custom assignments, and is exposed with `Exposure.ruleOverride`. An object or array sent in place of the rules string is ignored rather than failing the context.
- `semver_eq`, `semver_gt`, `semver_gte`, `semver_lt` and `semver_lte` JSON expression operators.
- `ContextConfig.setIncludeSystemAttributes`: exposes `app_version` to conditions and publishes `sdk_name`, `sdk_version`, `application`, `environment` and `app_version`.
- `ClientConfig.setApplicationVersion` and `Client.applicationVersion`, `Client.agentVersion`, `Client.agent`. `X-Agent` is now `absmartly-kotlin-sdk/<version>`.
- A tagged-release workflow publishing to Maven Central, with sources and Javadoc JARs.
- `Hashing.utf8Bytes` and `Hashing.base64UrlNoPadding`, the encoders used for unit hashing.
- Audiences and assignment-rule conditions now always see the configured `application` and `environment` names (the same names the collector attaches to every exposure server-side). The values are overlaid by any user attribute of the same name, including one set after an assignment was cached (the cache is invalidated). Empty or unset names are left out. `app_version` is provided only with `setIncludeSystemAttributes(true)`.

  This changes assignment for running experiments. A unit whose experiment has Audience enforced and an `environment` or `application` condition currently gets variant 0, because the SDK never supplied those attributes, so a strict audience always mismatched. After upgrading, that unit is randomised normally. Those experiments are broken today; this fixes them, but the assignment they produce will change.

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
