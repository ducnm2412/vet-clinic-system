"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, CalendarDays, CalendarPlus, Package, PawPrint, Plus, ShoppingBag, Stethoscope, type LucideIcon } from "lucide-react";
import { bookingApi, myPetApi, orderApi } from "@/lib/api";
import { todayISO } from "@/lib/utils/format";
import { AppointmentCard, OrderCard, StatisticCard, WelcomeBanner } from "@/components/user/Cards";
import { EmptyState, SkeletonList } from "@/components/user/EmptyState";
import { useDisplayName } from "@/components/user/UserSidebar";
import { btnPrimary, card, cardHover } from "@/components/user/ui";
import { cn } from "@/lib/utils/cn";

/** Trang mặc định sau khi khách đăng nhập: lời chào, số liệu, lịch hẹn sắp tới, đơn gần đây, thao tác nhanh. */
export default function UserDashboardPage() {
  const name = useDisplayName();
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: myPetApi.list });
  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });
  const orders = useQuery({ queryKey: ["orders", "mine", 0], queryFn: () => orderApi.mine(0, 10) });

  const today = todayISO();
  const upcoming = (appointments.data ?? [])
    .filter((a) => a.date >= today && (a.status === "PENDING" || a.status === "CONFIRMED"))
    .sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`));
  const petById = new Map((pets.data ?? []).map((p) => [p.id, p]));
  const visits = (appointments.data ?? []).filter((a) => a.status === "COMPLETED").length;
  const recentOrders = (orders.data?.content ?? []).slice(0, 3);

  return (
    <div className="space-y-6">
      <WelcomeBanner name={name}>
        <div className="mt-5 flex flex-wrap gap-2.5">
          <Link href="/appointments/create" className={btnPrimary}>
            <CalendarPlus aria-hidden className="size-4" /> Đặt lịch khám
          </Link>
          {upcoming[0] && (
            <span className="inline-flex h-11 items-center rounded-full bg-white/80 px-4 text-sm text-[#1f4a3c] backdrop-blur">
              Bạn có <b className="mx-1">{upcoming.length}</b> lịch hẹn sắp tới
            </span>
          )}
        </div>
      </WelcomeBanner>

      <section aria-label="Tổng quan" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatisticCard icon={Package} label="Tổng đơn hàng" value={orders.data?.totalElements ?? "–"} unit="đơn" href="/user/orders" tone="orange" />
        <StatisticCard icon={CalendarDays} label="Lịch hẹn sắp tới" value={appointments.data ? upcoming.length : "–"} unit="lịch" href="/user/appointments" tone="green" />
        <StatisticCard icon={PawPrint} label="Thú cưng" value={pets.data?.length ?? "–"} unit="bé" href="/user/pets" tone="sky" />
        <StatisticCard icon={Stethoscope} label="Lần đã khám" value={appointments.data ? visits : "–"} unit="lần" href="/user/appointments" tone="gold" />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_300px]">
        <div className="min-w-0 space-y-6">
          <Section title="Lịch hẹn sắp tới" href="/user/appointments">
            {appointments.isLoading ? (
              <SkeletonList count={2} />
            ) : upcoming.length === 0 ? (
              <EmptyState
                icon={CalendarDays}
                title="Chưa có lịch hẹn sắp tới"
                description="Đặt lịch trước để đến nơi là được khám ngay."
                action={
                  <Link href="/appointments/create" className={btnPrimary}>
                    Đặt lịch khám
                  </Link>
                }
              />
            ) : (
              <ul className="space-y-3">
                {upcoming.slice(0, 2).map((a) => (
                  <li key={a.id}>
                    <AppointmentCard appointment={a} pet={petById.get(a.petId)} compact />
                  </li>
                ))}
              </ul>
            )}
          </Section>

          <Section title="Đơn hàng gần đây" href="/user/orders">
            {orders.isLoading ? (
              <SkeletonList count={2} />
            ) : recentOrders.length === 0 ? (
              <EmptyState icon={ShoppingBag} title="Bạn chưa có đơn hàng nào" description="Thức ăn, phụ kiện và đồ chăm sóc đều có sẵn tại cửa hàng." />
            ) : (
              <ul className="space-y-3">
                {recentOrders.map((o) => (
                  <li key={o.id}>
                    <OrderCard order={o} compact />
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </div>

        <aside className="space-y-6">
          <Section title="Thao tác nhanh">
            <div className="grid grid-cols-2 gap-3">
              <QuickAction href="/user/pets?add=1" icon={Plus} label="Thêm thú cưng" tone="bg-[#fff1e3] text-[#f57c1f]" />
              <QuickAction href="/appointments/create" icon={CalendarPlus} label="Đặt lịch khám" tone="bg-[#e3f2ec] text-[#1f7a5a]" />
              <QuickAction href="/user/orders" icon={Package} label="Xem đơn hàng" tone="bg-[#e8f1fb] text-[#2f6fb2]" />
              <QuickAction href="/products" icon={ShoppingBag} label="Mua sắm" tone="bg-[#fff6d9] text-[#c98a00]" />
            </div>
          </Section>

          {(pets.data?.length ?? 0) > 0 && (
            <Section title="Thú cưng của bạn" href="/user/pets">
              <ul className={cn(card, "divide-y divide-[#eeeeee]")}>
                {pets.data!.slice(0, 4).map((p) => (
                  <li key={p.id}>
                    <Link href={`/user/pets/${p.id}`} className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-[#fffaf5]">
                      <span className="grid size-10 place-items-center rounded-full bg-[#fff1e3] text-sm font-bold text-[#f57c1f]">
                        {p.name.charAt(0)}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-semibold text-[#242424]">{p.name}</span>
                        <span className="block truncate text-xs text-[#777777]">
                          {p.species}
                          {p.breed ? ` · ${p.breed}` : ""}
                        </span>
                      </span>
                      <ArrowRight aria-hidden className="size-4 text-[#bbbbbb]" />
                    </Link>
                  </li>
                ))}
              </ul>
            </Section>
          )}
        </aside>
      </div>
    </div>
  );
}

function Section({ title, href, children }: { title: string; href?: string; children: React.ReactNode }) {
  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-bold text-[#1f4a3c]">{title}</h2>
        {href && (
          <Link href={href} className="inline-flex items-center gap-1 text-sm font-medium text-[#777777] hover:text-[#f57c1f]">
            Xem tất cả <ArrowRight aria-hidden className="size-4" />
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}

function QuickAction({ href, icon: Icon, label, tone }: { href: string; icon: LucideIcon; label: string; tone: string }) {
  return (
    <Link href={href} className={cn(card, cardHover, "flex flex-col items-center gap-2.5 px-3 py-5 text-center")}>
      <span className={cn("grid size-11 place-items-center rounded-2xl", tone)}>
        <Icon aria-hidden className="size-5" />
      </span>
      <span className="text-[13px] font-semibold text-[#242424]">{label}</span>
    </Link>
  );
}
