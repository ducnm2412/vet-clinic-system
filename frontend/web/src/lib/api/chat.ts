import { http } from "./client";

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface ChatReply {
  reply: string;
  /** rule = câu soạn sẵn, ai = Gemini trả lời, fallback = Gemini lỗi nên trả câu dự phòng. */
  source: "rule" | "ai" | "fallback";
}

/** Giới hạn khớp chat-service: mỗi lượt gửi tối đa 20 tin gần nhất, mỗi tin tối đa 1000 ký tự. */
export const CHAT_MAX_MESSAGES = 20;
export const CHAT_MAX_CHARS = 1000;

export const chatApi = {
  /**
   * enabled: chatbot có mở không (nút chat chỉ hiện khi true). aiEnabled: backend đã có GEMINI_API_KEY
   * chưa; chưa có thì bot vẫn trả lời câu soạn sẵn, còn câu cần AI nhận câu dự phòng.
   */
  status: () => http.get<{ enabled: boolean; aiEnabled: boolean }>("/chat/status", true),

  /**
   * Gửi hội thoại (service không lưu gì, lịch sử do frontend giữ). Không đặt publicRoute để token
   * đi kèm khi khách đã đăng nhập: được hạn mức cao hơn và hỏi được giờ khám còn trống.
   */
  send: (messages: ChatTurn[]) => http.post<ChatReply>("/chat/messages", { messages }),
};
