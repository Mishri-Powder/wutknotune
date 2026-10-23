# Riff distribution and licensing

> Owner/scope: cross-system licensing, signing and release distribution

## Distribution and licensing

All first-party Riff application source code, build scripts, tests, and distributed derivative works
are licensed under the GNU General Public License, version 3 or (at the recipient's option) any
later version (GPL-3.0-or-later). The repository and every source or binary distribution MUST
include the complete GPL text and preserve this licensing notice. Donations and voluntary financial
support do not create a feature, source-access, or usage restriction.

The independent [Cuegrain library](/Cuegrain) retains its separately adopted Apache-2.0 license.
Riff packages its LICENSE, NOTICE and dependency notices verbatim with the consumer APK.

Third-party, generated, and imported components retain their own copyright notices and license
terms. The repository MUST identify and preserve those notices and complete license texts in source
and binary distributions; this requirement does not relicense them. A new dependency or imported
source component MUST be compatible with GPL-3.0-or-later, with its license and required notices
recorded before distribution.

## Public release source

The authorized public-source edition is signed FINAL 261018, version `1.0.2`, from the committed
main release tree. The website, public APK, source archive and read-only Git export MUST describe
that same public edition. A separately user-authorized local signed edition may be newer;
installation does not update the website or its source snapshot. A website update replaces the
published source with a current clean cut: one new root commit, without the original Git history. It
does not authorize changing history in the canonical development repository. Preserve the
application, its current source dependencies, schemas, checked-in assets, reproducible generators,
tests and required source/binary license notices. Historical pipeline implementations, unrelated QR
sources, player research, duplicate unused vendor trees and external hardware experiments are
excluded from this release checkout. Required adopted artwork and model provenance remain unchanged.
Source archives contain the release tree without signing material, local evidence, build outputs or
Git history.

## Signed distribution

The authorized Riff distribution is a signed FINAL APK built from the app release variant with
package name `link.roadio.riff`. The current authorized public and device edition is version
`1.0.2`, versionCode `16`. Debug and instrumented variants, their package suffixes, instrumentation
runner, test APKs, test fixtures and debug-only tooling MUST NOT be shipped. The source-owned
releaseChannel in config/project.json selects ALPHA, BETA, RC or FINAL for signed distributions;
releaseDate owns the edition date. Gradle and the signing helper consume the same values. The
user-authorized 1.0.2 release retains FINAL. Its stable application identity does not change the
signing key or the separately versioned public-beta model, and does not establish a blanket
data-format compatibility guarantee.

Riff owns one persistent self-managed application-signing key for direct APK distribution. The
private key and passwords remain outside Git, APKs, status evidence and Wiki; access is owner-only
and an encrypted independent backup is required. Only the public certificate and its SHA-256
fingerprint may be recorded or distributed. Every update for the same application ID MUST use that
certificate. Signing uses the aligned release APK and is verified before publication.

The release APK includes the GPL, retained third-party notices and the complete current Level 2
public-beta model package. The model package is content-derived and validated with its manifest,
calibrators, reference vector, validation report, notices and exact XXH3-64 runtime inventory; the
external source-package SHA-256 provenance is checked during release assembly. Distributed builds
MUST NOT use or present synthetic A/V as AI analysis. Essentia runtime code, binaries and models
remain absent. The beta model retains its declared research-derived supervision provenance and
beta-quality limitations; its inclusion does not claim stable-model quality or future compatibility.

Every APK declares one immutable compile-time `ReleaseIdentity`: channel `DEBUG`, `TEST`, `ALPHA`,
`BETA`, `RC`, or `FINAL`, plus an explicit ISO-8601 release date. The date is declared in source for
the release edition and is never derived from installation time, first use, runtime wall-clock time,
or build-machine time. Rebuilds of the same edition retain it. The authorized signed channel is
`FINAL`; later signed builds retain that policy until the user explicitly changes it. Debug and
disposable-test variants keep their own identities.

Every channel except `FINAL` is a non-final release. MAIN_SCREEN alone always paints its passive
diagonal label as `<Channel>: YYMMDD`, using title-case `Debug`, `Test`, `Alpha`, `Beta`, or `RC`
and the declared date. A `FINAL` APK has no overlay. The overlay has no age limit and remains
visible after any release-age acknowledgement.

At the start of the device-local calendar date equal to `releaseDate.plusMonths(3)`, each newly
launched Player process for `DEBUG`, `TEST`, `ALPHA`, or `BETA` presents exactly one non-dismissible
modal dialog over MAIN_SCREEN. Its title is `Old <Channel> build`, its message is
`This <Channel> build is more than three months old. It may be outdated.`, and its sole action is
`OK`. Before that boundary the dialog is absent. `RC` and `FINAL` never present it. The
acknowledgement is process-session state and is not persisted; after `OK`, every control immediately
resumes its ordinary storage, catalog, application-mode and motion-policy behavior for the rest of
that process. The modal blocks underlying pointer, key, rotary, accessibility and programmatic UI
activation while visible, but never stops, pauses, changes, or reconstructs an active Media3
playback session or its service. Device-local date is a product-lifetime input, not a security
clock, and no clock-rollback or acknowledgement state is persisted.

The local release-age acknowledgement uses the mandatory
[shared modal component](/Riff/LATEST/ui-modal-dialogs); its sole OK and non-dismissible policy
remain.

Before publication, verify release identity/date parsing, exact overlay and expiry boundary, shared
status default and explicit file override under
[project configuration](/Riff/LATEST/project-configuration#final-status-default),
signature/certificate continuity, model package completeness and hashes, absence of
debug/instrumentation payload, required GPL/notices, and proportionate build and artifact checks.

## Public website copy and references

Public-facing English uses American spelling and idiom. Do not publish a user's location or the
private context for a language preference. Public pages and user-facing external references must not
link to the internal Wiki hostname or assume its Internet availability. Public explanations remain
self-contained; use accessible public references or ordinary text instead. The public article
generator enforces this boundary. Copy-only website maintenance preserves the published APK and
matching immutable release-source artifacts.
