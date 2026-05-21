"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";
import type { CustomerDto } from "../../api";
import { useCustomers } from "../../model";

interface Props {
  onChange: (customer: CustomerDto | null) => void;
  error?: string;
}

export function CustomerPicker({ onChange, error }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const { data, isLoading } = useCustomers({ size: 1000 });
  const customers = useMemo(() => data?.content ?? [], [data?.content]);

  const filtered = useMemo(() => {
    if (!query.trim()) return customers;
    const q = query.trim().toLowerCase();
    return customers.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.phone.toLowerCase().includes(q),
    );
  }, [customers, query]);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  function handleSelect(c: CustomerDto) {
    onChange(c);
    setQuery("");
    setOpen(false);
  }

  return (
    <div ref={containerRef} className="relative flex flex-col gap-1">
      <div className="relative">
        <Search
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none"
        />
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder="고객명 또는 전화번호로 검색…"
          className={`w-full pl-9 pr-3 py-2.5 rounded-lg border ${
            error ? "border-status-error" : "border-outline-variant"
          } bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container`}
        />
      </div>
      {error && <span className="text-xs text-status-error">{error}</span>}

      {open && (
        <div className="absolute top-full left-0 right-0 mt-1 z-10 bg-surface-container-lowest border border-outline-variant rounded-lg shadow-card max-h-64 overflow-y-auto">
          {isLoading && (
            <div className="px-3 py-2 text-sm text-on-surface-variant">로딩 중…</div>
          )}
          {!isLoading && customers.length === 0 && (
            <div className="px-3 py-3 text-sm text-on-surface-variant">
              등록된 고객이 없습니다. 먼저{" "}
              <Link href="/customers" className="text-primary hover:underline">
                고객 관리
              </Link>
              에서 등록하세요.
            </div>
          )}
          {!isLoading && customers.length > 0 && filtered.length === 0 && (
            <div className="px-3 py-3 text-sm text-on-surface-variant">
              검색 결과가 없습니다.{" "}
              <Link href="/customers" className="text-primary hover:underline">
                고객 관리
              </Link>
              에서 등록 후 다시 시도하세요.
            </div>
          )}
          {!isLoading &&
            filtered.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => handleSelect(c)}
                className="block w-full text-left px-3 py-2 hover:bg-surface-container-low transition-colors"
              >
                <div className="text-sm font-medium text-on-surface">{c.name}</div>
                <div className="text-xs text-on-surface-variant">{c.phone}</div>
              </button>
            ))}
        </div>
      )}
    </div>
  );
}
