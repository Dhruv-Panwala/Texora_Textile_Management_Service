type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className = '' }: SkeletonProps) {
  return <span aria-hidden="true" className={`block animate-pulse rounded-2xl bg-stone-200/80 ${className}`} />;
}

export function SkeletonRows({ columns, rows = 6 }: { columns: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }, (_, row) => (
        <tr key={row}>
          {Array.from({ length: columns }, (_, column) => (
            <td key={column}>
              <Skeleton className={column === 0 ? 'h-4 w-24' : 'h-4 w-full max-w-32'} />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

export function DashboardSkeleton() {
  return (
    <>
      <section className="grid grid-cols-1 gap-4 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <article key={index} className="card-surface">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-5 h-9 w-36" />
            <Skeleton className="mt-3 h-4 w-44" />
          </article>
        ))}
      </section>

      <section className="grid grid-cols-1 gap-6 xl:grid-cols-[1.6fr_0.9fr]">
        <article className="card-surface">
          <div className="mb-6 flex items-center justify-between">
            <div>
              <Skeleton className="h-7 w-52" />
              <Skeleton className="mt-2 h-4 w-64" />
            </div>
            <Skeleton className="h-11 w-11" />
          </div>
          <div className="space-y-4">
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="rounded-2xl bg-white/55 p-4">
                <Skeleton className="h-4 w-20" />
                <Skeleton className="mt-3 h-3 w-full" />
                <Skeleton className="mt-2 h-3 w-4/5" />
              </div>
            ))}
          </div>
        </article>
        <article className="card-surface">
          <Skeleton className="h-7 w-44" />
          <Skeleton className="mt-2 h-4 w-56" />
          <div className="mt-5 space-y-4">
            {Array.from({ length: 3 }, (_, index) => <Skeleton key={index} className="h-20 w-full" />)}
          </div>
        </article>
      </section>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }, (_, index) => (
          <article key={index} className="card-surface">
            <Skeleton className="h-4 w-28" />
            <Skeleton className="mt-4 h-7 w-32" />
            <Skeleton className="mt-3 h-4 w-44" />
          </article>
        ))}
      </section>
    </>
  );
}

export function RouteLoading() {
  return (
    <div className="page-shell" aria-busy="true">
      <div className="page-header">
        <div>
          <Skeleton className="h-10 w-48" />
          <Skeleton className="mt-3 h-4 w-72" />
        </div>
        <div className="action-row">
          <Skeleton className="h-12 w-32" />
          <Skeleton className="h-12 w-36" />
        </div>
      </div>
      <section className="table-surface p-6">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="mt-2 h-4 w-72" />
        <div className="mt-6 space-y-4">
          {Array.from({ length: 7 }, (_, index) => (
            <div key={index} className="flex items-center gap-4 rounded-2xl bg-white/55 p-4">
              <Skeleton className="h-4 w-28" />
              <Skeleton className="h-4 flex-1" />
              <Skeleton className="h-4 w-24" />
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
