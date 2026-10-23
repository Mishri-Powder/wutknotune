# Riff documentation governance

> Owner/scope: documentation authority, current-state presentation and verification links.

## Authority

The [requirements registry](/Riff/LATEST/requirements) routes each scope to one normative owner.
Requirements are normative within that scope; comments, tests, status and evidence link to the
owner. For authorized behavior changes, update the owning contract before implementation. A
discrepancy with code is recorded and reconciled against the accepted requirement.

## Current editions

Active pages describe the adopted system in present tense. Current status contains delivery,
verification limits and actual pending work. A worklog is a concise current handoff. Outgoing
editions, completed reviews, rejected designs and chronological work records are archived verbatim
under LEGACY. The separate history index owns navigation to those records; active technical pages
link to active owners.

## Decision anchors

Use stable descriptive section anchors for architectural invariants. Comments explain a decision,
constraint or failure boundary and link to the owning section. One comment can serve programmers and
automated agents. `AI-INTENT:` marks informative maintenance rationale; documentation can carry it
in HTML comments. Such comments cite their owner and introduce no requirement. Keep procedural
instructions in contributor guides and wire rules in format contracts. Avoid duplicating normative
tables across pages.

## Verification

Each Wiki update explicitly preserves its published, non-private Wiki state, verifies exact API
read-back and fetches the internal route without authentication. A worklog update includes a
whole-Riff-tree publication check. Run the pinned Markdown formatter/linter and the
current-reference checker. Normative pages target at most 1,200 counted words; split oversized
scopes by responsibility. Archive bytes are immutable and excluded from formatting.

## Internal documentation boundary

Wiki.js is an internal service. A published, non-private Wiki page does not imply Internet exposure;
no external exposure is planned. Internal publication checks use the reachable internal service. Do
not give external users links to the Wiki hostname. The
[public website owner](/Riff/LATEST/distribution#public-website-copy-and-references) governs public
copy and accessible references. Personal context used to select language is not public content.
