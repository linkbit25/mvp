# LinkBit API Documentation

This document provides a detailed overview of all API endpoints available in the LinkBit MVP.

## Authentication (`/auth`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/auth/register` | `POST` | Register a new user | [RegisterRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/RegisterRequest.java#7-32) | `201 Created` |
| `/auth/login` | `POST` | Login and get tokens | [LoginRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/LoginRequest.java#7-17) | [AuthResponse](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/AuthResponse.java#9-18) |
| `/auth/me` | `GET` | Get current user details | None | `UserResponse` |
| `/auth/password/forgot` | `POST` | Request password reset | `ForgotPasswordRequest` | `202 Accepted` |
| `/auth/password/reset` | `POST` | Reset password using link | `ResetPasswordRequest` | `200 OK` |

### Auth DTOs

**RegisterRequest**
- `email` (String, Required)
- `password` (String, Required)
- `phoneNumber` (String, Required)
- `pseudonym` (String, Required)
- `bankAccountNumber` (String, Required)
- `ifsc` (String, Required)
- `upiId` (String, Required)

**AuthResponse**
- `accessToken` (String)
- `refreshToken` (String)
- `userId` (UUID)
- `kycStatus` (Enum)

---

## Loan Marketplace (`/offers`, `/loans`)

| Endpoint | Method | Description | Request/Params | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/offers` | `POST` | Create a new loan offer | [CreateOfferRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/CreateOfferRequest.java#11-36) | `201 Created` |
| `/offers` | `GET` | List available offers | Query Params (amount, tenure, etc) | `List<OfferResponse>` |
| `/offers/{offerId}` | `PUT` | Edit an existing offer | [CreateOfferRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/CreateOfferRequest.java#11-36) | `200 OK` |
| `/loans/connect` | `POST` | Connect to an offer (start loan) | `ConnectOfferRequest` | `UUID (loanId)` |

**CreateOfferRequest**
- `loan_amount_inr` (BigDecimal)
- `interest_rate` (BigDecimal)
- `expected_ltv_percent` (Integer)
- `tenure_days` (Integer)

---

## Negotiation (`/loans/{loanId}`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/loans/{loanId}/terms` | `PUT` | Update loan terms during negotiation | [UpdateTermsRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/UpdateTermsRequest.java#10-45) | `200 OK` |
| `/loans/{loanId}/finalize` | `POST` | Finalize contract terms | None | `200 OK` |
| `/loans/{loanId}/sign` | `POST` | Sign the loan contract | `SignContractRequest` | `200 OK` |
| `/loans/{loanId}/cancel` | `POST` | Cancel negotiation | None | `200 OK` |

---

## Escrow & Collateral (`/loans/{loanId}/escrow`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/loans/{loanId}/escrow/generate` | `POST` | Generate BTC escrow address | None | `EscrowResponse` |
| `/loans/{loanId}/deposit` | `POST` | Submit collateral deposit proof | `DepositRequest` | `202 Accepted` |
| `/loans/{loanId}/topup-collateral` | `POST` | Submit top-up collateral proof | `TopupRequest` | `202 Accepted` |
| `/loans/{loan_id}/collateral` | `GET` | Get collateral balance/status | None | `Map<String, Object>` |

---

## Disbursement (`/loans/{loanId}`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/loans/{loanId}/payment-details` | `GET` | Get payment details for lender | None | `PaymentDetailsResponse` |
| `/loans/{loanId}/disburse` | `POST` | Mark loan as disbursed (Lender) | `DisbursementRequest` | `202 Accepted` |
| `/loans/{loanId}/confirm-receipt` | `POST` | Confirm receipt of fiat (Borrower) | None | `202 Accepted` |
| `/loans/{loanId}/cancel-disbursement`| `POST` | Cancel disbursement | None | `202 Accepted` |
| `/loans/{loanId}/open-dispute` | `POST` | Open a dispute | None | `202 Accepted` |

---

## Repayment (`/loans/{loanId}`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/loans/{loanId}/repay` | `POST` | Submit repayment proof | [RepaymentRequest](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/RepaymentRequest.java#14-32) | `202 Accepted` |
| `/loans/{loanId}/ledger` | `GET` | Get loan repayment ledger | None | `List<LedgerResponse>` |

---

## Dashboard (`/loans`, `/admin`)

| Endpoint | Method | Description | Response |
| :--- | :--- | :--- | :--- |
| `/loans/mine` | `GET` | Get list of user's loans | `List<LoanSummaryResponse>` |
| `/loans/{loanId}/details` | `GET` | Get detailed loan information | [LoanDetailResponse](file:///d:/LB/mvp/src/main/java/com/linkbit/mvp/dto/LoanDetailResponse.java#13-52) |
| `/admin/overview` | `GET` | Get admin dashboard overview | `AdminOverviewResponse` |

---

## Admin Functions (`/admin`)

| Endpoint | Method | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `/admin/repayments/{repaymentId}/verify` | `POST` | Verify a repayment | None | `202 Accepted` |
| `/admin/payments/{feeId}/verify` | `POST` | Verify a fee payment | None | `200 OK` |
| `/admin/collateral/{loanId}/verify` | `POST` | Verify collateral deposit | None | `200 OK` |
| `/admin/collateral/{loanId}/verify-topup` | `POST` | Verify top-up deposit | None | `200 OK` |
| `/admin/loans/{loanId}/activate` | `POST` | Activate loan manually | None | `202 Accepted` |
| `/admin/loans/{loanId}/refund-collateral` | `POST` | Refund collateral manually | None | `202 Accepted` |
| `/admin/loans/{loanId}/release-collateral` | `POST` | Release collateral (closed loan) | None | `200 OK` |
| `/admin/loans/{loanId}/set-risk-state` | `POST` | Override loan risk state | `SetRiskStateRequest` | `202 Accepted` |
| `/admin/loans/{loanId}/execute-liquidation` | `POST` | Execute liquidation | None | `200 OK` |

---

## Miscellaneous

| Endpoint | Method | Description | Response |
| :--- | :--- | :--- | :--- |
| `/btc/price` | `GET` | Get current BTC/INR price | `{"inr": BigDecimal}` |
| `/payments/fee/pay` | `POST` | Initiate platform fee payment | `FeeResponse` |

---

## Chat (WebSockets)

LinkBit uses Spring Messaging for real-time chat between borrower and lender.

**Base Topic / Destination**
- `app/chat.sendMessage`: Destination for sending messages.

**Message Properties (`ChatMessage`)**
- `loanId` (UUID)
- `messageId` (UUID, Server Generated)
- `senderPseudonym` (String, Server Populated)
- `messageText` (String)
- `timestamp` (LocalDateTime, Server Generated)

---

## Key Domain Enums

### LoanStatus
- `OFFER_OPEN`
- `NEGOTIATION`
- `CONTRACT_SIGNED`
- `ESCROW_PENDING`
- `ESCROW_VERIFIED`
- `DISBURSEMENT_PENDING`
- `ACTIVE`
- `CLOSED`
- `MARGIN_CALL`
- `LIQUIDATION_ELIGIBLE`
- `LIQUIDATED`
- `DISPUTED`
- `CANCELLED`

### RepaymentType
- `BULLET`
- `MONTHLY_EMI`
- `WEEKLY_EMI`
