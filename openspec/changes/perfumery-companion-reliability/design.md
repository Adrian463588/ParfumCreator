# Design

Use the existing repository interfaces and use cases. Retrieval owns ranking; the chat use case owns local/web routing and citations; the inference engine consumes only relevant evidence. Shared pure evidence scoring avoids different thresholds in each consumer. Uploaded sources retain document/page provenance. Public pages are bounded, split and ranked before inclusion in the prompt; full page text may be cached as WEB_UNTRUSTED with TTL.

Internet fallback remains a persistent preference. Once enabled, insufficient local evidence triggers lookup silently without a permission dialog. Existing OFF values stay OFF. Errors remain visible as retrieval status, not invented answers. Source URLs remain visible/clickable.

Catalog planner constraints are enforced before scoring: natural excludes synthetic fragrance materials, requested brands exclude other brands, explicit ingredients are required, unavailable constraints yield clarification. Solvent is assigned last. The shared calculation engine validates totals and units; UI uses its computed values. Fragrance wheel primary selection uses aggregate axis grams and its dominant subfamily belongs to that axis. Empty/solvent-only formulas show no olfactory profile.

Ponytail/SOLID/DRY: fix shared boundaries already used by callers; no new persistence framework, agent runtime, retrieval service or speculative abstraction. BMAD/OpenSpec are development methodology/artifacts only.

References: https://docs.bmad-method.org/build/build-a-change/ ; https://github.com/Fission-AI/OpenSpec/blob/main/docs/concepts.md
