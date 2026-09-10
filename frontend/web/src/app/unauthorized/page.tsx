"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ShieldOff } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { ROLE_LABEL } from "@/lib/api";
import { Button } from "@/components/ui";

export default function UnauthorizedPage() {
  const { roles, email, signOut } = useAuth();
  const router = useRouter();

  return (
    <div className="flex min-h-dvh items-center justify-center px-6">
      <div className="max-w-md text-center">
        <ShieldOff aria-hidden className="mx-auto size-6 text-bark" />
        <h1 className="mt-4 font-[family-name:var(--font-display)] text-[25px] text-ink">
          Khu vực này không dành cho tài khoản của bạn
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-bark">
          {email ? (
            <>
              Bạn đang đăng nhập bằng {email}
              {roles.length > 0 && <> với quyền {roles.map((r) => ROLE_LABEL[r]).join(", ")}</>}. Cần
              quyền khác thì liên hệ quản trị viên.
            </>
          ) : (
            "Đăng nhập để tiếp tục."
          )}
        </p>

        <div className="mt-6 flex justify-center gap-2">
          <Link href={homeFor(roles)}>
            <Button>Về khu vực của tôi</Button>
          </Link>
          <Button
            variant="secondary"
            onClick={() => {
              signOut();
              router.push("/login");
            }}
          >
            Đăng nhập tài khoản khác
          </Button>
        </div>
      </div>
    </div>
  );
}
