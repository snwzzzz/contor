import { AuthLayout } from "@/components/layout/AuthLayout";

export default function Layout({ children }: LayoutProps<"/">) {
  return <AuthLayout>{children}</AuthLayout>;
}
