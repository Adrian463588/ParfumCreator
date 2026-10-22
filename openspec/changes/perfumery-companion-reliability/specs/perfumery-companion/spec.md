## ADDED Requirements

### Requirement: Evidence first answers and automatic web fallback
The companion SHALL answer from passages relevant to the actual question, preserving document and page citations. When local evidence is insufficient and fallback is enabled it SHALL automatically fetch allowlisted public sources, rank passages, answer with URL/time provenance, and cache untrusted content. It SHALL NOT ask a per-turn confirmation or send document/history contents. Explicit OFF SHALL prevent all web calls and cached web use.

#### Scenario: Uploaded answer offline
- **GIVEN** an indexed upload with a relevant answer and no available LLM
- **WHEN** the user asks about that information with internet off
- **THEN** the answer includes the relevant extract and a local document citation; no web call occurs.

#### Scenario: Related topic but missing answer
- **GIVEN** catalog and knowledge rows share generic perfume words but do not cover the requested subject
- **WHEN** fallback is enabled
- **THEN** automatic search occurs and a fetched relevant passage is cited; unrelated local rows cannot suppress it.

#### Scenario: Late passage and provider failure
- **GIVEN** a web page whose relevant passage is beyond its introduction
- **WHEN** lookup succeeds
- **THEN** the answer context includes that passage instead of only the introduction.
- **WHEN** the provider fails or no passage is relevant
- **THEN** report unavailable/insufficient evidence and never manufacture an answer; cancellation propagates.

### Requirement: Catalog constrained formulation
The companion SHALL generate new and template-based formulas from existing catalog IDs. Natural, synthetic/bibit, brand, explicitly named materials, batch and concentration constraints SHALL be honored. Missing requested materials SHALL cause a clear explanation or clarification, not arbitrary substitution. Final gram sum SHALL equal target within 0.01 g. Web sources SHALL NOT add catalog materials or validate IFRA limits.

#### Scenario: Natural and fragrance oil formulas
- **WHEN** natural citrus or a specific-brand bibit blend is requested
- **THEN** every fragrance material satisfies the request, has a positive catalog ID, and calculated mass/concentration match the request.

#### Scenario: Invalid input
- **WHEN** batch/concentration/unit/density/material constraints are invalid or impossible
- **THEN** return actionable validation, with no importable invalid recipe.

#### Scenario: Available starter recipes and unsupported legacy templates
- **GIVEN** legacy templates with missing catalog materials
- **WHEN** a template is requested
- **THEN** missing materials are reported without substitution, while explicitly labeled application-calculated starter concentrates 31/32 resolve completely to existing natural/Luzi catalog materials and retain their untested design provenance.

### Requirement: Correct calculator and wheel
The calculator SHALL validate numeric inputs, convert g/mg/mL with finite positive mL density, and exclude solvents from concentration/notes/wheel. Material identity SHALL distinguish aromatic alcohols from carriers. Primary wheel SHALL use aggregate axis weight. No fragrance SHALL mean no profile. SPL SHALL be labeled as an unvalidated heuristic.

#### Scenario: Aggregate wheel wins
- **GIVEN** Citrus 30 g + Aquatic 30 g + Floral 40 g
- **WHEN** wheel is computed
- **THEN** Fresh is primary at 60%, Floral secondary at 40%.

#### Scenario: Dilution and empty carrier
- **GIVEN** 20 g perfume materials and 80 g solvent
- **THEN** concentration is 20% w/w and notes/wheel exclude solvent.
- **GIVEN** only solvent
- **THEN** concentration, notes and wheel weights are zero and no fragrance direction is claimed.

### Requirement: Reviewable verified delivery
The change SHALL have a versioned README, app version, GitHub description/topics, unit-test and lint results, an acceptance checklist distinguishing automated/device evidence, and a verified commit/push to the user-authorized repository. Generated files, downloaded LLMs, credentials and browser profiles SHALL be excluded.

#### Scenario: Deliver verified source
- **GIVEN** the user-authorized private GitHub repository and completed automated checks
- **WHEN** the reviewed source is committed and pushed
- **THEN** remote and local commit hashes match, README/version/description/topics are populated, and device-only acceptance remains explicit when no device/model is available.
