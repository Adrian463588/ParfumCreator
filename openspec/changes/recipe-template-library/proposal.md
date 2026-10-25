# Natural and fragrance-oil recipe template library

Date: 2026-10-23
Status: implementation authorized by user

## Why

The supplied JSON files contain 100 natural and 100 commercial-fragrance concentrate studies. The existing menu only reads 32 legacy/starter templates and contains hardcoded counts. Users need a searchable library and a safe path from each source recipe into the existing formula editor.

## What changes

- Bundle the two supplied files unchanged, preserve source IDs, percentages and provenance, and show natural/bibit categories in the Formula template menu.
- Preview ingredients, mass percentages, reference grams, source links and experimental/non-skin-ready warnings. Scale the concentrate batch without prescribing dilution.
- Resolve only unambiguous catalog identities. Require explicit catalog selection for missing grades and unverified accord slots. Record manual adaptations; never create fictitious supplier SKUs or silently substitute ingredients.
- Apply a complete, validated draft atomically through the existing calculator; cancellation/failure preserves the active draft. Keep existing templates and saved formulas compatible.

## BMAD workflow

Analysis: inspect source JSON, catalog identities and existing menu-to-editor flow. Planning: proposal and scenarios. Solution design: design.md. Implementation: bounded changes using existing Gson, Compose, domain calculator and catalog picker. Review/QA: real-asset parser tests, application validation tests, lint/build and device gates. Delivery: version, provenance, explicit commit and verified push.

## Scope

No new runtime dependency, database migration, web scrape, safety certification, SKU invention or automatic catalog expansion. Supplied statements about awards/catalog verification remain unverified source metadata, not application endorsements. Direct application means loading an editable recipe, not applying perfume to skin.
