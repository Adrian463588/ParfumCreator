# Design

## Data and parsing

Retain byte-identical source assets. One pure Gson parser supports legacy arrays and the supplied metadata/recipes objects. Validate IDs, types, positive finite weights, percentage sum, reference mass and redundant gram values. Reject a malformed file as a whole. Namespace imported numeric IDs (1000 + NAT number, 2000 + BIB number); retain original NAT/BIB IDs and source filename. Preserve composite family text rather than pretending it is an exact catalog family.

The existing cached loader reads all three files on IO and exposes file errors, without discarding valid files. Both UI and RAG consume the same templates. Source citations use each actual source file. Add only identity-preserving EO and Macbrame spelling aliases. Explicitly unverified accord slots cannot auto-resolve.

## Application contract

A pure use case resolves initial catalog bindings, filters eligible replacements by ingredient type/brand/carrier role, and validates an entire template application. Manual bindings use live catalog IDs, not ingredient objects supplied by the caller. Scale grams proportionally to a finite positive target batch; validate resulting mass/concentration with the shared calculation engine. Keep mass precision until display. Duplicate selected IDs are merged without changing total mass. Manual changes are recorded as adaptations in formula notes, including source-to-selected mapping and source links.

## UI and state

Reuse the Formula template menu, bento surfaces, search/category filters and existing catalog picker. List cards are compact; selection opens a scrollable detail preview with gram batch field, all ingredient percentages/grams, binding state and source links. An explicit acknowledgement is required for manual adaptations. Apply stays unavailable until all bindings and batch are valid. Successful apply replaces the editable draft, never auto-saves it. Dismiss/cancel preserves the previous draft. Parsing runs off Main. Empty/loading/error states remain visible.

No new tab, parallel editor, persistence schema or speculative repository abstraction is needed. Internet and inference behavior do not change.
