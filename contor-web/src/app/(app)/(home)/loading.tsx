import { HomeSkeleton } from "@/components/app/home/HomeSkeleton";

/** Shown inside the app shell while the home page's route loads (the top bar stays). */
export default function Loading() {
  return <HomeSkeleton />;
}
