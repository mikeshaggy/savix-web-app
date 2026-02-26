"use client";
import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  Loader2,
  Mail,
  AlertCircle,
  Wallet,
  CheckCircle,
  ArrowLeft,
} from "lucide-react";
import { authApi, ApiError } from "@/lib/api";
import { useTranslations } from "next-intl";

export default function ForgotPasswordPage() {
  const t = useTranslations();
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);

    try {
      await authApi.forgotPassword({ email });
      setSuccess(true);
    } catch (err) {
      console.error("Forgot password failed:", err);

      if (err instanceof ApiError) {
        setSuccess(true);
      } else {
        setError(t("auth.serverError"));
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <div className="w-full max-w-md text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-green-500/10 rounded-full mb-4">
            <CheckCircle className="w-8 h-8 text-green-500" />
          </div>
          <h1 className="text-2xl font-bold text-white mb-2">
            {t("auth.checkYourEmail")}
          </h1>
          <p className="text-gray-400 mb-6">
            {t("auth.resetLinkSent").replace("{email}", "")}
            <span className="text-white">{email}</span>
            {t("auth.resetLinkSent").split("{email}")[1]}
          </p>
          <Link
            href="/login"
            className="inline-flex items-center gap-2 text-violet-400 hover:text-violet-300 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {t("auth.backToLogin")}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo/Brand */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-18 h-18 rounded-3xl mb-4">
            <Image
              src="/logo.png"
              alt="Savix"
              width={192}
              height={192}
              className="rounded-3xl"
            />
          </div>
          <h1 className="text-2xl font-bold text-white">
            {t("auth.forgotPasswordTitle")}
          </h1>
          <p className="text-gray-400 mt-2">
            {t("auth.forgotPasswordDescription")}
          </p>
        </div>

        {/* Forgot Password Form */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Error Alert */}
            {error && (
              <div className="flex items-center gap-3 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
                <AlertCircle className="w-5 h-5 shrink-0" />
                <p className="text-sm">{error}</p>
              </div>
            )}

            {/* Email Field */}
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-gray-300 mb-2"
              >
                {t("auth.email")}
              </label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
                <input
                  type="email"
                  id="email"
                  name="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    setError(null);
                  }}
                  placeholder={t("auth.enterEmail")}
                  required
                  autoComplete="email"
                  className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500 transition-colors"
                />
              </div>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-violet-500 hover:bg-violet-600 disabled:bg-violet-500/50 disabled:cursor-not-allowed text-white font-medium py-3 px-4 rounded-lg transition-colors flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  {t("auth.sending")}
                </>
              ) : (
                t("auth.sendResetLink")
              )}
            </button>
          </form>

          {/* Back to Login Link */}
          <div className="mt-6 pt-6 border-t border-gray-800 text-center">
            <Link
              href="/login"
              className="inline-flex items-center gap-2 text-violet-400 hover:text-violet-300 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              {t("auth.backToLogin")}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
