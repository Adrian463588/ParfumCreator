# Perfumery companion reliability

Date: 2026-10-23
Status: implementation authorized by user

## Why

The app must help create natural perfumes and perfumes blended from real catalog fragrance oils. Weak local matches currently suppress internet lookup, retrieved page introductions can displace the relevant passage, and the wheel selects a primary axis from a single subfamily rather than its aggregate weight. Formula amounts and imports must remain tied to real catalog IDs.

## What changes

- Answer from relevant uploaded/local passages first; automatically use bounded, cited public sources when evidence is insufficient and internet fallback is enabled. No per-turn confirmation dialog. Preserve explicit offline preference and never send uploaded text/history.
- Reject unrelated evidence instead of returning a plausible-looking answer. Preserve errors and cancellation.
- Honor natural/synthetic, brand, material, batch and concentration constraints in catalog formulas.
- Correct aggregate fragrance wheel, solvent detection and calculator validation; explain heuristic SPL and mass-based concentration honestly.
- Validate through regression tests, Android lint, build and device checks where available. Deliver versioned source to the user-authorized private GitHub repository.

## BMAD workflow

Analysis: inspect actual source, datasets and governing docs. Planning: this proposal and scenario specs. Solution design: design.md. Implementation: tasks.md, bounded parallel ownership. Review and QA: regression tests and an evidence-backed checklist. Delivery: explicit staging, secret/generated-file review, conventional commit and remote SHA verification.

## Scope

Reuse existing Kotlin, Room, LiteRT-LM, Compose and network adapters. No cloud inference, unrestricted URL scraping, fabricated ingredients, or inferred safety clearance. Local model acceptance requires an installed verified model and a device; code tests alone cannot establish it.
