# Riff probabilistic selection explanation

Added a new topic to the public TL;DR section:
[English](https://roadio.link/tldr/probabilistic-selection/) and
[Russian](https://roadio.link/tldr/ru/probabilistic-selection/). Both language indexes link to it
and the homepage topic summary covers selection as well as IPC.

## Content

The articles explain eligibility before scoring, distinct Media.ID candidates, normalized A/V
distances and confidence, PTE medians and the 42-per-group gate, Like/Dislike/Ban, directory decay,
retained start-history decay, weighted squared coordinates, temperature, normalized exponential
probabilities and ordered sampling without replacement. The numerical example is independently
checked. Each language has eight sections, eight native MathML formulas and two tables.

The existing [selection owner](/Riff/LATEST/player-automatic-selection) and
[configuration owner](/Riff/LATEST/project-configuration) remain normative. The explanatory copies
are [English](/Riff/LATEST/tldr-probabilistic-selection-en) and
[Russian](/Riff/LATEST/tldr-probabilistic-selection-ru).

## Delivery

Published incrementally with a private rollback backup under
/home/yegor/nginx/backups/website-selection-tldr-261013. New CSS and homepage JavaScript use
content-named immutable assets. MathML is embedded directly, requires no external math scripts, and
retains TeX annotations. Both articles and language navigation are static.

Regenerated the sitemap and purged affected mutable URLs through the Cloudflare API. Origin and
public-CDN exact-content/cache checks cover the changed files; local links, formula structure,
contents anchors, bilingual navigation and 320-pixel page overflow checks pass. Browser review
covers actual formula rendering and the weight table.

Evidence, inputs, text sources, file inventories and publication receipts are under
status/website-selection-tldr-261013. This explanation changes no product behavior, Android build,
APK distribution, published source snapshot or canonical Git history.
