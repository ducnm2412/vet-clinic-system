/**
 * Mở link thanh toán. Link có thể là trang trong web (cổng giả lập, đi bằng router cho mượt) hoặc
 * địa chỉ ngoài (cổng thật, phải rời hẳn trang).
 */
export function goToPaymentUrl(router: { push: (href: string) => void }, url: string): void {
  if (url.startsWith("/")) router.push(url);
  else window.location.assign(url);
}
