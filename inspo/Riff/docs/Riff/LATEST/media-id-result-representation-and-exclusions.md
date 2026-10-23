# Riff MediaID representation and exclusions

> Owner/scope: Cuetree identity use in Riff

The current identity scheme is [Cuetree identity and access](../../Cuetree/identity-access.md). The
first admission of a file uses a short MediaID with SHRT. A high collision calculates low for both
the original and contender. The original retains SHRT with its full ID so pre-existing short
references in MDRG and Player state continue to resolve; the contender receives no alias. RESL
indicates pending resolution and clears after resolution. Durable references are never rewritten
solely because a collision occurred. Exact full duplicates can supply alternate physical sources;
different lows never share a short alias.

A MediaID has signed high and nullable signed low. The low's absence is distinct from numeric zero.
Current Cuetree and [MDRG](format-mdrg.md) schemas define their own representations. Riff uses
Cuetree's ID for Player state, analysis and playback lookup. Paths, URI, filename, directories,
source timestamps, tags and library generation are excluded from identity. FILF includes its
nullable span under the
[component-formation contract](media-id-scope-and-terminology.md#fragment-component-framing).
