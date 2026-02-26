"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import {
  Loader2,
  Mail,
  Lock,
  User,
  AlertCircle,
  Wallet,
  CheckCircle,
} from "lucide-react";
import { authApi, ApiError } from "@/lib/api";
import { useTranslations } from "next-intl";

export default function RegisterPage() {
  const router = useRouter();
  const t = useTranslations();
  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setError(null);
  };

  const validateForm = () => {
    if (formData.password !== formData.confirmPassword) {
      setError(t("auth.passwordsDoNotMatch"));
      return false;
    }
    if (formData.password.length < 12) {
      setError(t("auth.passwordTooShort"));
      return false;
    }
    if (formData.password.length > 255) {
      setError(t("auth.passwordTooLong"));
      return false;
    }
    if (formData.username.length < 3) {
      setError(t("auth.usernameTooShort"));
      return false;
    }
    if (formData.username.length > 100) {
      setError(t("auth.usernameTooLong"));
      return false;
    }
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) return;

    setIsLoading(true);
    setError(null);

    try {
      await authApi.register({
        username: formData.username,
        email: formData.email,
        password: formData.password,
      });

      setSuccess(true);

      setTimeout(() => {
        router.push("/login");
      }, 2000);
    } catch (err) {
      console.error("Registration failed:", err);

      if (err instanceof ApiError) {
        if (err.status === 409) {
          setError(t("auth.emailAlreadyExists"));
        } else if (err.details?.errors) {
          const errorMessages = Object.values(err.details.errors).flat();
          setError(errorMessages.join(". "));
        } else {
          setError(err.message || t("auth.registrationFailed"));
        }
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
            {t("auth.accountCreated")}
          </h1>
          <p className="text-gray-400 mb-4">{t("auth.redirectingToLogin")}</p>
          <Loader2 className="w-6 h-6 animate-spin text-violet-500 mx-auto" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4 py-8">
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
            {t("auth.createAccount")}
          </h1>
          <p className="text-gray-400 mt-2">
            {t("auth.startTrackingExpenses")}
          </p>
        </div>

        {/* Register Form */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Error Alert */}
            {error && (
              <div className="flex items-center gap-3 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
                <AlertCircle className="w-5 h-5 shrink-0" />
                <p className="text-sm">{error}</p>
              </div>
            )}

            {/* Username Field */}
            <div>
              <label
                htmlFor="username"
                className="block text-sm font-medium text-gray-300 mb-2"
              >
                {t("auth.username")}
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
                <input
                  type="text"
                  id="username"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  placeholder={t("auth.usernamePlaceholder")}
                  required
                  autoComplete="username"
                  className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500 transition-colors"
                />
              </div>
            </div>

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
                  value={formData.email}
                  onChange={handleChange}
                  placeholder={t("auth.enterEmail")}
                  required
                  autoComplete="email"
                  className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500 transition-colors"
                />
              </div>
            </div>

            {/* Password Field */}
            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-300 mb-2"
              >
                {t("auth.password")}
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
                <input
                  type="password"
                  id="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  autoComplete="new-password"
                  className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-4 py-3 text-white placeholder-gray-500 focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500 transition-colors"
                />
              </div>
              <p className="mt-1 text-xs text-gray-500">
                {t("auth.passwordHintShort")}
              </p>
            </div>

            {/* Confirm Password Field */}
            <div>
              <label
                htmlFor="confirmPassword"
                className="block text-sm font-medium text-gray-300 mb-2"
              >
                {t("auth.confirmPassword")}
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
                <input
                  type="password"
                  id="confirmPassword"
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  autoComplete="new-password"
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
                  {t("auth.creatingAccount")}
                </>
              ) : (
                t("auth.createAccount")
              )}
            </button>
          </form>

          {/* Login Link */}
          <div className="mt-6 pt-6 border-t border-gray-800 text-center">
            <p className="text-gray-400">
              {t("auth.alreadyHaveAccount")}{" "}
              <Link
                href="/login"
                className="text-violet-400 hover:text-violet-300 font-medium transition-colors"
              >
                {t("auth.signIn")}
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
