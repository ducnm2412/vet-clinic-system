# Auth Service

Đăng ký/đăng nhập, phân quyền (RBAC), quản lý JWT.

- Database riêng: `auth_db` (PostgreSQL)
- Actor liên quan: Khách hàng, Bác sĩ, Nhân viên, Admin (đăng nhập/phân quyền)
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`
