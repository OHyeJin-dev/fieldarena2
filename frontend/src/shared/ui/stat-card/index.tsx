interface StatCardProps {
  label: string;
  value: string | number;
  description?: string;
}

export function StatCard({ label, value, description }: StatCardProps) {
  return (
    <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-card">
      <p className="text-sm text-on-surface-variant mb-2">{label}</p>
      <p className="text-3xl font-bold text-on-surface">{value}</p>
      {description && (
        <p className="text-xs text-on-surface-variant mt-1">{description}</p>
      )}
    </div>
  );
}
