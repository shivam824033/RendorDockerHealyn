/// Compliance surface: versioned legal documents (Privacy Policy / Terms), demonstrable
/// consent capture (incl. family-member authority), and the account deletion / right-to-erasure
/// flow. A high-level module that orchestrates erasure across {@code auth} and {@code patients}
/// via their own erasure methods, and records family-authority consent through the
/// {@code patients}-owned {@code ConsentRecorderPort}. See docs/SECURITY_GUIDELINES.md
/// (Consent &amp; Data Lifecycle) and docs/SYSTEM_ARCHITECTURE.md §3.1.
package com.healyn.compliance;
