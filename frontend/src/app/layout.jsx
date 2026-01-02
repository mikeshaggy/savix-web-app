import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import AppLayout from "@/components/layout/AppLayout";
import ErrorBoundary from "@/components/common/ErrorBoundary";
import { UserProvider } from "@/contexts/UserContext";
import { WalletProvider } from "@/contexts/WalletContext";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata = {
  title: "ExpenseTracker",
  description: "Track your expenses with ease",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <ErrorBoundary>
          <UserProvider>
            <WalletProvider>
              <AppLayout>
                {children}
              </AppLayout>
            </WalletProvider>
          </UserProvider>
        </ErrorBoundary>
      </body>
    </html>
  );
}
