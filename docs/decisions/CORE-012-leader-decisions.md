# CORE-012 Leader decisions

## Stable assignment move date boundaries

- **Approval source:** Leader response in the CORE-012 Stable Assignment implementation conversation.
- **Decision:** Stable assignment date ranges are inclusive.
- When moving a horse, close its previous open assignment with `effective_to = new.effective_from - 1 day`.
- Create the new assignment with the requested `effective_from` and `effective_to = NULL`.
- Preserve the closed assignment as history.
- Reject overlapping assignment periods and invalid moves, including a new `effective_from` that would make the previous assignment's `effective_to` earlier than its `effective_from`.
- Validate and perform the close-and-create operation atomically within one database transaction.
