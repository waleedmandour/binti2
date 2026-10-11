# License analysis for Binti

> **Update (v2.3.1-beta):** The migration to Apache License 2.0 is
> **complete**. The `LICENSE` file now contains the Apache 2.0 text, the
> `NOTICE` file preserves MIT-licensed attributions, and source files carry
> `SPDX-License-Identifier: Apache-2.0` headers. The analysis below is
> retained for historical context and for anyone considering future license
> changes.

## Current state

Binti is licensed under the **Apache License 2.0** (see [`LICENSE`](LICENSE)).
The migration from MIT was completed in v2.3.1-beta. This document analyzes
why Apache 2.0 was chosen and retains the comparison for future reference.

## Project characteristics that matter for licensing

1. **Reverse-engineering of proprietary vehicle services** — The Tier 2
   Advanced Telemetry module reflects against BYD's internal
   `android.hardware.bydauto.*` Android framework services. This touches a
   legal grey area in many jurisdictions (interoperability reverse
   engineering is permitted in some places, restricted in others).

2. **Distribution on Huawei AppGallery** — a commercial app store with its
   own review guidelines. The license must permit commercial distribution.

3. **Distribution via GitHub Releases (sideload)** — the enthusiast flavor
   is distributed as a sideloadable APK, not through any app store.

4. **Depends on MIT-licensed reference research** — `wheregoes/byd-apps`
   (MIT) is used as reference for the Tier 2 probe. MIT code can be included
   in projects under any license, but the attribution must be preserved.

5. **No contributor agreement** — there is no CLA, so every contributor
   retains copyright on their own contributions.

6. **Vehicle safety implications** — the app explicitly avoids
   safety-critical systems (steering, braking, ADAS), but a licensee who
   forks the project might not respect that boundary. A license with
   stronger terms could require derivative works to maintain the same
   safety scope.

7. **Patent risk** — vehicle technology is patent-heavy. BYD or other
   automakers may hold patents that the project's code inadvertently
   practices. A license without an explicit patent grant leaves
   contributors and users exposed to patent litigation.

## License comparison

| License | Permissive? | Patent grant? | Copyleft? | Attribution required? | Compatible with MIT deps? |
|---------|:-----------:|:-------------:|:---------:|:---------------------:|:-------------------------:|
| **MIT** (current) | ✅ Yes | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **Apache 2.0** | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes (NOTICE file) | ✅ Yes |
| **BSD-3-Clause** | ✅ Yes | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **MPL 2.0** | ⚠️ Partial | ✅ Yes | ⚠️ File-level | ✅ Yes | ✅ Yes |
| **GPL v3** | ❌ No | ✅ Yes | ✅ Strong | ✅ Yes | ✅ Yes |
| **AGPL v3** | ❌ No | ✅ Yes | ✅ Strong + network | ✅ Yes | ✅ Yes |
| **BUSL-1.1** | ❌ No | ❌ No | ❌ No | ✅ Yes | ⚠️ No (non-commercial) |

## Recommendation: Apache License 2.0

**Apache 2.0 is the most suitable license for Binti.** Here's why:

### 1. Explicit patent grant (the biggest advantage over MIT)

Apache 2.0 includes an explicit grant of patent rights from contributors to
users. Given that Binti operates in the vehicle technology space — where
companies like BYD, Tesla, and others hold large patent portfolios — this
matters. If a contributor's code inadvertently practices a patent held by
their employer, Apache 2.0 ensures that employer can't later sue Binti's
users for patent infringement.

MIT has **no patent grant**. This is a known weakness that the open-source
community has been moving away from. Most modern permissive projects
(AOSP, Kubernetes, TensorFlow, Kotlin) use Apache 2.0 for exactly this
reason.

### 2. Standard for the Android ecosystem

The Android Open Source Project itself is licensed under Apache 2.0. Most
Android libraries (Jetpack, Material Components, Kotlin coroutines) are
Apache 2.0. Using the same license as the surrounding ecosystem:

- Reduces friction for contributors (they already understand the terms).
- Signals that the project follows Android ecosystem norms.
- Makes the project attractive to corporate contributors who have
  Apache-2.0-approved contribution processes but might need legal review
  for MIT projects.

### 3. NOTICE file requirement (protects attributions)

