"use client";

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type FormEvent,
  type KeyboardEvent,
} from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { MessageCircle, PawPrint, RotateCcw, Send, X } from "lucide-react";
import { ApiError, CHAT_MAX_CHARS, CHAT_MAX_MESSAGES, chatApi } from "@/lib/api";
import { splitChatText } from "@/lib/chatText";
import { CLINIC } from "@/config/clinic";
import { cn } from "@/lib/utils/cn";
import { BrandMark } from "./Brand";

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

/**
 * Kho lịch sử dùng chung cho useSyncExternalStore. Server (và lượt hydrate đầu) luôn thấy mảng rỗng,
 * trình duyệt đọc sessionStorage một lần rồi giữ trong bộ nhớ, nên không lệch hydration và không bao
 * giờ ghi đè lịch sử bằng mảng rỗng trước khi đọc xong.
 */
const EMPTY: ChatMessage[] = [];
let memory: ChatMessage[] | null = null;
const listeners = new Set<() => void>();

function getHistory(): ChatMessage[] {
  if (memory === null) memory = loadHistory();
  return memory;
}

function getServerHistory(): ChatMessage[] {
  return EMPTY;
}

function subscribeHistory(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function setHistory(update: ChatMessage[] | ((cur: ChatMessage[]) => ChatMessage[])) {
  memory = typeof update === "function" ? update(getHistory()) : update;
  saveHistory(memory);
  listeners.forEach((l) => l());
}

let idSeq = 0;
/** Chỉ gọi trong event handler / callback, không gọi lúc render. */
function newId(): string {
  return `${Date.now()}-${idSeq++}`;
}

/** Giãn chiều cao ô nhập theo nội dung, tối đa 96px (max-h-24). */
function fitHeight(el: HTMLTextAreaElement) {
  el.style.height = "auto";
  el.style.height = `${Math.min(el.scrollHeight, 96)}px`;
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
  const messages = useSyncExternalStore(subscribeHistory, getHistory, getServerHistory);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

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

  // Ô nhập tự giãn theo nội dung (kể cả khi bị xoá trắng sau khi gửi / làm mới).
  useLayoutEffect(() => {
    const el = inputRef.current;
    if (el) fitHeight(el);
  }, [input]);

  const request = useCallback(async (history: ChatMessage[]) => {
    setSending(true);
    setNotice(null);
    try {
      const turns = history
        .slice(-CHAT_MAX_MESSAGES)
        .map((m) => ({ role: m.role, content: m.content.slice(0, CHAT_MAX_CHARS) }));
      const res = await chatApi.send(turns);
      setHistory((cur) => [...cur, { id: newId(), role: "assistant", content: res.reply }]);
    } catch (e) {
      setNotice(errorText(e));
    } finally {
      setSending(false);
    }
  }, []);

  function submit(text: string) {
    const content = text.trim().slice(0, CHAT_MAX_CHARS);
    if (!content || sending) return;
    const next: ChatMessage[] = [...messages, { id: newId(), role: "user", content }];
    setHistory(next);
    setInput("");
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

  function reset() {
    setHistory([]);
    setNotice(null);
    setInput("");
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
                onChange={(e) => setInput(e.target.value)}
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
        title={open ? "Đóng trò chuyện" : "Hỏi trợ lý Vet Clinic"}
        className="group fixed bottom-4 right-4 z-50 grid size-16 place-items-center rounded-full bg-white shadow-[var(--shadow-lift)] ring-4 ring-white transition-transform hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal sm:right-5"
      >
        {open ? (
          <span className="grid size-full place-items-center rounded-full bg-teal text-white">
            <X aria-hidden className="size-7" />
          </span>
        ) : (
          <>
            {/* Logo phòng khám làm nút mở chat; bong bóng nhỏ ở góc cho biết đây là trò chuyện. */}
            <BrandMark className="size-full" />
            <span
              aria-hidden
              className="absolute -right-1 -top-1 grid size-6 place-items-center rounded-full bg-coral text-white ring-2 ring-white"
            >
              <MessageCircle className="size-3.5" />
            </span>
          </>
        )}
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
