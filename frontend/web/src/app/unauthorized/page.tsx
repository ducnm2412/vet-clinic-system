"use client";

import { useRouter } from "next/navigation";
import { ShieldOff } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { ROLE_LABEL } from "@/lib/api";
import { ButtonLink, SiteButton } from "@/components/site/primitives";

export default function UnauthorizedPage() {
  const { roles, email, signOut } = useAuth();
  const router = useRouter();

  return (
    <div className="flex min-h-dvh items-center justify-center bg-white px-6 text-pine">
      <div className="max-w-lg text-center">
        <ShieldOff aria-hidden className="mx-auto size-9 text-stone" />
        <h1 className="t-h2 mt-6">Khu vực này không dành cho tài khoản của bạn</h1>
        <p className="mt-4 text-stone">
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

        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <ButtonLink href={homeFor(roles)}>Về khu vực của tôi</ButtonLink>
          <SiteButton
            variant="outline"
            onClick={() => {
              signOut();
              router.push("/login");
            }}
          >
            Đăng nhập tài khoản khác
          </SiteButton>
        </div>
      </div>
    </div>
  );
}
