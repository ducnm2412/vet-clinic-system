import { cn } from "@/lib/utils/cn";

/** Dấu chân vẽ bằng CSS: 1 đệm lớn + 4 ngón tròn. */
export function Paw({ className }: { className?: string }) {
  return (
    <span aria-hidden className={cn("pointer-events-none absolute block", className)}>
      <span className="absolute bottom-0 left-1/2 h-[48%] w-[58%] -translate-x-1/2 rounded-[50%_50%_45%_45%] bg-current" />
      <span className="absolute left-0 top-[30%] size-[24%] rounded-full bg-current" />
      <span className="absolute left-[22%] top-0 size-[26%] rounded-full bg-current" />
      <span className="absolute right-[22%] top-0 size-[26%] rounded-full bg-current" />
      <span className="absolute right-0 top-[30%] size-[24%] rounded-full bg-current" />
    </span>
  );
}

/** Chiếc lá vẽ bằng CSS: hai góc đối bo tròn + gân giữa. */
export function Leaf({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        "pointer-events-none absolute z-10 block rounded-[100%_0] shadow-sm after:absolute after:left-1/2 after:top-[10%] after:h-[80%] after:w-px after:bg-white/40",
        className,
      )}
    />
  );
}
