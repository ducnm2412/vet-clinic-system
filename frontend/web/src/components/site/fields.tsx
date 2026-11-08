"use client";

import { ChevronDown, Eye, EyeOff } from "lucide-react";
import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";
import { fold } from "@/lib/vnAdmin";

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
>(function SiteInput({ label, hint, error, className, id, type, ...props }, ref) {
  const auto = useId();
  const fieldId = id ?? auto;
  const isPassword = type === "password";
  const [visible, setVisible] = useState(false);
  const input = (
    <input
      ref={ref}
      id={fieldId}
      type={isPassword && visible ? "text" : type}
      aria-invalid={error ? true : undefined}
      aria-describedby={error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined}
      className={cn(fieldClass(!!error), "h-12", isPassword && "pr-12", className)}
      {...props}
    />
  );
  return (
    <SiteField label={label} hint={hint} error={error} required={props.required} htmlFor={fieldId}>
      {isPassword ? (
        <div className="relative">
          {input}
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            aria-pressed={visible}
            className="absolute inset-y-0 right-0 flex w-12 items-center justify-center rounded-r-xl text-stone transition-colors hover:text-pine focus-visible:text-teal focus-visible:outline-none"
          >
            {visible ? <EyeOff className="h-5 w-5" aria-hidden /> : <Eye className="h-5 w-5" aria-hidden />}
          </button>
        </div>
      ) : (
        input
      )}
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

/**
 * Ô gõ có gợi ý, chỉ nhận giá trị nằm trong `options`. Gõ không dấu cũng khớp ("ben nghe" ra
 * "Phường Bến Nghé"). Rời ô mà chưa chọn thì trả lại giá trị đang có, không để chữ gõ dở lọt ra ngoài.
 */
export function SiteCombobox({
  label,
  hint,
  error,
  required,
  disabled,
  placeholder,
  options,
  value,
  onChange,
}: Base & {
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  options: string[];
  value: string;
  onChange: (value: string) => void;
}) {
  const id = useId();
  const listId = `${id}-list`;
  // Chữ đang gõ dở; null nghĩa là không gõ, ô hiện đúng `value` (nên giá trị bị đổi từ nơi khác,
  // như ghim bản đồ, tự hiện theo mà không cần effect đồng bộ).
  const [typing, setTyping] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  const query = fold(typing ?? "");
  const shown = typing === null || query === "" ? options : options.filter((o) => fold(o).includes(query));

  function choose(option: string) {
    onChange(option);
    setTyping(null);
    setOpen(false);
  }

  function onBlur() {
    if (typing !== null) {
      const exact = options.find((o) => fold(o) === query);
      if (exact) onChange(exact);
      else if (query === "") onChange("");
    }
    setTyping(null);
    setOpen(false);
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      setOpen(true);
      if (shown.length) setActive((a) => (a + (e.key === "ArrowDown" ? 1 : -1) + shown.length) % shown.length);
    } else if (e.key === "Enter" && open && shown[active]) {
      e.preventDefault();
      choose(shown[active]);
    } else if (e.key === "Escape" && open) {
      // Chỉ đóng danh sách; Esc lần nữa mới đóng hộp thoại.
      e.preventDefault();
      e.stopPropagation();
      setTyping(null);
      setOpen(false);
    }
  }

  return (
    <SiteField label={label} hint={hint} error={error} required={required} htmlFor={id}>
      <div className="relative">
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && shown[active] ? `${id}-opt-${active}` : undefined}
          aria-invalid={error ? true : undefined}
          autoComplete="off"
          disabled={disabled}
          placeholder={placeholder}
          className={cn(fieldClass(!!error), "h-12 pr-11")}
          value={typing ?? value}
          onFocus={(e) => {
            setOpen(true);
            setActive(Math.max(0, options.indexOf(value)));
            e.currentTarget.select();
          }}
          onChange={(e) => {
            setTyping(e.target.value);
            setOpen(true);
            setActive(0);
          }}
          onBlur={onBlur}
          onKeyDown={onKeyDown}
        />
        <ChevronDown
          aria-hidden
          className="pointer-events-none absolute right-4 top-1/2 size-5 -translate-y-1/2 text-stone"
        />
        {open && (
          <ul
            id={listId}
            role="listbox"
            className="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-xl border border-mist bg-white py-1 shadow-[var(--shadow-lift)]"
          >
            {shown.length === 0 ? (
              <li className="px-4 py-2.5 text-[15px] text-stone">Không có kết quả khớp</li>
            ) : (
              shown.map((o, i) => (
                <li
                  key={o}
                  id={`${id}-opt-${i}`}
                  role="option"
                  aria-selected={o === value}
                  // mousedown (không phải click) + preventDefault: chọn xong rồi ô mới mất focus,
                  // nếu không onBlur chạy trước và đóng danh sách trước khi kịp nhận lượt chọn.
                  onMouseDown={(e) => {
                    e.preventDefault();
                    choose(o);
                  }}
                  onMouseEnter={() => setActive(i)}
                  className={cn(
                    "cursor-pointer px-4 py-2.5 text-[15px] text-pine",
                    i === active && "bg-mint",
                    o === value && "font-medium text-teal-deep",
                  )}
                >
                  {o}
                </li>
              ))
            )}
          </ul>
        )}
      </div>
    </SiteField>
  );
}
