"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { MessageCircle, PawPrint, RotateCcw, Send, X } from "lucide-react";
import { ApiError, CHAT_MAX_CHARS, CHAT_MAX_MESSAGES, chatApi } from "@/lib/api";
import { splitChatText } from "@/lib/chatText";
import { CLINIC } from "@/config/clinic";
import { cn } from "@/lib/utils/cn";

interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
}

/** Lịch sử chỉ sống trong tab này (sessionStorage): đóng tab là hết, không lưu lên máy chủ. */
const STORAGE_KEY = "vetclinic:chat:v1";
const MAX_STORED = 40;

const SUGGESTIONS = [
  "Bên bạn có những dịch vụ nào?",
  "Tiêm phòng cho chó giá bao nhiêu?",
  "Phòng khám mở cửa mấy giờ?",
  "Có bán thức ăn cho mèo không?",
];

function loadHistory(): ChatMessage[] {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (m): m is ChatMessage =>
          typeof m === "object" &&
          m !== null &&
          (m.role === "user" || m.role === "assistant") &&
          typeof m.content === "string" &&
          typeof m.id === "string",
      )
      .slice(-MAX_STORED);
  } catch {
    return [];
  }
}

function saveHistory(messages: ChatMessage[]) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-MAX_STORED)));
  } catch {
    // Trình duyệt chặn lưu trữ: chat vẫn chạy, chỉ không nhớ khi tải lại trang.
  }
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 429) return e.message; // máy chủ đã viết sẵn câu "thử lại sau ... giây"
    if (e.status === 503) return `Trợ lý đang tạm nghỉ. Bạn thử lại sau hoặc gọi hotline ${CLINIC.phone}.`;
    if (e.status === 0) return e.message;
    if (e.status === 400) return "Tin nhắn chưa hợp lệ, bạn thử viết ngắn gọn hơn nhé.";
  }
  return `Chưa gửi được tin nhắn. Bạn thử lại hoặc gọi hotline ${CLINIC.phone}.`;
}

/**
 * Chatbot tư vấn nổi ở góc phải mọi trang khách hàng. Gọi chat-service qua /api/chat/*; chỉ ẩn khi
 * không gọi được chat-service. Chưa có GEMINI_API_KEY thì bot vẫn trả lời các câu soạn sẵn.
 * Nội dung trả lời chỉ hiển thị là chữ thuần, riêng liên kết dạng [chữ](/trang-nội-bộ) được biến
 * thành link — xem lib/chatText.ts.
 */
