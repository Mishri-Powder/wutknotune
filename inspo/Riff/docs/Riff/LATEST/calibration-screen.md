# Riff CALIBRATION_SCREEN contract — registry

> **Status:** Adopted and normative for CALIBRATION_SCREEN only\
> **Authority:** Registered by [Riff system requirements](requirements.md). Central requirements
> control system-wide density, AAOS exceptions, persistence, icon provenance, and conflicts.
> [SETTINGS_SCREEN](settings-screen.md) controls parent draft/commit behavior. This page controls
> CALIBRATION_SCREEN-specific presentation, references, interaction, calculation, validation,
> limitations, and verification.

CALIBRATION_SCREEN is the raw-pixel Android View/Canvas screen hosted by Compose that measures the
real pixel density of the panel by having the user match an on-screen outline to a physical
reference object.

This contract cannot broaden central policy. aaos reaches it only through an official permitted
parked Settings path with no recovery; automotive may reach it through the exact unavailable-source
Settings recovery but never after a positive restriction; phone reaches it normally. Raw precision
calibration remains an explicit automotive deviation and requires applicable AAOS validation before
a locked-AAOS release includes it.

---

This page is a bounded registry. The linked children own the detailed requirements.

## Sections

- [1. Why this screen is different from every other screen](calibration-screen-why-this-screen-is-different-from-every-other-screen.md)
- [4. Canvas geometry](calibration-screen-canvas-geometry.md)
- [5. Layout](calibration-screen-layout.md)
- [6. Interaction](calibration-screen-interaction.md)
- [10. Acceptance criteria](calibration-screen-acceptance-criteria.md)
