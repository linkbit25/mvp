# LinkBit MVP Frontend 🚀

LinkBit is a decentralized lending platform that enables users to obtain INR (fiat) loans by pledging Bitcoin (BTC) as collateral. This repository contains the MVP (Minimum Viable Product) frontend, built with React, TypeScript, and Vite.

## 📖 Overview

The LinkBit frontend provides a seamless and intuitive user interface for borrowers, lenders, and administrators to interact with the platform. It handles everything from user authentication and loan discovery to real-time negotiation and platform monitoring.

## ✨ Core Features

-   **Borrower & Lender Dashboards**: Dedicated views for managing loans, tracking collateral, and discovering offers.
-   **Loan Marketplace**: Explore and filter available loan offers.
-   **Real-time Negotiation Interface**: WebSocket-powered chat for finalizing loan terms.
-   **Admin Dashboard**: Comprehensive tools for administrators to monitor the platform, verify collateral deposits, manage disputes, and oversee the system ledger.
-   **Notifications System**: In-app alerts for quick updates on loan status changes and margin calls.
-   **Responsive Design**: A user-friendly, responsive design that works across multiple devices.

## 🛠 Technology Stack

-   **Framework**: React 18, TypeScript
-   **Build Tool**: Vite
-   **Routing**: React Router
-   **Styling**: Custom CSS
-   **Real-time Communication**: WebSockets (STOMP) / SockJS

## 🚥 Getting Started

### Prerequisites
-   Node.js (v18 or higher recommended)
-   npm or yarn

### Running Locally

1.  **Environment Setup**: Ensure your `.env` file is configured with the correct backend API URL. For local development, create a `.env` file from the default variables.
    ```env
    VITE_API_BASE_URL=http://localhost:8080/api/v1
    ```

2.  **Install Dependencies**:
    ```bash
    npm install
    ```

3.  **Launch Dev Server**:
    ```bash
    npm run dev
    ```
    The frontend will be available at `http://localhost:5173` (or the port specified by Vite).

### Building for Production

To create a production build:
```bash
npm run build
```
This will compile the TypeScript code and generate optimize static assets in the `dist` directory, which can be deployed to any static hosting service.

## 📄 License

This project is proprietary and confidential. Unauthorized copying or distribution is prohibited.
