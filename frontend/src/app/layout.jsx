import { Outfit, DM_Mono } from "next/font/google";
import "./globals.css";
import AppLayout from "@/components/layout/AppLayout";
import ErrorBoundary from "@/components/common/ErrorBoundary";
import ProtectedRoute from "@/components/common/ProtectedRoute";
import { UserProvider } from "@/contexts/UserContext";
import { WalletProvider } from "@/contexts/WalletContext";
import { LanguageProvider, I18nProvider } from "@/i18n";

const outfit = Outfit({
  variable: "--font-outfit",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
});

const dmMono = DM_Mono({
  variable: "--font-dm-mono",
  subsets: ["latin"],
  weight: ["300", "400", "500"],
});

export const metadata = {
  title: "Savix",
  description: "Track your expenses with ease",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${outfit.variable} ${dmMono.variable} antialiased`}
      >
        <ErrorBoundary>
          {/* LanguageProvider manages language state and localStorage persistence */}
          <LanguageProvider>
            {/* I18nProvider wraps NextIntlClientProvider with current language */}
            <I18nProvider>
              <UserProvider>
                <ProtectedRoute>
                  <WalletProvider>
                    <AppLayout>
                      {children}
                    </AppLayout>
                  </WalletProvider>
                </ProtectedRoute>
              </UserProvider>
            </I18nProvider>
          </LanguageProvider>
        </ErrorBoundary>
      </body>
    </html>
  );
}
