import { ShieldCheck } from "lucide-react";
import type { ReportClaimDto } from "@/lib/api/generated/model/reportClaimDto";
import type { ReportDto } from "@/lib/api/generated/model/reportDto";
import { verifiedLabel } from "@/lib/report";

/** What the fact check did: the count, and on demand the rewritten and removed sentences with the reason. */
export function FactCheck({ report }: { report: ReportDto }) {
  if (report.claimsTotal === 0) return null;
  const repaired = report.sections.flatMap((section) => [...section.claims, ...section.findings.flatMap((finding) => finding.claims)])
    .filter((claim) => claim.check === "REPAIRED");
  const changed = repaired.length + report.removedClaims.length;
  return (
    <details className="rounded-xl border px-4 py-3 text-sm">
      <summary className="flex cursor-pointer items-center gap-2 font-medium">
        <ShieldCheck className="size-4 text-success" aria-hidden="true" />
        Fact check: {verifiedLabel(report.claimsVerified, report.claimsTotal)}
        {changed > 0 ? (
          <span className="font-normal text-muted-foreground">
            · {repaired.length} rewritten, {report.removedClaims.length} removed
          </span>
        ) : null}
      </summary>
      <p className="mt-2 text-xs text-muted-foreground">
        Every sentence was checked against the sources it cites: sources must exist, quotes must be verbatim, numbers and
        dates must appear in the sources, and a separate model call confirmed each sentence.
        {report.model ? ` Model ${report.model}, prompt ${report.promptVersion ?? "?"}.` : null}
      </p>
      {repaired.length > 0 ? <ClaimList title="Rewritten" claims={repaired} /> : null}
      {report.removedClaims.length > 0 ? <ClaimList title="Removed" claims={report.removedClaims} /> : null}
    </details>
  );
}

function ClaimList({ title, claims }: { title: string; claims: readonly ReportClaimDto[] }) {
  return (
    <div className="mt-3 grid gap-1.5">
      <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{title}</h3>
      <ul className="grid gap-2">
        {claims.map((claim) => (
          <li key={claim.id} className="grid gap-0.5">
            <span>{claim.text}</span>
            {claim.note ? <span className="text-xs text-muted-foreground">{claim.note}</span> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
