# Phase 5 audit and implementation plan

Before edits: backend clean HEAD151ec999f0a5cb049a43dfd17cadde1b9cb4698d; frontend has no Git repository.
Read entity relationships, DTOs/errors, security URL/method rules, V1-V3, Phase4 verification/security helpers,
existing tests/PHASE4, frontend client/guards/layout/components/tests and Phase4.5 reports.
Preserve all existing endpoints/authentication and applied migrations. No credentials available to this agent.

Plan: additive V4 and feature package timetable, subject/room/term JPA masters; weekly series plus dated
cancellation/rescheduling and actual-date unavailable blocks. DRAFT and PUBLISHED reserve all dated occurrences.
A database-owned allocation ledger uses GiST exclusion constraints over built-in numeric point ranges and
local timestamp ranges: no privileged extensions, global booking lock or exists-then-save race. Adjacent
half-open slots allowed; triggers rebuild allocations on session/exception/block writes. All history FKs RESTRICT.
Terms bounded to366 days; recurrence dates campus-local Asia/Kolkata. Explicit per-faculty coverage windows
required for FREE. Draft conflicts or absent coverage produce UNKNOWN; FREE never proves physical presence.
Same-department subject/faculty/section/term on creation; no implicit cross-department teaching. Multi-faculty
subject teaching arises through multiple sessions. Student view matches linked section and current semester.
Private unavailable reasons visible only to owner/Admin. Shared finder excludes private profile/account data.

Extend existing frontend services/components/routes for master data, weekly grid/mobile list, own timetable,
coverage, exceptions and unavailability plus Faculty Finder. Preserve memory-only token transport.
Add unit/web/real PostgreSQL concurrency tests, secure owned-fixture verification and complete Hinglish docs.
Do not claim real PostgreSQL or live/browser integration until actually run with private local credentials.