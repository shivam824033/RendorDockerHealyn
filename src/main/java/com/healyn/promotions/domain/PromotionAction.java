package com.healyn.promotions.domain;

/// The closed set of call-to-action behaviours a promotion's button can trigger. All
/// are in-app (no external/marketing URLs in Phase 1, CLAUDE.md §13):
/// <ul>
///   <li>{@link #NONE} — informational only; the card still opens its details screen.</li>
///   <li>{@link #BOOK_APPOINTMENT} — deep-links into the request-first booking flow.</li>
///   <li>{@link #CALL_CLINIC} — dials the clinic contact number from the physio profile.</li>
/// </ul>
public enum PromotionAction {
    NONE,
    BOOK_APPOINTMENT,
    CALL_CLINIC
}
