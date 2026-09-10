"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, PawPrint, Receipt } from "lucide-react";
import { bookingApi, customerApi, orderApi } from "@/lib/api";
import { speciesStripe } from "@/lib/utils/status";
import { formatAge } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import { appointmentColumns, orderColumns } from "@/components/dashboard/columns";
import { cn } from "@/lib/utils/cn";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  TableFrame,
  TableSkeleton,
} from "@/components/ui";

export default function CustomerDashboard() {
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });
  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });
  const orders = useQuery({ queryKey: ["orders", "mine"], queryFn: () => orderApi.mine(0, 5) });

  // Lịch sắp tới: chưa huỷ, chưa khám xong, tính từ hôm nay.
  const upcoming = (appointments.data ?? [])
    .filter((a) => a.status === "PENDING" || a.status === "CONFIRMED")
    .sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`));

  return (
    <>
      <PageHeader
        title="Tổng quan"
        description="Lịch khám sắp tới và đơn hàng gần đây của bạn."
        actions={
          <Link href="/customer/appointments/create">
            <Button>Đặt lịch khám</Button>
          </Link>
        }
      />

      <MetricRow>
        <Metric
          label="Thú cưng"
          value={pets.data?.length ?? null}
          icon={PawPrint}
          loading={pets.isLoading}
        />
        <Metric
          label="Lịch khám sắp tới"
          value={upcoming.length}
          icon={CalendarDays}
          loading={appointments.isLoading}
        />
        <Metric
          label="Đơn hàng"
          value={orders.data?.totalElements ?? null}
          icon={Receipt}
          loading={orders.isLoading}
        />
      </MetricRow>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame
          title="Lịch khám sắp tới"
          count={upcoming.length}
          actions={
            <Link href="/customer/appointments" className="text-sm text-moss underline underline-offset-2">
              Xem tất cả
            </Link>
          }
        >
          {appointments.isLoading ? (
            <TableSkeleton rows={3} cols={3} />
          ) : appointments.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được lịch khám." onRetry={() => appointments.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Lịch khám sắp tới"
              rows={upcoming}
              keyOf={(a) => a.id}
              columns={appointmentColumns({ showDate: true })}
              empty={
                <EmptyState
                  title="Chưa có lịch khám nào"
                  description="Đặt lịch để bác sĩ sắp xếp khung giờ cho thú cưng của bạn."
                  action={
                    <Link href="/customer/appointments/create">
                      <Button size="sm">Đặt lịch khám</Button>
                    </Link>
                  }
                />
              }
            />
          )}
        </TableFrame>

        <TableFrame
          title="Đơn hàng gần đây"
          count={orders.data?.totalElements}
          actions={
            <Link href="/customer/orders" className="text-sm text-moss underline underline-offset-2">
              Xem tất cả
            </Link>
          }
        >
          {orders.isLoading ? (
            <TableSkeleton rows={3} cols={3} />
          ) : orders.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được đơn hàng." onRetry={() => orders.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Đơn hàng gần đây"
              rows={orders.data?.content ?? []}
              keyOf={(o) => o.id}
              columns={orderColumns()}
              empty={<EmptyState title="Bạn chưa đặt đơn nào" />}
            />
          )}
        </TableFrame>
      </div>

      <section className="mt-6">
        <div className="mb-2 flex items-center gap-3">
          <h2 className="font-medium text-ink">Thú cưng của bạn</h2>
          <Link href="/customer/pets" className="ml-auto text-sm text-moss underline underline-offset-2">
            Quản lý
          </Link>
        </div>

        {pets.isLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div className="h-20 animate-pulse rounded-[var(--radius-control)] bg-line" />
            <div className="h-20 animate-pulse rounded-[var(--radius-control)] bg-line" />
          </div>
        ) : pets.data && pets.data.length > 0 ? (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {pets.data.map((pet) => (
              <li key={pet.id}>
                <Link
                  href={`/customer/pets/${pet.id}`}
                  className="flex overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface transition-colors hover:border-line-strong"
                >
                  {/* Dải màu theo loài — mượn từ tab bìa hồ sơ bệnh án giấy. */}
                  <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet.species))} />
                  <span className="min-w-0 flex-1 px-4 py-3">
                    <span className="block font-medium text-ink">{pet.name}</span>
                    <span className="mt-0.5 block text-sm text-bark">
                      {pet.species}
                      {pet.breed ? ` ${pet.breed}` : ""} · {formatAge(pet.dateOfBirth)}
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <div className="rounded-[var(--radius-control)] border border-line bg-surface">
            <EmptyState
              title="Chưa có thú cưng nào"
              description="Thêm thú cưng trước khi đặt lịch khám."
              action={
                <Link href="/customer/pets">
                  <Button size="sm">Thêm thú cưng</Button>
                </Link>
              }
            />
          </div>
        )}
      </section>
    </>
  );
}


