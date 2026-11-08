# Parameter audit — historical baseline

Performed on 8 November 2026, before the parameter-object refactoring completed that day. These counts describe the **old code**, not today's signatures. The live [guidelines](parameter-guidelines.md) and Checkstyle supersede the old per-line inventory.

## Original scope

The Java 25 compiler syntax tree was used without compiling/running the application. Interfaces and implementations were counted separately. Generated accessors and MapStruct/Lombok methods were excluded; inferred injection constructors were reported separately.

| Original inventory | Count |
| --- | ---: |
| Production method declarations | 149 |
| Methods with 0–2 parameters | 108 |
| Methods with 3 parameters | 18 |
| Methods with 4+ parameters | 23 |
| Test method declarations | 27 |
| Test helpers with 4+ parameters | 1 |
| Explicit production constructors | 17 |
| Inferred Lombok injection constructors | 14 |
| Production records, including a nested record | 15 |

## Decisions that followed

- Replace the aggregate's six-argument change with ChangeSessionPurchase plus SessionExpirationPolicy.
- Preserve PaymentSessionIdentity, PurchaseRevision and CommandMetadata.
- Group idempotency scope/request and original result without splitting the transaction.
- Keep SDK types and typed documents instead of custom varargs document helpers.
- Give simulator operations and phase commands cohesive types.
- Keep explicit HTTP bindings and narrow, documented exceptions.
- Review record/dependency cohesion rather than wrapping every constructor.

The old line-by-line proposal contained local machine paths and signatures made stale by implementation. This portable summary preserves its baseline and rationale; current code and executable checks govern today's signatures.
