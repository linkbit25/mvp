# 🔍 LinkBit Full System Audit Report
**Date:** 2026-03-21 | **Auditor:** Antigravity AI | **Scope:** Full-stack (Backend + Frontend)

---

## 📊 System Readiness Score: **6.2 / 10**

> **Verdict:** The core loan lifecycle is architecturally sound, but has a cluster of critical frontend bugs that would break the live user flow. Backend logic is more robust, but has missing endpoint coverage and security gaps. Not production-ready as-is.

---

## ✅ Fully Working Features

### Backend
- ✅ **All 21 required API endpoints are implemented** (Auth, Marketplace, Negotiation, Agreement, Fee, Escrow/Collateral, Disbursement, Repayment, Closure, BTC price)
- ✅ **State machine is explicit and enforced** via [StateMachineService](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/StateMachineService.java#21-227) with a full transition map
- ✅ **Terminal states are protected** — `CLOSED`, `LIQUIDATED`, `CANCELLED` throw `IllegalStateException` on any further transition
- ✅ **Overpayment prevention** — `RepaymentService.submitRepayment()` throws if `amount > totalOutstanding`; also enforced in [processRepaymentFinancials()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#141-183)
- ✅ **Financial interest calculation** — Uses simple interest formula `P × R × D / 36500` correctly
- ✅ **Ledger consistency** — Entries created for both `FIAT_DISBURSEMENT` and `BORROWER_REPAYMENT` events
- ✅ **IDOR prevention for ledger** — [getLoanLedger()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#184-200) checks borrower OR lender participation
- ✅ **IDOR prevention for repayment** — Only borrower can submit repayments
- ✅ **JWT-based authentication** — Token injected via interceptor, 401 auto-clears token
- ✅ **RBAC** — `/admin/**` protected by `hasRole('ADMIN')` at both URL matcher and `@PreAuthorize` levels
- ✅ **Idempotency** — State machine has [isAlreadyInTargetState()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/StateMachineService.java#222-226) guard
- ✅ **Audit log** — Every state transition is persisted in `LoanAuditLog`
- ✅ **LTV invariant validation** — On margin call / liquidation transitions, BTC price and LTV are re-verified
- ✅ **BTC price API** — `GET /btc/price` and `GET /btc/price/history` implemented
- ✅ **Database indexes on Loan** — `status`, `borrower_id`, `lender_id`, `updated_at`

### Frontend
- ✅ **All required pages exist and are routed** — Login, Register, Dashboard, Marketplace, KYC, Negotiation, Agreement, Fee, Collateral, Disbursement, Repayment, Closure
- ✅ **JWT interceptor in api.ts** — Adds `Authorization: Bearer <token>` to all requests
- ✅ **401 auto-logout** — Response interceptor clears localStorage on 401
- ✅ **Loading states present** on most pages (spinner + message)
- ✅ **Overpayment UI guard** — RepaymentPage shows error if `amount > totalOutstanding`
- ✅ **Dashboard sorts at-risk loans first** (MARGIN_CALL, LIQUIDATION_ELIGIBLE at top)
- ✅ **DisbursementPage role-aware** — Lender sees payment details, borrower sees receipt confirmation
- ✅ **Optimistic polling** — Negotiation page polls loan at 5s, messages at 2.5s
- ✅ **Signature tracking** — Agreement page correctly detects per-role signing status

---

## ⚠️ Minor Issues

### Backend
- ⚠️ **[CollateralController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralController.java#11-21) is an empty stub** — The file exists with a comment saying "future collateral endpoints". This is confusing since [EscrowController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/EscrowController.java#16-63) handles collateral. Should either be removed or properly documented.
- ⚠️ **[AdminController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AdminController.java#11-22) is an empty stub** — Same issue as above. The only admin endpoint is behind [CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53), [AdminLiquidationController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AdminLiquidationController.java#11-25), and scattered `@PostMapping("/admin/...")` routes on other controllers.
- ⚠️ **No `AGREED` state used** — `LoanStatus` declares `AGREED` but the state machine never transitions to it. Dead enum value.
- ⚠️ **No `DEFAULTED` state used** — `LoanStatus` declares `DEFAULTED` but no transition leads to it. Another dead enum value.
- ⚠️ **`COLLATERAL_LOCKED` → `AGREED`** — The state `AGREED` is never in any transition. The spec shows `COLLATERAL_LOCKED → ACTIVE` directly via `DISBURSE_FIAT`, which is implemented, but the enum pollution is confusing.
- ⚠️ **`GET /loans/{id}/collateral` response is raw `Map<String, Object>`** — Not typed. Should return a proper DTO for consistency and Swagger docs.
- ⚠️ **[CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53) does a raw `loanRepository.getReferenceById()`** after `collateralReleaseService.getCollateralBalance()` — This performs two DB hits. If the escrow service already loads the loan, this is an N+1-style redundancy.
- ⚠️ **LedgerResponse missing timestamp** — [getLoanLedger()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#184-200) builds `LedgerResponse` with only `type` and `amount`. No `created_at` or `notes` field. Frontend sorts and displays ledger entries but can't show when they occurred.
- ⚠️ **`DISPUTE_OPEN` → `CLOSED`** via `RELEASE_COLLATERAL` bypasses outstanding check — Confirmed: the closure invariant check in [StateMachineService](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/StateMachineService.java#21-227) fires on `next == CLOSED`, so this is guarded. But the semantic risk (dispute closed with outstanding debt) warrants explicit documentation.
- ⚠️ **Admin override map is sparse** — `ADMIN_ALLOWED_TRANSITIONS` doesn't include `DISPUTE_OPEN` resolution to `ACTIVE` — admin can't manually force-resolve a disputed loan. This is likely a missing case.
- ⚠️ **BTC price is `permitAll()`** — Not a security issue per se, but means unauthenticated callers can probe BTC price endpoint. Acceptable for public market data but worth documenting.

### Frontend
- ⚠️ **Hardcoded `proofUrl`** — [DisbursementPage](file:///d:/LB/mvp/frontend/src/features/loans/DisbursementPage.tsx#25-409) initializes `proofUrl` to `'https://mock-proof-url.com/receipt.png'` and [RepaymentPage](file:///d:/LB/mvp/frontend/src/features/loans/RepaymentPage.tsx#27-371) to `'https://mock-proof-url.com/repayment.png'`. These are submitted as real proof URLs to the backend. Misleading to admin verifiers.
- ⚠️ **[LoanClosurePage](file:///d:/LB/mvp/frontend/src/features/loans/LoanClosurePage.tsx#19-178) — `interestPaid` computation is wrong** — `interestPaid = totalRepaymentAmount - principalAmount`. This shows the *scheduled* interest, not the actual interest paid (which would be `totalRepaymentAmount - totalOutstanding` at time of closure if partially paid).
- ⚠️ **Admin page is a hardcoded `<div>`** — The `/admin` route renders `<AdminDashboard>` which is just an inline div with placeholder text. No real admin dashboard UI exists.
- ⚠️ **"Download Settlement PDF" button is non-functional** — `onClick` is missing on the button in [LoanClosurePage](file:///d:/LB/mvp/frontend/src/features/loans/LoanClosurePage.tsx#19-178). This is dead UI.
- ⚠️ **Polling on [LoanNegotiationPage](file:///d:/LB/mvp/frontend/src/features/loans/LoanNegotiationPage.tsx#28-351) fires even in non-negotiating states** — The loan query `refetchInterval: 5000` always runs. Should pause when `status !== 'NEGOTIATING'`.
- ⚠️ **[DisbursementPage](file:///d:/LB/mvp/frontend/src/features/loans/DisbursementPage.tsx#25-409) lender detection uses wrong field** — `user?.id === loan?.lender?.id`. But `authStore` stores the user as `{ userId, email, role }`. The field is `userId`, not [id](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AdminLiquidationController.java#19-24). This will always evaluate `false`, meaning **all users will see the borrower view**, even lenders.
- ⚠️ **[CollateralDepositPage](file:///d:/LB/mvp/frontend/src/features/loans/CollateralDepositPage.tsx#22-344) — escrow balance displayed as `loan.escrowBalanceSats / 100_000_000`** — But the API likely returns BTC directly (not satoshis). This calculation may be wrong depending on backend DTO.

---

## ❌ Critical Issues

### 1. 🔴 URL Parameter Name Mismatch (Frontend — Breaks All Loan Pages)

**Affected pages:** [LoanNegotiationPage](file:///d:/LB/mvp/frontend/src/features/loans/LoanNegotiationPage.tsx#28-351), but also indirectly all pages

[router.tsx](file:///d:/LB/mvp/frontend/src/app/router.tsx) defines the route as:
```ts
path: 'loans/:id',
element: <LoanNegotiationPage />
```
But [LoanNegotiationPage.tsx](file:///d:/LB/mvp/frontend/src/features/loans/LoanNegotiationPage.tsx) destructures:
```ts
const { loanId } = useParams(); // ❌ should be `id`
```
The `useParams()` key must match the route param name (`:id`). All other pages correctly use `const { id: loanId } = useParams()`, but [LoanNegotiationPage](file:///d:/LB/mvp/frontend/src/features/loans/LoanNegotiationPage.tsx#28-351) uses `loanId` directly. This means **`loanId` is `undefined`** on this page, breaking ALL API calls: `GET /loans/undefined/details`, `GET /chat/messages?loanId=undefined`, etc.

**Fix:**
```ts
const { id: loanId } = useParams(); // ✅
```

---

### 2. 🔴 POST Endpoint Called via `useQuery` (Escrow Generation — Race Condition + Idempotency Violation)

In [CollateralDepositPage.tsx](file:///d:/LB/mvp/frontend/src/features/loans/CollateralDepositPage.tsx):
```ts
const { data: escrow } = useQuery({
  queryKey: ['escrow', loanId],
  queryFn: async () => {
    const res = await api.post(`/loans/${loanId}/escrow/generate`); // ❌ POST inside useQuery
    return res.data;
  },
  enabled: !!loanId && loan?.status === 'AWAITING_COLLATERAL',
});
```
`useQuery` is designed for GET (idempotent) requests. Using `POST` inside it means:
- The escrow generation fires on every cache invalidation / refocus / retry
- React Query may call this concurrently on multiple mounts
- If the backend doesn't properly deduplicate, multiple escrow addresses can be generated

**Fix:** Use `useMutation` + call it once on mount or on button click. The backend's `generateAddress()` should also be idempotent (return existing address if already generated).

---

### 3. 🔴 [LoanAgreementPage](file:///d:/LB/mvp/frontend/src/features/loans/LoanAgreementPage.tsx#20-256) Redirects to Wrong Path After Full Signing

After both parties sign:
```ts
useEffect(() => {
  if (isFullySigned) {
    setTimeout(() => navigate(`/loans/${loanId}`), 3000); // ❌ Redirects to /loans/:id (Negotiation page)
  }
}, [isFullySigned]);
```
After signing, the loan status transitions to `AWAITING_FEE`. The user should be redirected to `/loans/${loanId}/fee`, not back to the negotiation page.

---

### 4. 🔴 KYC Not Enforced Before Loan Participation

**Backend:** `AuthService.submitKyc()` exists and sets `KycStatus`. The [connectOffer()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/LoanMarketplaceController.java#50-55) in `LoanMarketplaceService` is the entry point for borrowers.

**Problem:** There is no KYC check in [connectOffer()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/LoanMarketplaceController.java#50-55) or anywhere in offer connection flow. A user with `KycStatus.PENDING` or `KycStatus.REJECTED` can connect to an offer and enter the loan lifecycle.

**Risk:** High. This is a compliance failure — KYC-unverified users participating in financial transactions.

---

### 5. 🔴 `REPAID` State Has No Guard for Collateral Release Timing

In [StateMachineService](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/StateMachineService.java#21-227):
```java
addTransition(LoanStatus.REPAID, LoanAction.RELEASE_COLLATERAL, LoanStatus.CLOSED);
```
And `CollateralReleaseService.releaseCollateral()` takes [(loanId, username)](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AuthController.java#30-34) — but the route is:
```java
@PostMapping("/admin/loans/{loan_id}/release-collateral")
```
This is admin-only, which is correct. However, there is **no automated trigger** — no background job or webhook fires when loan enters `REPAID` to notify admin. Admin must manually call this endpoint. The loan can sit in `REPAID` state indefinitely with BTC locked, causing collateral to be stuck.

**Risk:** Medium-High. BTC locked in escrow indefinitely without any SLA or automated release.

---

### 6. 🔴 Security: `EscrowController.submitDeposit()` Has No Authorization Check On Ownership

```java
@PostMapping("/loans/{loanId}/deposit")
public ResponseEntity<Void> submitDeposit(
        Authentication authentication,
        @PathVariable UUID loanId,
        @Valid @RequestBody DepositRequest request) {
    escrowService.deposit(authentication.getName(), loanId, request.getAmountBtc());
```

The route is authenticated (correct), but if `EscrowService.deposit()` doesn't verify that `authentication.getName()` is the borrower on this loan, any authenticated user can submit a deposit proof for any loan. This is an **IDOR vulnerability**. Same concern applies to `/loans/{loanId}/topup-collateral`.

---

### 7. 🔴 Security: [CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53) Admin Endpoint Not Protected by `@PreAuthorize`

```java
@PostMapping("/admin/loans/{loan_id}/release-collateral")
public ResponseEntity<Void> releaseCollateral(...) {
```

This is in [CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53), which has **no** `@PreAuthorize("hasRole('ADMIN')")` annotation and **no** `@RequestMapping("/admin")` class-level annotation. It relies **entirely** on the URL-based security rule:
```java
.requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
```
This is technically sufficient, but is brittle — if the path ever changes slightly (e.g., a typo or prefix change), the role check silently drops. Defense-in-depth requires `@PreAuthorize` at the method level, just as [AdminLiquidationController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AdminLiquidationController.java#11-25) does.

---

### 8. 🔴 [DisbursementPage](file:///d:/LB/mvp/frontend/src/features/loans/DisbursementPage.tsx#25-409) isLender Detection Broken

```ts
const isLender = user?.id === loan?.lender?.id; // ❌
```

`authStore` stores:
```ts
{ userId, email, role } // no `id` field
```
So `user?.id` is always `undefined`. The comparison is always `false`. **Every user is treated as a borrower**, meaning no user can ever see the payment details or mark disbursement. The lender flow is completely broken.

**Fix:**
```ts
const isLender = user?.userId === loan?.lender?.id; // ✅ — if lender.id matches UUID
// OR: rely on loan.role field (as LoanNegotiationPage does):
const isLender = loan?.role === 'LENDER'; // ✅ Recommended
```

---

## 🔧 Suggested Fixes (Priority Order)

| # | Priority | Issue | Fix |
|---|----------|-------|-----|
| 1 | 🔴 Critical | `useParams().loanId` → `undefined` in NegotiationPage | Change to `const { id: loanId } = useParams()` |
| 2 | 🔴 Critical | `isLender` bug → all users see borrower view | Use `loan?.role === 'LENDER'` |
| 3 | 🔴 Critical | POST inside `useQuery` for escrow | Refactor to `useMutation` called once on mount |
| 4 | 🔴 Critical | Agreement redirects to Negotiation page post-signing | Change redirect to `/loans/${loanId}/fee` |
| 5 | 🔴 Critical | KYC not enforced on [connectOffer()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/LoanMarketplaceController.java#50-55) | Add KYC gate in `LoanMarketplaceService.connectOffer()` |
| 6 | 🔴 Critical | Add `@PreAuthorize` to [CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53) | Add `@PreAuthorize("hasRole('ADMIN')")` to release endpoint |
| 7 | 🔴 Critical | IDOR possible on `/loans/{loanId}/deposit` | Verify ownership in `EscrowService.deposit()` |
| 8 | 🟡 High | Remove dead enum values `AGREED`, `DEFAULTED` from `LoanStatus` | Or add explicit transitions and UI handling |
| 9 | 🟡 High | Add `created_at` to `LedgerResponse` | Needed for meaningful ledger display |
| 10 | 🟡 High | Automated collateral release trigger | Background job when loan enters `REPAID` state |
| 11 | 🟡 Medium | Hardcoded proof URLs | Replace defaults with empty string, require real input |
| 12 | 🟡 Medium | Admin transition map missing `DISPUTE_OPEN` → `ACTIVE` | Add to `ADMIN_ALLOWED_TRANSITIONS` |
| 13 | 🟡 Medium | `interestPaid` calculation in ClosurePage | Use actual ledger sum, not scheduled amount |
| 14 | 🟢 Low | [CollateralController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralController.java#11-21) and [AdminController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/AdminController.java#11-22) stubs | Remove or document clearly |
| 15 | 🟢 Low | Admin UI page is a placeholder | Build real admin dashboard with verification actions |

---

## 🔷 Step 1: Backend API Audit

| Endpoint | Status | Notes |
|----------|--------|-------|
| `POST /auth/register` | ✅ | Implemented |
| `POST /auth/login` | ✅ | Implemented |
| `GET /auth/me` | ✅ | Implemented |
| `GET /offers` | ✅ | Public access, implemented |
| `POST /offers` | ✅ | Implemented |
| `POST /loans/connect` | ✅ | Implemented — **⚠️ Missing KYC gate** |
| `PUT /loans/{id}/terms` | ✅ | Implemented |
| `POST /loans/{id}/finalize` | ✅ | Implemented |
| `POST /loans/{id}/cancel` | ✅ | Implemented |
| `GET /loans/{id}/agreement` | ✅ | In NegotiationController |
| `POST /loans/{id}/sign` | ✅ | In NegotiationController |
| `POST /payments/fee/pay` | ✅ | Implemented |
| `POST /loans/{id}/escrow/generate` | ✅ | Implemented |
| `POST /loans/{id}/deposit` | ✅ | **⚠️ IDOR risk** |
| `GET /loans/{id}/collateral` | ✅ | Implemented in CollateralReleaseController |
| `GET /loans/{id}/payment-details` | ✅ | Implemented |
| `POST /loans/{id}/disburse` | ✅ | Implemented |
| `POST /loans/{id}/confirm-receipt` | ✅ | Implemented |
| `POST /loans/{id}/open-dispute` | ✅ | Implemented |
| `POST /loans/{id}/repay` | ✅ | Implemented |
| `GET /loans/{id}/ledger` | ✅ | Implemented |
| `POST /admin/loans/{id}/release-collateral` | ✅ | **⚠️ Missing @PreAuthorize** |
| `GET /btc/price` | ✅ | Implemented |
| `GET /btc/price/history` | ✅ | Implemented |

---

## 🔷 Step 2: State Machine Audit

| Transition | Status | Notes |
|-----------|--------|-------|
| `NEGOTIATING → AWAITING_SIGNATURES` | ✅ | Via `FINALIZE_CONTRACT` |
| `AWAITING_SIGNATURES → AWAITING_FEE` | ✅ | Via `SIGN_CONTRACT` (both parties) |
| `AWAITING_FEE → AWAITING_COLLATERAL` | ✅ | Via `PAY_FEE` |
| `AWAITING_COLLATERAL → COLLATERAL_LOCKED` | ✅ | Via `DEPOSIT_COLLATERAL` |
| `COLLATERAL_LOCKED → ACTIVE` | ✅ | Via `DISBURSE_FIAT` |
| `ACTIVE → REPAID` | ✅ | Via `REPAY_LOAN` |
| `REPAID → CLOSED` | ✅ | Via `RELEASE_COLLATERAL` (admin) |
| `ACTIVE → MARGIN_CALL` | ✅ | Via `LTV_DROP_MARGIN_CALL` |
| `MARGIN_CALL → LIQUIDATION_ELIGIBLE` | ✅ | Via `LTV_DROP_LIQUIDATION` |
| `LIQUIDATION_ELIGIBLE → LIQUIDATED` | ✅ | Via `EXECUTE_LIQUIDATION` |
| `COLLATERAL_LOCKED → DISPUTE_OPEN` | ✅ | Via `MARK_DISPUTE` |
| `DISPUTE_OPEN → ACTIVE` | ✅ | Via `RESOLVE_DISPUTE` |
| `DISPUTE_OPEN → CLOSED` | ✅ | Via `RELEASE_COLLATERAL` + outstanding=0 guard |
| Terminal state protection | ✅ | `CLOSED`, `LIQUIDATED`, `CANCELLED` fully protected |
| Admin override safety | ✅ | `ADMIN_ALLOWED_TRANSITIONS` map restricts targets — **⚠️ Missing DISPUTE_OPEN→ACTIVE** |

---

## 🔷 Step 3: Frontend Page Audit

| Page | Route | Exists | Issues |
|------|-------|--------|--------|
| Login | `/login` | ✅ | Clean |
| Register | `/register` | ✅ | Clean |
| Dashboard | `/dashboard` | ✅ | Clean. Pagination missing for large loan lists |
| Marketplace | `/marketplace` | ✅ | Clean |
| KYC | `/kyc` | ✅ | KYC not enforced before loan connect |
| Loan Negotiation | `/loans/:id` | ✅ | **❌ `loanId` param bug** |
| Agreement | `/loans/:id/agreement` | ✅ | **❌ Post-sign redirect to wrong page** |
| Fee | `/loans/:id/fee` | ✅ | Clean |
| Collateral | `/loans/:id/collateral` | ✅ | **❌ POST via useQuery** |
| Disbursement | `/loans/:id/disbursement` | ✅ | **❌ isLender detection broken** |
| Repayment | `/loans/:id/repay` | ✅ | Hardcoded proofUrl |
| Closure | `/loans/:id/closure` | ✅ | Interest calc wrong, PDF button dead |

---

## 🔷 Step 4: Frontend ↔ Backend Integration

| Page | API Called | Correct Payload | Error Handling | Loading State |
|------|-----------|-----------------|----------------|---------------|
| Dashboard | `GET /loans/mine` | ✅ | ✅ | ✅ |
| Dashboard | `GET /loans/{id}/details` (on click) | ✅ | — | — |
| Negotiation | `GET /loans/${loanId}/details` | ⚠️ `loanId` = undefined | ❌ No error shown | ✅ |
| Negotiation | `GET /chat/messages` | ⚠️ `loanId` = undefined | ❌ | ✅ |
| Negotiation | `PUT /loans/${loanId}/terms` | ✅ payload | ❌ No error display | — |
| Agreement | `GET /loans/${id}/agreement` | ✅ | ✅ | ✅ |
| Agreement | `POST /loans/${id}/sign` | ✅ | ✅ | ✅ |
| Fee | `POST /payments/fee/pay` | ✅ `{ loan_id }` | ⚠️ No error display | ✅ |
| Collateral | `POST /loans/${id}/escrow/generate` | ✅ | ❌ In useQuery | ⚠️ |
| Collateral | `POST /loans/${id}/deposit` | ✅ `{ amount_btc }` | ⚠️ Silent | ✅ |
| Disbursement | `GET /loans/${id}/payment-details` | ✅ | ⚠️ Only lender but detection broken | ✅ |
| Disbursement | `POST /loans/${id}/disburse` | ✅ | ⚠️ Silent | ✅ |
| Disbursement | `POST /loans/${id}/confirm-receipt` | ✅ | ✅ | ✅ |
| Disbursement | `POST /loans/${id}/open-dispute` | ✅ | ✅ | ✅ |
| Repayment | `POST /loans/${id}/repay` | ✅ `{ amount, transaction_reference, proof_image_url }` | ✅ Overpayment guard | ✅ |
| Repayment | `GET /loans/${id}/ledger` | ✅ | ✅ | ✅ |
| Closure | `GET /loans/${id}/details` | ✅ | ✅ | ✅ |

---

## 🔷 Step 5: Business Flow Validation

| Step | Status | Notes |
|------|--------|-------|
| 1. Register → Login | ✅ | Works |
| 2. Marketplace → Apply (connectOffer) | ⚠️ | No KYC gate |
| 3. KYC → Verified | ⚠️ | KYC submit works, but not enforced as prerequisite |
| 4. Negotiation → Finalize | ❌ | `loanId = undefined` breaks all negotiation API calls |
| 5. Agreement → Sign | ✅ | Works (if user navigates directly) — but redirect broken post-sign |
| 6. Fee → Paid | ✅ | Works |
| 7. Collateral → Locked | ⚠️ | Escrow via useQuery causes race conditions |
| 8. Disbursement → Confirmed | ❌ | isLender detection always false — lender can't mark as disbursed |
| 9. Repayment → Completed | ✅ | Works (backend correctly transitions to REPAID) |
| 10. Closure → Collateral Released | ⚠️ | Admin must manually trigger; no UI; `AGREED` state is dead |

---

## 🔷 Step 6: Security Validation

| Check | Status | Details |
|-------|--------|---------|
| JWT required for protected routes | ✅ | All non-public routes require valid JWT |
| RBAC for admin routes | ✅ | URL matcher + `@PreAuthorize` on most admin routes |
| [CollateralReleaseController](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/controller/CollateralReleaseController.java#17-53) admin route | ⚠️ | URL matcher only, no `@PreAuthorize` |
| Loan ownership (repayment) | ✅ | Only borrower can submit repayment |
| Loan ownership (disbursement) | ✅ | Service verifies lender/borrower email |
| IDOR on `/loans/{id}/deposit` | ⚠️ | Service-level check not confirmed |
| KYC enforcement | ❌ | No KYC gate before loan lifecycle entry |
| Sensitive data exposure | ✅ | Pseudonyms used in negotiation; full details only to participants |
| Brute-force protection | ✅ | `LoginAttemptService` exists |
| CORS | ✅ | Configured via `allowedOrigins` property |
| CSRF | ✅ | Disabled (stateless JWT — appropriate) |

---

## 🔷 Step 7: Financial Validation

| Check | Status | Details |
|-------|--------|---------|
| Overpayment prevention | ✅ | Both UI and service enforce `amount <= totalOutstanding` |
| Interest formula | ✅ | Simple interest: `P × R × D / 36500` |
| Outstanding calculation | ✅ | Principal + interest tracked separately |
| Interest paid first | ✅ | [processRepaymentFinancials()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#141-183) deducts interest before principal |
| Full repayment before closure | ✅ | [StateMachineService](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/StateMachineService.java#21-227) invariant check on `CLOSED` transition |
| Ledger consistency | ✅ | `FIAT_DISBURSEMENT` and `BORROWER_REPAYMENT` entries created |
| Ledger timestamp | ❌ | `LedgerResponse` missing `created_at` field |
| EMI schedule generation | ✅ | [generateEmiSchedule()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#68-89) handles partial last EMI |
| Bullet repayment | ✅ | Works — no EMI generated when type is BULLET |
| Closure interest display | ⚠️ | [LoanClosurePage](file:///d:/LB/mvp/frontend/src/features/loans/LoanClosurePage.tsx#19-178) shows scheduled interest, not ledger-derived interest paid |

---

## 🔷 Step 8: Performance & Scalability

| Check | Status | Details |
|-------|--------|---------|
| Database indexes | ✅ | [Loan](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/domain/Loan.java#13-163) indexed on `status`, `borrower_id`, `lender_id`, `updated_at` |
| LTV worker | ✅ | Background worker (`LiquidationService`) consumes BTC price |
| Polling frequency | ⚠️ | NegotiationPage polls loan at 5s and messages at 2.5s even in non-NEGOTIATING states |
| Marketplace query filtering | ✅ | Supports `amount`, `tenure_days`, `interest_rate`, `expected_ltv_percent` filters |
| N+1 in CollateralReleaseController | ⚠️ | Two DB calls to get balance + loan status |
| EMI query | ⚠️ | `emiRepository.findByLoanIdOrderByEmiNumberAsc()` fetches all EMIs on every payment — no streaming |
| Chat messages in negotiation | ⚠️ | `GET /chat/messages?loanId=...` polls every 2.5s; no WebSocket fallback used in REST path (WebSocket is configured but frontend uses REST polling) |
| No pagination on ledger | ⚠️ | [getLoanLedger()](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/service/RepaymentService.java#184-200) returns all entries; could grow large |
| No pagination on dashboard | ⚠️ | `GET /loans/mine` returns all loans; no limit |

---

*Generated by Antigravity AI System Auditor | LinkBit Platform v0.1-MVP*
