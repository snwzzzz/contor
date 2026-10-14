import { CircleAlert } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

/** Server messages may combine several problems with "; " (password rules, record validation). */
const SEPARATOR = "; ";

/** Form-level error. Renders nothing without a message; lists combined server messages. */
export function ErrorMessage({ message }: { message: string | undefined }) {
  if (!message) return null;
  const parts = message.split(SEPARATOR);
  return (
    <Alert variant="destructive" role="alert">
      <CircleAlert aria-hidden="true" />
      <AlertDescription>
        {parts.length > 1 ? (
          <ul className="list-disc pl-4">
            {parts.map((part) => (
              <li key={part}>{part}</li>
            ))}
          </ul>
        ) : (
          message
        )}
      </AlertDescription>
    </Alert>
  );
}
