"use client";

import { InputHTMLAttributes, forwardRef, useId } from "react";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  ({ label, error, id: idProp, ...props }, ref) => {
    const generatedId = useId();
    const id = idProp ?? generatedId;
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={id} className="text-sm font-medium text-on-surface">
          {label}
        </label>
        <input
          ref={ref}
          id={id}
          className={[
            "px-4 py-3 rounded-lg border",
            "bg-surface-container-lowest text-on-surface",
            "placeholder:text-on-surface-variant",
            "outline-none transition-colors",
            "focus:border-primary-container",
            error ? "border-status-error" : "border-outline-variant",
          ].join(" ")}
          {...props}
        />
        {error && <p className="text-xs text-status-error">{error}</p>}
      </div>
    );
  },
);
TextField.displayName = "TextField";
