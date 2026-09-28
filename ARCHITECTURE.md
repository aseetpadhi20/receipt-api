# Architecture

## Storage choice

**Why SQL for the data:**
Receipts, transactions, taxes and line items are linked to each other and the amounts must add up exactly, so a SQL database with foreign keys and decimal columns fits well.

**Why file storage for the receipt file:**
The receipt file is just a file we save and read back, so we keep it in file storage and only save its key in the database.

**Problem with storing it as a BLOB:**
Files make the database big and slow to back up, and every upload and download puts extra load on the database.

**How file storage helps:**
File storage is cheaper and built for large files, and it keeps the database small and fast.

**SQL database (by profile):** H2 for local, PostgreSQL (RDS) for aws, PostgreSQL (Azure Database for PostgreSQL) for azure.

## Data model

**Schema:** `expenses`

| Parent | Child | Relationship | Linked by |
|---|---|---|---|
| receipt | expense_transaction | one to zero or one | expense_transaction.receipt_id |
| expense_transaction | transaction_tax | one to many | transaction_tax.transaction_id |
| expense_transaction | transaction_item | one to many | transaction_item.transaction_id |

### Table: `receipt`

The file itself is in file storage. This table keeps its details and the `storage_key` used to find it.

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | primary key, auto-generated |
| original_filename | VARCHAR(255) | not null |
| content_type | VARCHAR(100) | not null; image/jpeg, image/png, application/pdf, text/plain |
| size_bytes | BIGINT | not null; > 0 |
| storage_backend | VARCHAR(20) | not null; local, aws, azure |
| storage_key | VARCHAR(500) | not null; unique with storage_backend |
| raw_ocr_text | TEXT | null until processed |
| uploaded_at | TIMESTAMP WITH TIME ZONE | not null |
| processed_at | TIMESTAMP WITH TIME ZONE | null until processed; if set, raw_ocr_text must not be null |

### Table: `expense_transaction`

One transaction per receipt, holding the receipt header.

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | primary key, auto-generated |
| receipt_id | BIGINT | not null; foreign key to receipt.id, on delete cascade; unique |
| merchant | VARCHAR(255) | null if not readable |
| transaction_date | DATE | null if not readable |
| currency | CHAR(3) | ISO code, e.g. EUR; null if not readable |
| grand_total | DECIMAL(12,2) | >= 0; null only if itemize_status = FAILED |
| itemize_status | VARCHAR(20) | not null; COMPLETE, NEEDS_REVIEW, FAILED |
| itemize_note | VARCHAR(500) | reason for NEEDS_REVIEW / FAILED |
| version | BIGINT | not null; starts at 0; +1 on every change (optimistic locking) |
| created_at | TIMESTAMP WITH TIME ZONE | not null |
| updated_at | TIMESTAMP WITH TIME ZONE | not null |

### Table: `transaction_tax`

One row per tax line on the receipt. A transaction can have many.

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | primary key, auto-generated |
| transaction_id | BIGINT | not null; foreign key to expense_transaction.id, on delete cascade |
| name | VARCHAR(20) | not null; e.g. VAT, GST |
| rate | DECIMAL(7,6) | not null; 0 to 1, e.g. 0.19 for 19%, 0.08875 for 8.875% |
| amount | DECIMAL(12,2) | not null; >= 0 |
| inclusive | BOOLEAN | not null; true = tax already inside the total ("incl. VAT") |
| jurisdiction | VARCHAR(50) | optional, e.g. DE |

### Table: `transaction_item`

One row per line item on the receipt. A transaction can have many.

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | primary key, auto-generated |
| transaction_id | BIGINT | not null; foreign key to expense_transaction.id, on delete cascade |
| position | INT | not null; order on the receipt |
| description | VARCHAR(255) | not null |
| amount | DECIMAL(12,2) | not null; >= 0; as printed on the receipt |
| quantity | DECIMAL(10,3) | optional; > 0 |
| tax_amount | DECIMAL(12,2) | optional; >= 0 |
| source | VARCHAR(10) | not null; AUTO (from auto-itemize) or USER (edited via PATCH) |

## File storage

**What it stores:** only the uploaded receipt file (image, PDF or text), saved under a key like `receipts/{yyyy}/{MM}/{uuid}-{filename}`. The filename is cleaned first so it can't change the path. Everything else is in the database.

The code uses one `FileStorage` interface (`store`, `load`, `delete`). The Spring profile decides which implementation runs:

| Profile | Implementation | Where the file goes | Status |
|---|---|---|---|
| local (default) | LocalFileStorage | `./uploads` folder on disk | Implemented |
| aws | S3FileStorage | Amazon S3 bucket | Planned, not implemented |
| azure | AzureBlobStorage | Azure Blob Storage container | Planned, not implemented |

Connection details come from environment variables set per environment. No keys or secrets are committed to the repo.

## Reconciliation rule

```
expected items total = grand total - exclusive taxes
```

Item amounts are taken as printed on the receipt: net when tax is added on top, gross when tax is included. The tax lines' `inclusive` flag tells which one it is. We don't convert them, because that needs each item's tax rate and rounding would break the sum. Exclusive taxes are added on top of the items, so we subtract them. Inclusive taxes ("incl. VAT") are already in the prices, so we don't. `tax_amount` on an item is not added, otherwise tax would be counted twice.

| Order | Check | Status | Why |
|---|---|---|---|
| 1 | No grand total | FAILED | Nothing to compare against |
| 2 | No items | NEEDS_REVIEW | Nothing to check; also stops a 0.00 receipt from passing as COMPLETE |
| 3 | Items sum differs from expected by more than 0.01 | NEEDS_REVIEW | 0.01 allows for rounding on printed receipts |
| 4 | Otherwise | COMPLETE | |

We never add a balancing item or change the total or taxes. The receipt is what the user paid, so a mismatch needs a person to check it.

PATCH uses the same checks against the stored total and taxes. If the new items don't match, it returns 409 with expected, actual and difference, and saves nothing. On a FAILED transaction there is no total to check against, so PATCH also returns 409. If two writes clash (the `version` sent is old, or another request saved first), process, re-itemize and PATCH return 409 `VERSION_CONFLICT`.

Amounts with more than 2 decimals (or quantities with more than 3) are rejected with 400, never rounded, so what is checked is exactly what is saved.

Re-process and re-itemize replace all items, including items the user edited. This is the user asking to start again from the receipt.

## OCR

OCR is stubbed. A `.txt` upload is used as the OCR text, and an image or PDF is matched by file name to its fixture (`receipt-clean.png` uses `fixtures/task-a/receipt-clean.txt`). If no fixture matches, the OCR text is saved as empty, no total is found, and the status is FAILED.

The stub sits behind an `OcrService` interface, so a real OCR vendor can be added later as another implementation, with its key read from an environment variable.

## API contracts

See [openapi.yaml](openapi.yaml).

## Sequence diagrams

See [DESIGN_DIAGRAMS.md](DESIGN_DIAGRAMS.md).
