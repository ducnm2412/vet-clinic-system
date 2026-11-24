/*
  Câu trả lời của chatbot là chữ thuần, chỉ có một thứ được biến thành liên kết: [chữ](/đường-dẫn)
  trỏ vào trang của chính website. Mọi thứ khác (kể cả thẻ HTML, markdown, địa chỉ web bên ngoài)
  giữ nguyên là chữ — React tự thoát ký tự nên không có đường nào chèn được HTML.
*/

export type ChatSegment = { type: "text"; text: string } | { type: "link"; text: string; href: string };

const LINK = /\[([^\]\n]{1,80})\]\(([^)\s]{1,200})\)/g;

/** Đường dẫn nội bộ hợp lệ: bắt đầu bằng "/" (không phải "//" — địa chỉ cùng giao thức), chỉ gồm ký tự an toàn. */
export function isInternalPath(href: string): boolean {
  return href.startsWith("/") && !href.startsWith("//") && /^\/[A-Za-z0-9/_\-.?=&%#]*$/.test(href);
}

export function splitChatText(content: string): ChatSegment[] {
  const segments: ChatSegment[] = [];
  let last = 0;
  for (const match of content.matchAll(LINK)) {
    const [whole, text, href] = match;
    if (!isInternalPath(href)) continue; // liên kết lạ: để nguyên cả cụm như chữ thường
    const start = match.index ?? 0;
    if (start > last) segments.push({ type: "text", text: content.slice(last, start) });
    segments.push({ type: "link", text, href });
    last = start + whole.length;
  }
  if (last < content.length) segments.push({ type: "text", text: content.slice(last) });
  return segments;
}
