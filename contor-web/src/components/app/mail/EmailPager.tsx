import { ChevronLeft, ChevronRight } from "lucide-react";
import { AppButton } from "@/components/app/AppButton";

interface EmailPagerProps {
  pageIndex: number;
  totalPages: number;
  totalElements: number;
  onPage: (pageIndex: number) => void;
}

/** "Page 1 of 12 · 584 emails" with Previous/Next, placed at the end of an email list (nothing when there is one page). */
export function EmailPager({ pageIndex, totalPages, totalElements, onPage }: EmailPagerProps) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pages" className="mt-3 flex items-center justify-between gap-2 pb-1 text-xs text-muted-foreground">
      <span>
        Page {pageIndex + 1} of {totalPages} · {totalElements} emails
      </span>
      <div className="flex gap-1">
        <AppButton size="sm" variant="ghost" disabled={pageIndex === 0} onClick={() => onPage(pageIndex - 1)}>
          <ChevronLeft className="size-3.5" />
          Previous
        </AppButton>
        <AppButton size="sm" variant="ghost" disabled={pageIndex + 1 >= totalPages} onClick={() => onPage(pageIndex + 1)}>
          Next
          <ChevronRight className="size-3.5" />
        </AppButton>
      </div>
    </nav>
  );
}
