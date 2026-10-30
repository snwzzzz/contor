import { useListContactPhotos, useListSenderLogos } from "@/lib/api/generated/endpoints/senders/senders";

/** Mail providers whose logo says nothing about the person behind an address. */
const FREE_MAIL_DOMAINS = new Set([
  "gmail.com", "googlemail.com", "outlook.com", "outlook.de", "hotmail.com", "hotmail.de", "live.com", "live.de", "msn.com",
  "yahoo.com", "yahoo.de", "icloud.com", "me.com", "mac.com", "gmx.de", "gmx.net", "gmx.at", "gmx.ch", "web.de",
  "aon.at", "chello.at", "t-online.de", "freenet.de", "proton.me", "protonmail.com", "mail.com", "yandex.com",
]);

export function isFreeMailDomain(domain: string | undefined): boolean {
  return domain !== undefined && FREE_MAIL_DOMAINS.has(domain.toLowerCase());
}

/**
 * Pictures for mail addresses and domains: contact photos (from the mail sync) and domain logos (fetched by
 * the backend from the web). `domains` are the ones whose logos may be needed; they are resolved in one request.
 */
export function useSenderPictures(domains: readonly string[]) {
  const unique = [...new Set(domains.map((domain) => domain.toLowerCase()))];
  const { data: logoDomains } = useListSenderLogos({ domain: unique }, { query: { enabled: unique.length > 0, staleTime: Infinity } });
  const { data: contactPhotos } = useListContactPhotos({ query: { staleTime: 5 * 60_000 } });
  const photoByAddress = new Map((contactPhotos ?? []).map((photo) => [photo.emailAddress, photo.id]));
  const withLogo = new Set(logoDomains ?? []);

  return {
    contactPhoto(address: string | undefined): string | undefined {
      const id = address ? photoByAddress.get(address.toLowerCase()) : undefined;
      return id !== undefined ? `/api/senders/contact-photos/${id}` : undefined;
    },
    logo(domain: string | undefined): string | undefined {
      return domain && withLogo.has(domain.toLowerCase()) ? `/api/senders/logos/${domain.toLowerCase()}` : undefined;
    },
  };
}
