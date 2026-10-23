# How probabilistic music selection works

[Read on the website](https://roadio.link/tldr/probabilistic-selection/)

Riff assigns each eligible track a weight and builds a queue through weighted random draws.
Better-matching tracks are selected more often, while other eligible tracks remain possible. The
probability describes a track's selection chance, not a calibrated prediction of enjoyment.

## Which inputs are used?

The model uses Arousal (A), describing musical activation; Valence (V), describing emotional
character; and PTE, describing perceived musical speed rather than beats per minute. It also uses
user reactions to tracks, directories, albums, artists and genres, and retained playback-start
history.

The listener chooses an A/V target (A*, V*), optionally selects slow, medium or fast music, and
requests 10, 20 or 50 tracks. Indexer has already prepared the feature values and their confidence.
Player uses those results without running new audio analysis.

## Eligibility comes before probabilities

Before scoring, Player excludes automatically identified and user-classified audiobooks, effectively
banned items, unavailable or technically unplayable media, and tracks without complete A/V values
and confidence values. Selecting PTE bands also requires complete PTE information and confidence.

Each distinct `Media.ID` is one candidate. Physical copies of the same content do not increase its
selection chances. Eligibility is never relaxed to fill the queue: an insufficient collection
produces a shorter result.

## How is mood matching measured?

A, V and PTE values range from −1 to +1; confidence ranges from 0 to 1. Arousal's scale is derived
from the eligible collection:

$$s_A=\max(A_{\max}-A_{\min},s_{\min})$$

The default minimum span s_min is 0.10. It prevents division by zero and excessive amplification of
tiny differences. A track m has this mismatch coordinate:

$$x_A(m)=c_A(m)\min\left(1,\frac{|A(m)-A_*|}{s_A}\right)$$

Valence uses the equivalent formula with its own scale. Greater distance from the selected point
means greater mismatch. Distance is normalized against the collection and capped at one. The
displayed canvas viewport does not determine the scoring scale.

Confidence c_A weakens or strengthens the feature's influence. Low confidence means a smaller
penalty for an uncertain mismatch. The coordinate is squared later: confidence 0.5 produces one
quarter of the corresponding contribution at confidence 1. Missing values are not assigned zero; the
track is ineligible.

## How is perceived speed used?

Tracks with complete PTE information and positive confidence are sorted by PTE and divided into
three nearly equal groups: Slow, Medium and Fast. Each group's median becomes its target: the middle
value, or the mean of the two central values for an even-sized group.

The default minimum is 42 observations per group, requiring at least 126 suitable observations
overall. With insufficient data, PTE selection is unavailable and PTE contributes nothing. One or
two bands may be selected; selecting all three is invalid. The coordinate uses distance to the
nearest selected median:

$$x_{PTE}(m)=c_{PTE}(m)\min\left(1,\frac{\min_{b\in B}|PTE(m)-\mu_b|}{s_{PTE}}\right)$$

B is the selected band set and μ_b its medians. The PTE scale is the observed range of
positive-confidence PTE values, floored by the same minimum span. Selecting Slow favors nearby
tracks but does not categorically exclude fast ones. With no band selected, PTE has no scoring
effect.

## How do Like, Dislike and Ban work?

Each own track reaction has two coordinates: Like deficit and Dislike penalty.

| Reaction | Like deficit | Dislike penalty |
| -------- | -----------: | --------------: |
| Like     |            0 |               0 |
| Neutral  |            1 |               0 |
| Dislike  |            1 |               1 |

Like reduces the overall mismatch and Dislike increases it. Ban is a separate hard exclusion from
automatic selection.

Album, Artist and Genre reactions follow the same principle. Multiple directly applicable artists or
genres contribute through their liked and disliked fractions. For liked fraction L and disliked
fraction U, the coordinates are √(1−L) and √U. Squaring them later produces the fractions
themselves. One liked artist out of two therefore halves the corresponding deficit. Membership count
alone does not amplify influence. Missing membership supplies the neutral pair (1, 0).

Directory reactions follow actual directory edges. The containing directory has d=0, its parent d=1,
and so on:

$$\alpha(d)=(1-q_D)q_D^d$$

With default q_D=0.5, successive influences are 0.5, 0.25, 0.125 and so on. A path's Like deficit is
one minus the sum of liked-directory influences; its Dislike penalty is the sum of
disliked-directory influences. The coordinates are their square roots. Contributions are not
renormalized by path length. Neutral reactions do not cancel ancestor reactions. Values across
multiple playable copies are averaged before taking square roots.

## How does history reduce repetition?

Retained active-profile playback-start events contribute:

$$F(m)=\sum_{e\in Starts(m)}(1-q_H)q_H^{g-g_e}$$

Here g is the current history generation, g_e the event's generation, and q_H=0.5 by default. Age is
measured in history generations rather than elapsed hours. Recent starts contribute more; older
starts contribute less; repeated starts accumulate. The coordinate is √F(m), so its final
contribution is w_H·F(m), reducing the relative chance of repeat selection.

Candidate appearance, queue generation, screen navigation and pause/resume do not count as new
playback starts. Only retained history is used, with a default window of 128 generations; no unsaved
older history is inferred.

## How do scores become probabilities?

The coordinates combine into one score:

$$D^2(m)=\sum_i w_i x_i(m)^2$$

A smaller D² means a more favorable score. Weights express relative importance:

| Factor                                             | Default weight |
| -------------------------------------------------- | -------------: |
| A, V and PTE, each                                 |              1 |
| Track Like and Dislike, each                       |              1 |
| Directory, Album and Artist Like and Dislike, each |            0.5 |
| Genre Like and Dislike, each                       |           0.25 |
| History                                            |              1 |

The sampling weight W(m) and next-draw probability P(m) are:

$$W(m)=\exp(-D^2(m)/T)$$

$$P(m)=\frac{W(m)}{\sum_{j\in C}W(j)}$$

C contains the remaining candidates. Positive temperature T, default 1, controls selectivity. Lower
temperature emphasizes lower scores; higher temperature moves the distribution toward uniform
selection.

For scores 0.2, 1.2 and 2.2 at T=1, first-draw probabilities are approximately 66.5%, 24.5% and 9%.
The third candidate remains possible. Probabilities are relative to the current candidate set. An
equal score contribution for every candidate cancels during normalization and does not affect the
distribution.

The implementation uses stable log-weight arithmetic, subtracting the common maximum log weight
before exponentiation. This leaves probabilities unchanged. It uses the complete eligible
distribution, without preliminary top-N truncation or random perturbations of feature values.

## How is the final queue constructed?

After the first weighted draw, the selected `Media.ID` is removed. Probabilities for the remaining
candidates are normalized again and the next track is drawn. This continues until the requested size
is reached or candidates run out.

This is weighted sampling without replacement: a `Media.ID` cannot occur twice in the result. If the
request exceeds the eligible collection, all eligible candidates are included; weights then mainly
determine their order.

Each request freezes one coherent snapshot of the library, profile, reactions, history and
configuration. The completed queue retains its selected order. Later scans or reactions affect later
requests without silently rebuilding or refilling the selected queue.

The [automatic-selection contract](https://wiki.271.pics/Riff/LATEST/player-automatic-selection)
defines these rules. Defaults belong to the
[configuration contract](https://wiki.271.pics/Riff/LATEST/project-configuration); Settings has no
separate editor for these weights or temperature.
