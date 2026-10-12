import { HTMLAttributes } from "react";
import { cx } from "../../lib/utils";

type AlertVariant = "info" | "success" | "warning" | "error";

interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  variant?: AlertVariant;
}

const alertVariants: Record<AlertVariant, string> = {
  info: "border-brand-200 bg-brand-50 text-brand-900",
  success: "border-emerald-200 bg-emerald-50 text-emerald-900",
  warning: "border-amber-200 bg-amber-50 text-amber-900",
  error: "border-red-200 bg-red-50 text-red-900",
};

const alertIcons: Record<AlertVariant, string> = {
  info: "ℹ️",
  success: "✅",
  warning: "⚠️",
  error: "⛔",
};

export function Alert({ variant = "info", className, children, ...props }: AlertProps) {
  return (
    <div
      role="alert"
      className={cx("flex items-start gap-2.5 rounded-lg border p-4 text-sm", alertVariants[variant], className)}
      {...props}
    >
      <span aria-hidden="true">{alertIcons[variant]}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
