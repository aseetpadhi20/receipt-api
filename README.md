# Receipt API

Upload a receipt, extract one transaction with its taxes and auto-itemized line items, and review or edit the items.

Java 25, Spring Boot 4, H2, Flyway. Design: [ARCHITECTURE.md](ARCHITECTURE.md), API: [openapi.yaml](openapi.yaml), flows: [DESIGN_DIAGRAMS.md](DESIGN_DIAGRAMS.md).

## Run

Needs Java 25. Maven is downloaded by the wrapper.

```bash
./mvnw spring-boot:run          # macOS / Linux
mvnw.cmd spring-boot:run        # Windows cmd
```

For Windows PowerShell see [PowerShell](#powershell).

The app starts on `http://localhost:8080` with the `local` profile: H2 database in `./data`, uploaded files in `./uploads`. No keys or environment variables are needed.

Port 8080 must be free. To use another port: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`.

To start with an empty database (ids from 1 again): stop the app, then delete the `data` and `uploads` folders.

Swagger UI: http://localhost:8080/swagger-ui/index.html

## OCR is stubbed

No OCR or LLM vendor is called.

- A `.txt` upload is used as the OCR text.
- An image or PDF is matched by file name to a fixture, e.g. `receipt-clean.png` uses `fixtures/task-a/receipt-clean.txt`.
- Any other file gives empty text, so the status is `FAILED`.

## Example curls

Run from the project folder in a bash shell (macOS, Linux, Git Bash). Ids come from the responses; the ones below assume a fresh database. For Windows PowerShell see [PowerShell](#powershell).

**Health**

```bash
curl http://localhost:8080/health
```

**1. Upload a receipt.** Returns `201` with `receipt_id`.

```bash
curl -F "file=@fixtures/task-a/receipt-clean.txt" http://localhost:8080/receipts
```

**2. Process it.** Returns `200` with the transaction, taxes, line items and `itemize_status`.

```bash
curl -X POST http://localhost:8080/receipts/1/process
```

**3. Get the transaction**

```bash
curl http://localhost:8080/transactions/1
```

**4. Re-run auto-itemize from the stored OCR text.** Same transaction id, line items replaced.

```bash
curl -X POST http://localhost:8080/transactions/1/itemize
```

**5. Replace line items (user edit).** Returns `200` if they add up, `409` if they don't.

```bash
curl -X PATCH http://localhost:8080/transactions/1/items \
  -H "Content-Type: application/json" \
  -d '{"items":[{"description":"Espresso","amount":3.50},{"description":"Sandwich","amount":8.90},{"description":"Mineral water","amount":2.60}]}'
```

### Mismatch fixture: 409, then fix

```bash
curl -F "file=@fixtures/task-a/receipt-mismatch.txt" http://localhost:8080/receipts   # receipt_id 2
curl -X POST http://localhost:8080/receipts/2/process                                  # NEEDS_REVIEW

# Items still don't add up (expected 16.60): 409 ITEMS_DO_NOT_RECONCILE, nothing saved
curl -X PATCH http://localhost:8080/transactions/2/items \
  -H "Content-Type: application/json" \
  -d '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00}]}'

# User adds the missing item: 200, COMPLETE
curl -X PATCH http://localhost:8080/transactions/2/items \
  -H "Content-Type: application/json" \
  -d '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00},{"description":"Minibar","amount":6.60}]}'
```

### Expected results on the fixtures

| Fixture | Total | Taxes | Line items | Status |
|---|---|---|---|---|
| receipt-clean | 17.85 | VAT 19% 2.85 | Espresso 3.50, Sandwich 8.90, Mineral water 2.60 | COMPLETE |
| receipt-tax-only | 24.00 | VAT 19% 3.83 (inclusive) | none | NEEDS_REVIEW |
| receipt-mismatch | 18.50 | VAT 19% 1.90 | Water 4.00, Snacks 6.00 | NEEDS_REVIEW |

Line items are never padded with a balancing line, and the grand total is never changed.

## Tests

```bash
./mvnw test
```

Includes a test that parses each fixture and compares every field with `fixtures/task-a/gold.json`.

## PowerShell

Same steps for Windows PowerShell (5.1 and 7). Use `curl.exe`, not `curl` (in Windows PowerShell 5.1 `curl` is an alias for `Invoke-WebRequest`). JSON bodies are piped to curl (`-d "@-"`), so no quote escaping is needed.

**Run and test**

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"   # another port
.\mvnw.cmd test
```

**Health, upload, process, get, re-itemize**

```powershell
curl.exe http://localhost:8080/health
curl.exe -F "file=@fixtures/task-a/receipt-clean.txt" http://localhost:8080/receipts
curl.exe -X POST http://localhost:8080/receipts/1/process
curl.exe http://localhost:8080/transactions/1
curl.exe -X POST http://localhost:8080/transactions/1/itemize
```

**Replace line items**

```powershell
$body = '{"items":[{"description":"Espresso","amount":3.50},{"description":"Sandwich","amount":8.90},{"description":"Mineral water","amount":2.60}]}'
$body | curl.exe -X PATCH http://localhost:8080/transactions/1/items -H "Content-Type: application/json" -d "@-"
```

**Mismatch fixture: 409, then fix**

```powershell
curl.exe -F "file=@fixtures/task-a/receipt-mismatch.txt" http://localhost:8080/receipts
curl.exe -X POST http://localhost:8080/receipts/2/process

$body = '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00}]}'
$body | curl.exe -X PATCH http://localhost:8080/transactions/2/items -H "Content-Type: application/json" -d "@-"

$body = '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00},{"description":"Minibar","amount":6.60}]}'
$body | curl.exe -X PATCH http://localhost:8080/transactions/2/items -H "Content-Type: application/json" -d "@-"
```

**Start with an empty database**

```powershell
Remove-Item data, uploads -Recurse -Force
```

## Profiles

| Profile | Database | File storage | Status |
|---|---|---|---|
| `local` (default) | H2 file | `./uploads` | Implemented |
| `aws` | PostgreSQL (RDS) | S3 | Not implemented |
| `azure` | PostgreSQL (Azure) | Blob Storage | Not implemented |

Only `local` runs today. The `aws` and `azure` profiles are planned: storage is behind a `FileStorage` interface and the database settings read environment variables (see [.env.example](.env.example)), but the S3 and Blob Storage implementations and the PostgreSQL driver are not added yet, so starting with those profiles fails. No secrets are committed.
