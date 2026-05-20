"use client";

import { ButtonHTMLAttributes, forwardRef } from "react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ children, loading, disabled, className = "", ...props }, ref) => (
    <button
      ref={ref}
      disabled={disabled ?? loading}
      className={[
        "w-full py-3 px-6 rounded-xl",
        "bg-primary-container text-on-primary",
        "text-sm font-semibold tracking-wide",
        "transition-opacity hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed",
        className,
      ].join(" ")}
      {...props}
    >
      {loading ? "처리 중…" : children}
    </button>
  ),
);
Button.displayName = "Button";
