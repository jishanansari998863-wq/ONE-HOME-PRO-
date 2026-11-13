# ONE HOME PRO — Relational Database Architecture (83 Tables) & PostgreSQL Migration Guide

## 1. Entity-Relationship Overview

`OneHomeProDatabase` implements **83 relational tables** across 7 domain modules using strict foreign keys (`ON DELETE RESTRICT` for financial/audit integrity, `CASCADE` only for owned child detail rows) and unique composite indexes (`businessId + code`, `agreementId + periodKey`, `idempotencyKey`, `tokenHash`).

```
businesses (1) ──< properties (1) ──< buildings (1) ──< floors (1) ──< rooms
     │                                                                   │
     ├──< customers ──< family_members                                   │
     │        │                                                          ▼
     │        └──< agreements >──────────────────────────────────────────┘
     │               │
     │               ├──< monthly_rents ──< payment_allocations >── payments ──< receipts
     │               ├──< security_deposits ──< deposit_transactions
     │               └──< cheques
     │
     ├──< chart_of_accounts ──< journal_lines >── journal_entries
     │        └──< bank_accounts ──< bank_transactions / bank_reconciliations
     │
     ├──< departments ──< teams ──< team_members >── employees ──< attendance / work_history
     ├──< users ──< user_roles / user_business_access / user_property_access / login_sessions / mfa_credentials
     │
     ├──< complaints ──< job_assignments / work_reports / material_usage
     └──< suppliers ──< purchases ──< purchase_items / goods_receipt_details ──> items ──< stock_movements
```

## 2. Engineering Corrections Implemented
1. **Integer Fils Monetary Arithmetic (`BIGINT` / `Long`)**: All monetary amounts (`amount_fils`, `rent_amount_fils`, `debit_fils`, `credit_fils`, `weighted_avg_cost_fils`) are stored in 1/100 QAR fils to eliminate IEEE-754 floating-point drift.
2. **Cheque Accounting Separation**: Received and deposited cheques post to asset account `1020 Cheques Receivable` and do **not** credit cleared Bank (`1010`) or rental revenue cash inflow until transitioned to `CLEARED`. `BOUNCED` cheques post a reversing journal back to `1100 Rent Receivable`.
3. **Security Deposit Liability Isolation**: Held deposits post to `2100 Security Deposits Held (Liability)`, never Rental Income (`4000`). Only forfeited deductions or refunds modify that liability.
4. **Idempotency Keys**: `payments`, `journal_entries`, `stock_movements`, and `material_usage` enforce unique `idempotency_key` constraints to prevent double-charging or duplicate stock deductions on retries.
5. **Non-Destructive Archiving**: Employees and financial vouchers are deactivated or reversed with contra-entries (`status = 'REVERSED'`), preserving full historical auditability.

## 3. PostgreSQL Production DDL Migration Equivalent

For multi-branch cloud deployment behind an API backend, apply the following PostgreSQL DDL conventions matching all 83 Room entities:

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    code TEXT NOT NULL UNIQUE,
    branch_name TEXT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'QAR',
    timezone TEXT NOT NULL DEFAULT 'Asia/Qatar',
    cr_number TEXT DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_epoch BIGINT NOT NULL,
    updated_at_epoch BIGINT NOT NULL
);

CREATE TABLE properties (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    property_type TEXT NOT NULL,
    address TEXT NOT NULL,
    city TEXT NOT NULL DEFAULT 'Doha',
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id UUID NOT NULL,
    created_at_epoch BIGINT NOT NULL,
    updated_at_epoch BIGINT NOT NULL,
    UNIQUE (business_id, code)
);

CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    normal_balance TEXT NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
    is_system_account BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_epoch BIGINT NOT NULL,
    UNIQUE (business_id, code)
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    property_id UUID REFERENCES properties(id) ON DELETE RESTRICT,
    entry_number TEXT NOT NULL,
    entry_date_epoch BIGINT NOT NULL,
    description TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT,
    idempotency_key TEXT NOT NULL UNIQUE,
    total_debit_fils BIGINT NOT NULL CHECK (total_debit_fils > 0 AND total_debit_fils = total_credit_fils),
    total_credit_fils BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'POSTED',
    reversed_entry_id UUID REFERENCES journal_entries(id),
    created_by_user_id UUID NOT NULL,
    created_at_epoch BIGINT NOT NULL,
    UNIQUE (business_id, entry_number)
);
```

All remaining 79 tables follow the exact column types, foreign keys, and unique indexes declared in `/app/src/main/java/com/example/data/db/entities/`.
