"use client";

import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

/*
  Ô nhập của website khách hàng: cao 48px, bo tròn, cỡ chữ 16px.
  16px là ngưỡng iOS không tự phóng to trang khi người ta chạm vào ô — dưới mức đó thì cả
  bố cục nhảy một cái mỗi lần gõ.
*/

export function fieldClass(invalid?: boolean): string {
  return cn(
    "w-full rounded-xl border bg-white px-4 text-[16px] text-pine outline-none transition-colors",
    "placeholder:text-stone/60 disabled:bg-mint disabled:text-stone",
    invalid ? "border-coral-deep" : "border-mist focus:border-teal",
  );
}

export function SiteField({
  label,
  hint,
  error,
  required,
  htmlFor,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  htmlFor: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-[15px] font-medium text-pine">
        {label}
        {required && (
          <span className="ml-0.5 text-coral-deep" aria-hidden>
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} className="mt-1.5 text-sm text-coral-deep">
          {error}
        </p>
      ) : hint ? (
        <p id={`${htmlFor}-hint`} className="mt-1.5 text-sm text-stone">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

interface Base {
  label: string;
  hint?: string;
  error?: string;
}

export const SiteInput = forwardRef<
  HTMLInputElement,
  Base & InputHTMLAttributes<HTMLInputElement>
>(function SiteInput({ label, hint, error, className, id, ...props }, ref) {
  const auto = useId();
  const fieldId = id ?? auto;
  return (
    <SiteField label={label} hint={hint} error={error} required={props.required} htmlFor={fieldId}>
      <input
        ref={ref}
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
        className={cn(fieldClass(!!error), "h-12", className)}
        {...props}
      />
    </SiteField>
  );
});

export const SiteTextarea = forwardRef<
  HTMLTextAreaElement,
  Base & TextareaHTMLAttributes<HTMLTextAreaElement>
>(function SiteTextarea({ label, hint, error, className, id, rows = 3, ...props }, ref) {
  const auto = useId();
  const fieldId = id ?? auto;
  return (
    <SiteField label={label} hint={hint} error={error} required={props.required} htmlFor={fieldId}>
      <textarea
        ref={ref}
        id={fieldId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
        className={cn(fieldClass(!!error), "py-3 leading-relaxed", className)}
        {...props}
      />
    </SiteField>
  );
});

export const SiteSelect = forwardRef<
  HTMLSelectElement,
  Base & SelectHTMLAttributes<HTMLSelectElement>
>(function SiteSelect({ label, hint, error, className, id, children, ...props }, ref) {
  const auto = useId();
  const fieldId = id ?? auto;
  return (
    <SiteField label={label} hint={hint} error={error} required={props.required} htmlFor={fieldId}>
      <select
        ref={ref}
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
        className={cn(fieldClass(!!error), "h-12", className)}
        {...props}
      >
        {children}
      </select>
    </SiteField>
  );
});
