## ADDED Requirements

### Requirement: Complete source-backed library
The app SHALL expose all 100 NAT and 100 BIB recipes, alongside compatible existing templates, with searchable names/materials/source IDs and natural/synthetic filters.

#### Scenario: Browse provided recipes offline
- **WHEN** the user opens Template Resep from Formula
- **THEN** the app lists both supplied datasets with dynamic counts and preserves the original source IDs

### Requirement: Validated mass-based parsing
The parser MUST reject invalid IDs, nonfinite/nonpositive quantities, percentage totals other than 100, or inconsistent reference grams, without partially importing that file.

#### Scenario: Corrupt source file
- **WHEN** a recipe has inconsistent percentage or redundant gram values
- **THEN** the file is rejected with an explicit error and other valid sources remain available

### Requirement: Honest preview and provenance
The preview SHALL show source weights, batch grams, source links, experimental status and the absence of IFRA/skin-use clearance without inventing product verification or dilution.

#### Scenario: Preview concentrate
- **WHEN** the user selects a supplied recipe
- **THEN** it is labeled a 100 percent concentrate rather than a finished skin-ready perfume

### Requirement: Explicit ingredient resolution
The app MUST use real live catalog IDs and require explicit selection for missing or unverified materials; automatic matching shall preserve grade, type and brand identity.

#### Scenario: Unverified accord
- **WHEN** a BIB recipe includes a LUZI accord without verified SKU
- **THEN** the app requires selection of a real eligible LUZI product and labels the result an adaptation requiring acknowledgement

### Requirement: Atomic application
The app SHALL scale a complete recipe to a positive finite target mass, verify the total within 0.01 g and preserve the current draft on cancellation or validation failure.

#### Scenario: Apply an adapted recipe
- **WHEN** every slot is bound, the batch is valid and manual changes are acknowledged
- **THEN** the editor receives the full validated recipe and provenance notes without automatic saving

#### Scenario: Cancel incomplete recipe
- **WHEN** the user closes an incomplete preview or supplies an invalid binding
- **THEN** the active formula remains unchanged

### Requirement: Verifiable delivery
The change MUST include real-asset regression tests, isolated unit/lint/build gates and an evidence checklist distinguishing automated results from unavailable device acceptance.

#### Scenario: No connected Android device
- **WHEN** no emulator or device is connected
- **THEN** device UI acceptance remains explicitly unverified despite successful automated gates