Apache 2.0 requires preserving a `NOTICE` file in derivative works. Binti
already has a `NOTICE` file that attributes the MIT-licensed
`wheregoes/byd-apps` reference research. Under Apache 2.0, this attribution
is **legally required** for anyone who forks the project. Under MIT, the
attribution requirement is weaker (just "the above copyright notice and
this permission notice shall be included").

This matters for Binti because the Tier 2 module's existence depends on
the community reverse-engineering research published by `wheregoes/byd-apps`.
Preserving that attribution protects the research community's contribution.

### 4. Permissive enough for AppGallery distribution

Apache 2.0 permits commercial use, modification, and distribution — including
distribution on commercial app stores like Huawei AppGallery. There is no
copyleft requirement that would force derivative works to open-source
themselves (unlike GPL).

### 5. Compatible with MIT dependencies

Apache 2.0 is compatible with MIT. The `wheregoes/byd-apps` MIT-licensed
reference can be used and attributed within an Apache 2.0 project without
conflict.

### 6. Trademark notice aligns with the project's stance

Binti already states clearly that it is "not affiliated with or endorsed
by BYD Auto Co., Ltd." Apache 2.0 explicitly excludes trademark rights
from the license grant (Section 6), which reinforces this position: the
license grants copyright and patent rights, but **not** the right to use
the "BYD" or "DiLink" trademarks.

## Why not other licenses?

- **GPL v3**: The copyleft requirement would prevent distribution on Huawei
  AppGallery unless the entire app (including any future proprietary
  dependencies) were open-sourced. This is too restrictive for a project
  that wants to ship on commercial app stores.

- **AGPL v3**: Same problem as GPL, plus the network-use clause is
  irrelevant for a locally-installed Android app.

- **MPL 2.0**: File-level copyleft is a reasonable middle ground, but it's
  less familiar in the Android ecosystem than Apache 2.0, and the
  file-level copyleft could discourage some commercial contributors.

- **BUSL-1.1** (what `angoikon/byd-trip-stats` uses): The Business Source
  License is non-commercial for 4 years, then converts to an open-source
  license. This would prevent AppGallery distribution and is unnecessary
  for a project that wants maximum adoption.

- **BSD-3-Clause**: Very similar to MIT. No patent grant. No advantage over
  Apache 2.0.

- **Stay with MIT**: MIT is acceptable and the project works fine under it.
  Apache 2.0 is strictly better for this project's characteristics, but
  switching has a small administrative cost (every contributor would need
  to agree, or you accept that pre-change code stays MIT).

## How to switch to Apache 2.0 (if you decide to)

If you decide to switch, the steps are:

1. Replace the `LICENSE` file with the Apache 2.0 text
   (https://www.apache.org/licenses/LICENSE-2.0.txt).

2. Update the copyright line in `LICENSE` to:
   ```
   Copyright 2025 Dr. Waleed Mandour
   ```

3. Update `NOTICE` to reflect the Apache 2.0 attribution format. The
   existing NOTICE file is already close to what Apache 2.0 requires —
   just add a header noting the project's own license.

4. Update `README.md` license badge from MIT to Apache 2.0.

5. Add a header comment to each source file (optional but recommended for
   Apache 2.0 projects):
   ```
   // Copyright 2025 Dr. Waleed Mandour
   // SPDX-License-Identifier: Apache-2.0
   ```

6. If there are external contributors (people who have submitted PRs that
   were merged), you need their permission to relicense their
   contributions. For a project with a single author (Dr. Mandour so
   far), this is a non-issue.

7. Add `SPDX-License-Identifier: Apache-2.0` to the top of each file for
   automated license-scanning tools. This is a one-line addition per file
   and is the modern standard.

## Summary

| Criterion | MIT (pre-v2.3.1) | Apache 2.0 (current) |
|-----------|:---:|:---:|
| Patent protection | ❌ | ✅ |
| Android ecosystem norm | ⚠️ | ✅ |
| Attribution enforcement | ⚠️ | ✅ |
| AppGallery-compatible | ✅ | ✅ |
| MIT dep compatible | ✅ | ✅ |
| Trademark exclusion explicit | ❌ | ✅ |
| Effort to switch | — | ✅ Completed in v2.3.1-beta |

**Status: Apache License 2.0 — migration completed in v2.3.1-beta.** The
patent grant and stronger attribution protection are the primary benefits
for a project operating in the vehicle technology space.
