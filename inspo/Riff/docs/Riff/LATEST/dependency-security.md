# Third-party dependency security

> Owner/scope: dependency inventory, recurring vulnerability review and update acceptance.

## Monthly CVE review

All third-party components used by the project MUST be checked for published CVEs at least once per
calendar month, with no more than one calendar month between completed reviews. This includes direct
and resolved transitive application libraries, vendored native/source libraries, standalone library
dependencies, and build, test, documentation and maintenance-tool dependencies. A release dependency
change also requires a review of the resulting resolved graph before distribution.

Use authoritative CVE records and upstream security advisories. Ecosystem indexes such as OSV and
GitHub Advisories may map exact packages/versions to those records. Check vendored revisions and
bundled native components explicitly; absence from an ecosystem index is not evidence of safety.
Record component/version or revision, scope, query date, sources, CVE/advisory identifiers,
affected-version/reachability assessment and remediation. Distinguish no matching published advisory
from an unperformed, failed or unsupported lookup.

## Inventory and remediation

Pin adopted versions and retain their license notices and imported-source provenance. Review both
declared dependencies and the resolved runtime/test/tool graph. Record bundled components and local
patches separately; a top-level library version does not identify every native dependency. Keep the
inventory, commands and machine-readable results with release evidence under the canonical project
status directory; publish a concise review and next due date in the Riff worklog.

For an applicable vulnerability, upgrade to a fixed supported release or remove the affected
dependency when the approved behavior permits it. An unresolved applicable finding requires an
explicit documented disposition before release; severity alone does not establish applicability.
Report failed coverage instead of declaring a clean scan.

## Update acceptance and derived data

Verify upstream release dates against the requested cutoff and adopt stable releases with focused
API, behavior, packaging and license checks. Regenerate compiler bindings and update their matching
runtime together. Test standalone exports, Android boundaries and actual APK permissions/components.

A change that can alter metadata, identity, decoder observations, gain or inference output MUST
invalidate affected derived data through its existing compatibility gate. The
[library lifecycle](/Riff/LATEST/indexer-library-lifecycle#automatic-library-recreation) owns
complete AU+IA recreation and retained generation watermarks. Pre-release updates use a clean cut
without migration or compatibility readers. Dependency version changes alone do not redefine source
identity or a wire format; their owners decide those semantics.

This contract requires a recurring review and evidence. It does not itself create a scheduler or
grant permission to disclose private inventories to an unrelated service.
