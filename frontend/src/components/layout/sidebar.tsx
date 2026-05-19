"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileSearch,
  LayoutDashboard,
  Receipt,
  X,
} from "lucide-react";

interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  disabled?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
  { label: "설계 관리", href: "/proposals", icon: ClipboardList },
  { label: "심사 현황", href: "/underwriting", icon: FileSearch },
  { label: "청구 관리", href: "/claims", icon: Receipt },
];

interface SidebarProps {
  desktopCollapsed: boolean;
  mobileDrawerOpen: boolean;
  onDesktopToggle: () => void;
  onMobileClose: () => void;
}

export function Sidebar({
  desktopCollapsed,
  mobileDrawerOpen,
  onDesktopToggle,
  onMobileClose,
}: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside
      className={[
        "flex flex-col bg-primary-container overflow-hidden",
        // 모바일: fixed drawer
        "fixed inset-y-0 left-0 z-50 w-[256px]",
        "transition-transform duration-200",
        mobileDrawerOpen ? "translate-x-0" : "-translate-x-full",
        // 데스크탑: static, translate 초기화, 너비 토글
        "lg:static lg:z-auto lg:translate-x-0",
        "lg:transition-[width] lg:duration-200",
        desktopCollapsed ? "lg:w-[80px]" : "lg:w-[256px]",
      ].join(" ")}
    >
      {/* 헤더 */}
      <div className="flex items-center h-16 px-4 border-b border-white/10 gap-2 shrink-0">
        {/* 모바일: X 버튼 */}
        <button
          type="button"
          onClick={onMobileClose}
          className="p-2 rounded-lg text-on-primary hover:bg-white/10 transition-colors shrink-0 lg:hidden"
          aria-label="메뉴 닫기"
        >
          <X size={20} />
        </button>

        {/* 로고 텍스트 (데스크탑 펼침 또는 모바일) */}
        {(!desktopCollapsed || mobileDrawerOpen) && (
          <span className="flex-1 text-on-primary font-bold text-base tracking-tight truncate hidden lg:block">
            AgentSupport
          </span>
        )}
        <span className="flex-1 text-on-primary font-bold text-base tracking-tight truncate lg:hidden">
          AgentSupport
        </span>

        {/* 데스크탑: 접힘/펼침 버튼 */}
        <button
          type="button"
          onClick={onDesktopToggle}
          className={[
            "p-2 rounded-lg text-on-primary hover:bg-white/10 transition-colors shrink-0 hidden lg:flex",
            desktopCollapsed && "mx-auto",
          ].join(" ")}
          aria-label={desktopCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
        >
          {desktopCollapsed ? (
            <ChevronRight size={20} />
          ) : (
            <ChevronLeft size={20} />
          )}
        </button>
      </div>

      {/* 네비게이션 */}
      <nav className="flex flex-col gap-1 p-2 flex-1">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          const collapsed = desktopCollapsed && !mobileDrawerOpen;
          const base = [
            "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors",
            collapsed && "lg:justify-center",
          ].join(" ");

          if (item.disabled) {
            return (
              <div
                key={item.href}
                className={`${base} opacity-40 cursor-not-allowed`}
                title={collapsed ? `${item.label} (준비 중)` : undefined}
              >
                <Icon size={20} className="text-on-primary shrink-0" />
                {!collapsed && (
                  <span className="text-sm text-on-primary truncate">
                    {item.label}
                    <span className="ml-1 text-xs opacity-70">준비 중</span>
                  </span>
                )}
              </div>
            );
          }

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onMobileClose}
              className={[
                base,
                isActive
                  ? "bg-white/20 text-on-primary"
                  : "text-on-primary hover:bg-white/10",
              ].join(" ")}
              title={collapsed ? item.label : undefined}
            >
              <Icon size={20} className="shrink-0" />
              {!collapsed && (
                <span className="text-sm font-medium truncate">{item.label}</span>
              )}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
