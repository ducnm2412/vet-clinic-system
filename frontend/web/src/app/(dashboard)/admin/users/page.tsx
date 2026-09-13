"use client";

import { useState } from "react";
import { UserPlus } from "lucide-react";
import { PageHeader } from "@/components/layout/DashboardShell";
import { CreateAccountDialog } from "@/components/admin/CreateAccountDialog";
import { UserTable } from "@/components/admin/UserTable";
import { Button } from "@/components/ui";

export default function Page() {
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title="Tài khoản"
        description="Mọi tài khoản đăng nhập vào hệ thống, gồm cả nhân sự và khách hàng."
        actions={
          <Button onClick={() => setCreating(true)}>
            <UserPlus aria-hidden className="size-4" />
            Tạo tài khoản
          </Button>
        }
      />
      <UserTable title="Danh sách tài khoản" unitLabel="tài khoản" />
      <CreateAccountDialog open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
