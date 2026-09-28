# Design diagrams

Sequence diagrams for each operation. `DB` is the SQL database; `Storage` is the file storage for the active profile.
Not-found and conflict cases are thrown by the service and turned into 404 / 409 by `GlobalExceptionHandler`.
Any write to a transaction can lose a race with another write; the `@Version` check turns that into 409 `VERSION_CONFLICT`.

## Upload receipt (`POST /receipts`)

The file is stored before the DB transaction, so a slow upload never holds a DB connection.

```mermaid
sequenceDiagram
    actor Client
    participant RC as ReceiptController
    participant RS as ReceiptService
    participant FS as FileStorage
    participant DB

    Client->>RC: POST /receipts (file)
    RC->>RS: upload(file)
    RS->>RS: validate type and size
    alt invalid
        RS-->>RC: error
        RC-->>Client: 400 / 413 / 415
    else valid
        RS->>FS: store(key, file)
        FS-->>RS: stored
        RS->>DB: begin, insert receipt, commit
        alt insert fails
            RS->>FS: delete(key)
            RS-->>RC: error
            RC-->>Client: 500
        else saved
            RS-->>RC: receipt
            RC-->>Client: 201 receipt
        end
    end
```

## Process receipt (`POST /receipts/{id}/process`)

OCR runs before the DB transaction. The receipt row is locked so two calls on the same receipt run one after the other.
Re-processing replaces all taxes and items, including items the user edited.

```mermaid
sequenceDiagram
    actor Client
    participant RC as ReceiptController
    participant RS as ReceiptService
    participant FS as FileStorage
    participant OCR as OcrService
    participant TS as TransactionService
    participant P as ReceiptParser
    participant R as Reconciler
    participant DB

    Client->>RC: POST /receipts/{id}/process
    RC->>RS: process(id)
    RS->>DB: find receipt
    alt not found
        RS-->>RC: not found
        RC-->>Client: 404
    else found
        RS->>FS: load(storage_key)
        alt file missing
            RS-->>RC: file missing
            RC-->>Client: 409 RECEIPT_FILE_MISSING
        else file loaded
            RS->>OCR: extractText(receipt, file)
            OCR-->>RS: text (or "" if no fixture)
            RS->>DB: begin, lock receipt row (for update)
            RS->>DB: save raw_ocr_text, processed_at
            RS->>TS: createOrUpdate(receiptId, text)
            TS->>P: parse(text)
            P-->>TS: header, taxes, items
            TS->>R: check(total, taxes, items)
            R-->>TS: status, note
            Note over TS,R: no total: FAILED (still 200)
            TS->>DB: upsert transaction, replace taxes and items (version 0 if new, + 1 if existing)
            TS-->>RS: transaction
            RS->>DB: commit
            alt another request changed the transaction first (@Version check)
                RS-->>RC: conflict
                RC-->>Client: 409 VERSION_CONFLICT
            else saved
                RS-->>RC: transaction
                RC-->>Client: 200 transaction
            end
        end
    end
```

## Get transaction (`GET /transactions/{id}`)

```mermaid
sequenceDiagram
    actor Client
    participant TC as TransactionController
    participant TS as TransactionService
    participant DB

    Client->>TC: GET /transactions/{id}
    TC->>TS: get(id)
    TS->>DB: find transaction with taxes and items
    alt not found
        TS-->>TC: not found
        TC-->>Client: 404
    else found
        TS-->>TC: transaction
        TC-->>Client: 200 transaction
    end
```

## Re-itemize (`POST /transactions/{id}/itemize`)

Replaces all items, including items the user edited. Header and taxes are not changed.

```mermaid
sequenceDiagram
    actor Client
    participant TC as TransactionController
    participant TS as TransactionService
    participant P as ReceiptParser
    participant R as Reconciler
    participant DB

    Client->>TC: POST /transactions/{id}/itemize
    TC->>TS: reitemize(id)
    TS->>DB: begin, find transaction and its receipt's raw_ocr_text
    alt not found
        TS-->>TC: not found
        TC-->>Client: 404
    else found
        TS->>P: parse(raw_ocr_text)
        P-->>TS: items (header and taxes ignored)
        TS->>R: check(stored total, stored taxes, new items)
        R-->>TS: status, note
        TS->>DB: replace items (source AUTO), update status and note, version + 1, commit
        alt another request changed the transaction first (@Version check)
            TS-->>TC: conflict
            TC-->>Client: 409 VERSION_CONFLICT
        else saved
            TS-->>TC: transaction (same id)
            TC-->>Client: 200 transaction
        end
    end
```

## Replace items (`PATCH /transactions/{id}/items`)

Nothing is written unless the items reconcile. `version` is optional in the request; if sent, it must match.

```mermaid
sequenceDiagram
    actor Client
    participant TC as TransactionController
    participant TS as TransactionService
    participant R as Reconciler
    participant DB

    Client->>TC: PATCH /transactions/{id}/items (items, version?)
    TC->>TC: validate body (not empty, description max 255, amounts max 2 decimals, quantity max 3 decimals)
    alt invalid or empty list
        TC-->>Client: 400
    else valid
        TC->>TS: replaceItems(id, items, version)
        TS->>DB: begin, find transaction with taxes
        alt not found
            TS-->>TC: not found
            TC-->>Client: 404
        else version sent and does not match
            TS-->>TC: conflict
            TC-->>Client: 409 VERSION_CONFLICT
        else status is FAILED
            TS-->>TC: conflict
            TC-->>Client: 409 TRANSACTION_FAILED
        else found
            TS->>R: check(stored total, stored taxes, items)
            R-->>TS: result
            alt does not reconcile
                TS-->>TC: mismatch
                TC-->>Client: 409 ITEMS_DO_NOT_RECONCILE
            else reconciles
                TS->>DB: replace items (source USER), set COMPLETE, clear note, version + 1, commit
                alt another request saved first (@Version check)
                    TS-->>TC: conflict
                    TC-->>Client: 409 VERSION_CONFLICT
                else saved
                    TS-->>TC: transaction
                    TC-->>Client: 200 transaction
                end
            end
        end
    end
```

## Health (`GET /health`)

```mermaid
sequenceDiagram
    actor Client
    participant HC as HealthController
    participant DB

    Client->>HC: GET /health
    HC->>DB: SELECT 1
    alt database reachable
        HC-->>Client: 200 {"status":"UP"}
    else database down
        HC-->>Client: 503 {"status":"DOWN"}
    end
```