export function ChatWidget() {
  const status = useQuery({
    queryKey: ["chat", "status"],
    queryFn: chatApi.status,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const loaded = useRef(false);
  const counter = useRef(0);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Đọc lịch sử sau khi trang đã hiển thị (không đọc lúc render để khỏi lệch giữa server và trình duyệt).
  useEffect(() => {
    setMessages(loadHistory());
    loaded.current = true;
  }, []);

  useEffect(() => {
    if (loaded.current) saveHistory(messages);
  }, [messages]);

  // Luôn cuộn xuống tin mới nhất.
  useEffect(() => {
    const list = listRef.current;
    if (open && list) list.scrollTop = list.scrollHeight;
  }, [messages, sending, notice, open]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  // Esc đóng khung chat.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: globalThis.KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const nextId = () => `${Date.now()}-${counter.current++}`;

  const request = useCallback(async (history: ChatMessage[]) => {
    setSending(true);
    setNotice(null);
    try {
      const turns = history
        .slice(-CHAT_MAX_MESSAGES)
        .map((m) => ({ role: m.role, content: m.content.slice(0, CHAT_MAX_CHARS) }));
      const res = await chatApi.send(turns);
      setMessages((cur) => [...cur, { id: `${Date.now()}-${counter.current++}`, role: "assistant", content: res.reply }]);
    } catch (e) {
      setNotice(errorText(e));
    } finally {
      setSending(false);
    }
  }, []);

  function submit(text: string) {
    const content = text.trim().slice(0, CHAT_MAX_CHARS);
    if (!content || sending) return;
    const next: ChatMessage[] = [...messages, { id: nextId(), role: "user", content }];
    setMessages(next);
    setInput("");
    resizeInput(null);
    void request(next);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    submit(input);
  }

  // Enter gửi, Shift+Enter xuống dòng. isComposing: đang gõ dấu tiếng Việt (Telex/VNI) thì Enter chỉ chốt chữ.
  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit(input);
    }
  }

  function resizeInput(el: HTMLTextAreaElement | null) {
    const target = el ?? inputRef.current;
    if (!target) return;
    target.style.height = "auto";
    target.style.height = `${Math.min(target.scrollHeight, 96)}px`;
  }

  function reset() {
    setMessages([]);
    setNotice(null);
    setInput("");
    resizeInput(null);
    inputRef.current?.focus();
  }

  if (!status.data?.enabled) return null;

  const canRetry = notice !== null && messages.length > 0 && messages[messages.length - 1].role === "user";

  return (
    <>
      {open && (
        <section
          role="dialog"
          aria-label="Trợ lý Vet Clinic"
          className="fixed inset-x-3 bottom-20 z-50 flex h-[min(34rem,calc(100dvh-7rem))] animate-[rise_.2s_ease-out] flex-col overflow-hidden rounded-2xl border border-mist bg-white shadow-[var(--shadow-lift)] sm:inset-x-auto sm:right-5 sm:w-[24rem]"
        >
          <header className="flex items-center gap-3 bg-pine px-4 py-3 text-white">
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white/15">
              <PawPrint aria-hidden className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <h2 className="font-display text-base leading-tight">Trợ lý Vet Clinic</h2>
              <p className="text-xs text-white/75">Hỏi về dịch vụ, giá, sản phẩm, đặt lịch</p>
            </div>
            {messages.length > 0 && (
              <button
                type="button"
                onClick={reset}
                aria-label="Bắt đầu cuộc trò chuyện mới"
                title="Cuộc trò chuyện mới"
                className="grid size-9 place-items-center rounded-full text-white/80 transition-colors hover:bg-white/10 hover:text-white"
              >
                <RotateCcw aria-hidden className="size-4" />
              </button>
            )}
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Đóng trò chuyện"
              className="grid size-9 place-items-center rounded-full text-white/80 transition-colors hover:bg-white/10 hover:text-white"
            >
              <X aria-hidden className="size-5" />
            </button>
          </header>

          <div ref={listRef} aria-live="polite" className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-mint/60 px-4 py-4">
            <Bubble role="assistant">
              Xin chào! Mình là trợ lý của Vet Clinic. Bạn cần hỏi gì về dịch vụ, giá tham khảo, sản phẩm hay cách đặt lịch ạ?
            </Bubble>

            {messages.length === 0 && (
              <div className="flex flex-wrap gap-2 pt-1">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => submit(s)}
                    disabled={sending}
                    className="rounded-full border border-teal/40 bg-white px-3 py-1.5 text-left text-sm text-teal-deep transition-colors hover:bg-mist disabled:opacity-60"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}

            {messages.map((m) => (
              <Bubble key={m.id} role={m.role} onNavigate={() => setOpen(false)}>
                {m.content}
              </Bubble>
            ))}

            {sending && (
              <div className="flex" role="status" aria-label="Trợ lý đang trả lời">
                <div className="flex items-center gap-1 rounded-2xl rounded-bl-md border border-mist bg-white px-4 py-3">
                  {[0, 1, 2].map((i) => (
                    <span
                      key={i}
                      className="size-1.5 animate-bounce rounded-full bg-teal motion-reduce:animate-none"
                      style={{ animationDelay: `${i * 0.15}s` }}
                    />
                  ))}
                </div>
              </div>
            )}

            {notice && (
              <p role="alert" className="rounded-xl border border-danger/30 bg-danger-wash px-3 py-2 text-sm text-danger">
                {notice}{" "}
                {canRetry && !sending && (
                  <button type="button" onClick={() => void request(messages)} className="font-semibold underline">
                    Thử lại
                  </button>
                )}
              </p>
            )}
          </div>

          <form onSubmit={onSubmit} className="border-t border-mist bg-white p-3">
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(e) => {
                  setInput(e.target.value);
                  resizeInput(e.target);
                }}
                onKeyDown={onKeyDown}
                rows={1}
                maxLength={CHAT_MAX_CHARS}
                placeholder="Nhập câu hỏi của bạn…"
                aria-label="Tin nhắn"
                className="max-h-24 min-h-10 flex-1 resize-none rounded-xl border border-line-strong bg-white px-3 py-2 text-[15px] text-pine outline-none placeholder:text-stone/70 focus:border-teal focus:ring-2 focus:ring-teal/20"
              />
              <button
                type="submit"
                disabled={sending || input.trim() === ""}
                aria-label="Gửi"
                className="grid size-10 shrink-0 place-items-center rounded-xl bg-teal text-white transition-colors hover:bg-teal-deep disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Send aria-hidden className="size-4" />
              </button>
            </div>
            <p className="mt-2 text-[11px] leading-snug text-stone">
              Trợ lý AI có thể chưa chính xác, giá chỉ là tham khảo. Trường hợp khẩn cấp xin gọi {CLINIC.phone}.
            </p>
          </form>
        </section>
      )}

      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={open ? "Đóng trò chuyện" : "Mở trò chuyện với trợ lý"}
        className="fixed bottom-4 right-4 z-50 flex h-14 items-center gap-2 rounded-full bg-teal px-5 text-white shadow-[var(--shadow-lift)] transition-colors hover:bg-teal-deep focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal sm:right-5"
      >
        {open ? <X aria-hidden className="size-6" /> : <MessageCircle aria-hidden className="size-6" />}
        {!open && <span className="hidden text-sm font-semibold sm:inline">Hỏi trợ lý</span>}
      </button>
    </>
  );
}

function Bubble({
  role,
  children,
  onNavigate,
}: {
  role: "user" | "assistant";
  children: string;
  onNavigate?: () => void;
}) {
  const mine = role === "user";
  return (
    <div className={cn("flex", mine && "justify-end")}>
      <div
        className={cn(
          "max-w-[85%] whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2.5 text-[15px] leading-relaxed",
          mine
            ? "rounded-br-md bg-teal-deep text-white"
            : "rounded-bl-md border border-mist bg-white text-pine",
        )}
      >
        {splitChatText(children).map((seg, i) =>
          seg.type === "link" ? (
            <Link
              key={i}
              href={seg.href}
              onClick={onNavigate}
              className={cn("font-semibold underline underline-offset-2", mine ? "text-white" : "text-teal-deep")}
            >
              {seg.text}
            </Link>
          ) : (
            <span key={i}>{seg.text}</span>
          ),
        )}
      </div>
    </div>
  );
}
