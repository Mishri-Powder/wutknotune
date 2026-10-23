# Player automatic selection

> Owner/scope: Player automatic selection and weighting

## Automatic selection

The production selector operates on distinct eligible `Media.id` items. Before feature ranges, PTE
groups, scores or sampling, Player removes AutomaticBook, effective Book, effective Ban, unavailable
and technically unplayable media, and every item without one complete Arousal/Valence
value-and-confidence pair in the current accepted MDRG. When the request selects one or two PTE
bands, Player also removes every item without one complete PTE value-and-confidence pair before
scoring and sampling. These exclusions cannot be relaxed to fill a queue. Physical copies never
create additional lottery tickets.

Each request freezes one coherent snapshot of active profile, eligible media and bindings, current
accepted MDRG, catalog memberships, own reactions, retained listening events, UI_CONFIG and the
requested `size`, PTE set, Arousal and Valence. It triggers no scan, extraction, inference, Indexer
work or live refill. Later state affects later requests only.

Arousal, Valence and PTE values are finite `[-1,+1]` with confidence `[0,1]`. For A/V, divide
absolute value-to-target distance by the eligible observed range floored by
`ai.selectionModel.axisMinimumSpan`, clamp to `[0,1]`, then multiply by confidence. Incomplete A/V
is an eligibility failure and is never assigned a neutral score. The fitted and padded AI canvas
viewport remains a presentation/input mapping and does not become the score scale.

PTE uses distinct eligible observations whose PTE confidence is greater than zero. Sort by PTE then
signed `Media.id`; for `n = 3q+r`, give the first `r` Slow/Medium/Fast groups `q+1` items and the
remainder `q`, and use the ordinary median of each group as its target. PTE is available only when
all three groups contain at least `ai.selectionModel.pteMinimumGroupSize` items, default `42`.
Otherwise its score contribution is zero and the three controls are disabled without deleting their
retained profile state. The selected set may contain zero, one or two bands. Zero means
unrestricted. For one or two, the coordinate is the confidence-scaled, clamped normalized distance
to the nearest selected median. Three selected bands are invalid UI and generator input.

The explicit own preference coordinates are `(trackLikeDeficit, trackDislikePenalty)`: LIKED
`(0,0)`, NEUTRAL `(1,0)`, DISLIKED `(1,1)`. Ban remains an independent hard exclusion. Album, Artist
and canonical visible Genre use the same Like-deficit and Dislike-penalty semantics. Multiple
directly applicable Artists or Genres use the mean liked and disliked fractions; the coordinates are
their square-root deficit and penalty so the squared-distance contribution stays bounded and
independent of membership count. Missing membership is neutral `(1,0)`.

Folder influence uses actual directory edges from each playable binding's containing directory. At
relative distance `d`, `alpha(d)=(1-q)q^d` with `q=ai.selectionModel.directoryDecay`; do not
renormalize for path length or observed reactions. Each path's Like deficit is
`1-sum(alpha for LIKED)` and Dislike penalty is `sum(alpha for DISLIKED)`, both bounded to `[0,1]`;
coordinates are their square roots. Average path values across a Media.id's distinct playable
bindings before taking the square root, so copies do not multiply probability. Clear adds nothing
and does not cancel ancestors.

Playback history uses only retained active-profile `PLAY_STARTED` events. At current history
generation `g`, `F=sum((1-q)q^(g-eventGeneration))` over starts, with
`q=ai.selectionModel.historyDecay`; repeated starts accumulate and the coordinate is `sqrt(F)`.
Queue generation, appearance among candidates, pause/resume, end events and opening or closing UI do
not create starts or generations. The retained window remains `player.recentHistoryGenerations`; no
unreconstructable older tail is inferred.

Let each configured coordinate be `x_i` and its relative weight be the same-named entry of
`ai.selectionModel.weights`. Compute `D2=sum(weight_i*x_i^2)` and `logWeight=-D2/temperature`. All
model values are finite; weights are non-negative and temperature positive. Sampling uses stable
complete-distribution log arithmetic and an unbiased random draw, never feature noise or top-N
truncation.

Generate the ordered queue through exact weighted draws without replacement. Remove the selected
Media.id, renormalize the remaining complete distribution and repeat until the requested `10`, `20`
or `50` items are selected or candidates end. A deterministic injected random source supports tests;
production randomness is not reset from request inputs. Insufficient libraries return the complete
shorter eligible set. The result is frozen and materialized through the registered Play/Add
boundary; no redraw, refill or mutation of an active queue occurs.
