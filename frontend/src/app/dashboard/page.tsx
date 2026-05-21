"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ClipboardList,
  FileSearch,
  Plus,
  Receipt,
  TrendingUp,
  Users,
} from "lucide-react";
import { useDashboardSummary } from "@/entities/dashboard";
import { useMe } from "@/entities/session";
import { useAnalysisSummary, useRecentAnalyses, RiskBadge } from "@/entities/health-analysis";
import { ProposalFormModal } from "@/features/proposal/create";

const MONTHLY_TARGET = 10;

const TABS_DEFAULT = ["공지사항", "최근 설계", "일정"] as const;
const TABS_AGENT2 = ["공지사항", "일정"] as const;

const NOTICES = [
  { id: 1, title: "실손보험 청구 서류 제출 기준 변경 안내", date: "2026-05-15", isNew: true },
  { id: 2, title: "5월 GA 월례 교육 일정 공지", date: "2026-05-10", isNew: true },
  { id: 3, title: "암보험 심사 기준 강화 안내 (DB손보, 삼성생명)", date: "2026-05-02", isNew: false },
  { id: 4, title: "2026년 상반기 수수료 정산 일정 및 기준 업데이트", date: "2026-04-25", isNew: false },
  { id: 5, title: "변액보험 판매 자격 갱신 대상자 안내", date: "2026-04-18", isNew: false },
];

const SCHEDULE_TYPE_CLASS: Record<string, string> = {
  상담: "bg-status-info-container text-status-info",
  서류: "bg-status-warning-container text-status-warning",
  교육: "bg-status-success-container text-status-success",
  청구: "bg-status-error-container text-status-error",
  세미나: "bg-surface-container-high text-on-surface-variant",
};

const SCHEDULES = [
  { id: 1, title: "류○○ 고객 설계 상담", date: "2026-05-20", time: "14:00", type: "상담" },
  { id: 2, title: "삼성생명 심사 서류 제출", date: "2026-05-21", time: "09:00", type: "서류" },
  { id: 3, title: "GA 월례 교육", date: "2026-05-22", time: "13:00", type: "교육" },
  { id: 4, title: "이○○ 청구 서류 확인 미팅", date: "2026-05-26", time: "10:30", type: "청구" },
  { id: 5, title: "DB손보 신상품 설계 세미나", date: "2026-05-27", time: "14:00", type: "세미나" },
];

const PROPOSAL_STATUS_CLASS: Record<string, string> = {
  "작성 중": "bg-status-info-container text-status-info",
  "설계 완료": "bg-status-success-container text-status-success",
  "취소": "bg-surface-container-high text-on-surface-variant",
};

function AnalysisSummaryCard({
  label,
  value,
  colorClass = "text-on-surface",
}: {
  label: string;
  value: number;
  colorClass?: string;
}) {
  return (
    <div className="bg-surface-container-lowest rounded-2xl p-4 shadow-card">
      <div className="text-xs text-on-surface-variant">{label}</div>
      <div className={`text-2xl font-bold ${colorClass}`}>{value}명</div>
    </div>
  );
}

export default function DashboardPage() {
  const { data, isLoading } = useDashboardSummary();
  const { data: me } = useMe();
  const isAgent2 = me?.role === "AGENT2";
  const tabs: readonly string[] = isAgent2 ? TABS_AGENT2 : TABS_DEFAULT;
  const [activeTab, setActiveTab] = useState<string>("공지사항");
  const [showModal, setShowModal] = useState(false);

  const { data: analysisSummary } = useAnalysisSummary();
  const { data: recentAnalyses } = useRecentAnalyses(5);

  const activeProposals = data?.activeProposals ?? 0;
  const underwritingPending = data?.underwritingPending ?? 0;
  const claimsInProgress = data?.claimsInProgress ?? 0;
  const monthlyProposals = data?.monthlyProposals ?? 0;
  const myCustomers = data?.myCustomers ?? 0;
  const monthlyClaims = data?.monthlyClaims ?? 0;
  const progressPct = Math.min(100, Math.round((monthlyProposals / MONTHLY_TARGET) * 100));

  return (
    <div className="p-6 max-w-300 mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-on-surface">대시보드</h1>

      {/* 요약 카드 3개 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {isAgent2 ? (
          <>
            <SummaryCard
              label="본인 고객"
              value={myCustomers}
              unit="명"
              caption="등록한 고객"
              Icon={Users}
              iconClass="bg-status-info-container text-status-info"
              borderClass="border-status-info"
              isLoading={isLoading}
            />
            <SummaryCard
              label="청구 처리 중"
              value={claimsInProgress}
              unit="건"
              caption="접수 + 심사 중"
              Icon={AlertCircle}
              iconClass="bg-status-error-container text-status-error"
              borderClass="border-status-error"
              isLoading={isLoading}
            />
            <SummaryCard
              label="이번 달 청구 등록"
              value={monthlyClaims}
              unit="건"
              caption="이번 달 등록"
              Icon={Receipt}
              iconClass="bg-status-success-container text-status-success"
              borderClass="border-status-success"
              isLoading={isLoading}
            />
          </>
        ) : (
          <>
            <SummaryCard
              label="진행 중인 설계"
              value={activeProposals}
              unit="건"
              caption="작성 중 + 설계 완료"
              Icon={ClipboardList}
              iconClass="bg-status-info-container text-status-info"
              borderClass="border-status-info"
              isLoading={isLoading}
            />
            <SummaryCard
              label="심사 대기 건수"
              value={underwritingPending}
              unit="건"
              caption="심사 중 + 서류 보완"
              Icon={FileSearch}
              iconClass="bg-status-warning-container text-status-warning"
              borderClass="border-status-warning"
              isLoading={isLoading}
            />
            <SummaryCard
              label="청구 처리 중"
              value={claimsInProgress}
              unit="건"
              caption="접수 + 심사 중"
              Icon={AlertCircle}
              iconClass="bg-status-error-container text-status-error"
              borderClass="border-status-error"
              isLoading={isLoading}
            />
          </>
        )}
      </div>

      {/* 2단 레이아웃 */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* 빠른 작업 */}
          <div className="bg-surface-container-lowest rounded-2xl shadow-card p-6">
            <h2 className="text-base font-semibold text-on-surface mb-4">빠른 작업</h2>
            <div className="grid grid-cols-3 gap-3">
              {isAgent2 ? (
                <>
                  <Link
                    href="/customers"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl bg-primary-container text-on-primary-container text-xs font-medium hover:opacity-90 transition-opacity"
                  >
                    <Plus size={18} /> 새 고객 등록
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Plus size={18} /> 청구 등록
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Receipt size={18} /> 청구 관리
                  </Link>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => setShowModal(true)}
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl bg-primary-container text-on-primary-container text-xs font-medium hover:opacity-90 transition-opacity"
                  >
                    <Plus size={18} />
                    새 설계 작성
                  </button>
                  <Link
                    href="/underwriting"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <FileSearch size={18} />
                    심사 현황
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Receipt size={18} />
                    청구 관리
                  </Link>
                </>
              )}
            </div>
          </div>

          {/* 업무 현황 */}
          <div className="bg-surface-container-lowest rounded-2xl shadow-card p-6">
            <h2 className="text-base font-semibold text-on-surface mb-4">업무 현황</h2>
            <div className="flex flex-col">
              {(isAgent2
                ? [
                    {
                      label: "청구 처리 중",
                      value: claimsInProgress,
                      iconClass: "bg-status-error-container text-status-error",
                      badgeClass: "bg-status-error-container text-status-error",
                      Icon: AlertCircle,
                    },
                  ]
                : [
                    {
                      label: "설계 진행 중",
                      value: activeProposals,
                      iconClass: "bg-status-info-container text-status-info",
                      badgeClass: "bg-status-info-container text-status-info",
                      Icon: ClipboardList,
                    },
                    {
                      label: "심사 대기",
                      value: underwritingPending,
                      iconClass: "bg-status-warning-container text-status-warning",
                      badgeClass: "bg-status-warning-container text-status-warning",
                      Icon: FileSearch,
                    },
                    {
                      label: "청구 처리 중",
                      value: claimsInProgress,
                      iconClass: "bg-status-error-container text-status-error",
                      badgeClass: "bg-status-error-container text-status-error",
                      Icon: AlertCircle,
                    },
                  ]
              ).map(({ label, value, iconClass, badgeClass, Icon }) => (
                <div
                  key={label}
                  className="flex items-center justify-between py-3 border-b border-outline-variant last:border-0"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${iconClass}`}
                    >
                      <Icon size={16} />
                    </div>
                    <span className="text-sm text-on-surface">{label}</span>
                  </div>
                  {isLoading ? (
                    <div className="h-6 w-10 bg-surface-container rounded-full animate-pulse" />
                  ) : (
                    <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${badgeClass}`}>
                      {value}건
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 오른쪽: 탭 패널 */}
        <div className="lg:col-span-5">
          <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden h-full flex flex-col">
            <div className="flex border-b border-outline-variant shrink-0">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setActiveTab(tab)}
                  className={[
                    "flex-1 py-3 text-sm font-medium border-b-2 -mb-px transition-colors",
                    activeTab === tab
                      ? "border-primary text-on-surface"
                      : "border-transparent text-on-surface-variant hover:text-on-surface",
                  ].join(" ")}
                >
                  {tab}
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-y-auto p-5">
              {activeTab === "공지사항" && (
                <div className="flex flex-col">
                  {NOTICES.map((n, i) => (
                    <div
                      key={n.id}
                      className={[
                        "flex items-start gap-3 py-3 cursor-default hover:bg-surface-container-low -mx-2 px-2 rounded-lg transition-colors",
                        i < NOTICES.length - 1 ? "border-b border-outline-variant" : "",
                      ].join(" ")}
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-on-surface leading-snug line-clamp-2">{n.title}</p>
                        <p className="text-xs text-on-surface-variant mt-1">{n.date}</p>
                      </div>
                      {n.isNew && (
                        <span className="shrink-0 text-xs font-bold text-status-error bg-status-error-container px-1.5 py-0.5 rounded mt-0.5">
                          N
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {activeTab === "최근 설계" && !isAgent2 && (
                <div className="flex flex-col gap-1">
                  {isLoading &&
                    Array.from({ length: 5 }).map((_, i) => (
                      <div key={i} className="flex items-center justify-between p-3 rounded-xl">
                        <div className="flex flex-col gap-1.5 flex-1">
                          <div className="h-3 w-20 bg-surface-container rounded animate-pulse" />
                          <div className="h-3.5 w-32 bg-surface-container rounded animate-pulse" />
                        </div>
                        <div className="h-5 w-14 bg-surface-container rounded-full animate-pulse shrink-0" />
                      </div>
                    ))}
                  {!isLoading && (data?.recentProposals ?? []).length === 0 && (
                    <p className="text-sm text-on-surface-variant text-center py-8">
                      최근 설계 내역이 없습니다.
                    </p>
                  )}
                  {!isLoading &&
                    (data?.recentProposals ?? []).map((p) => (
                      <div
                        key={p.id}
                        className="flex items-center justify-between p-3 rounded-xl hover:bg-surface-container-low transition-colors"
                      >
                        <div className="flex flex-col gap-0.5 min-w-0 flex-1 mr-3">
                          <span className="text-xs text-on-surface-variant">{p.insurerName}</span>
                          <span className="text-sm text-on-surface truncate">
                            {p.customerName} · {p.productName}
                          </span>
                        </div>
                        <span
                          className={`shrink-0 text-xs font-medium px-2 py-1 rounded-full ${
                            PROPOSAL_STATUS_CLASS[p.status] ??
                            "bg-surface-container text-on-surface-variant"
                          }`}
                        >
                          {p.status}
                        </span>
                      </div>
                    ))}
                </div>
              )}

              {activeTab === "일정" && (
                <div className="flex flex-col">
                  {SCHEDULES.map((s, i) => (
                    <div
                      key={s.id}
                      className={[
                        "flex items-center gap-3 py-3",
                        i < SCHEDULES.length - 1 ? "border-b border-outline-variant" : "",
                      ].join(" ")}
                    >
                      <div className="shrink-0 w-10 text-center">
                        <p className="text-xs text-on-surface-variant leading-tight">
                          {s.date.slice(5).replace("-", "/")}
                        </p>
                        <p className="text-base font-bold text-on-surface leading-tight">
                          {new Date(s.date).getDate()}
                        </p>
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-on-surface truncate">{s.title}</p>
                        <p className="text-xs text-on-surface-variant">{s.time}</p>
                      </div>
                      <span
                        className={`shrink-0 text-xs font-medium px-2 py-1 rounded-full ${
                          SCHEDULE_TYPE_CLASS[s.type] ??
                          "bg-surface-container text-on-surface-variant"
                        }`}
                      >
                        {s.type}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {showModal && <ProposalFormModal onClose={() => setShowModal(false)} />}

      {/* 이번 달 설계 작성 현황 — AGENT2는 숨김 */}
      {!isAgent2 && (
        <div className="bg-primary-container rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp size={20} className="text-on-primary-container" />
            <h2 className="text-base font-semibold text-on-primary-container">
              이번 달 설계 작성 현황
            </h2>
          </div>
          <div className="flex items-end justify-between mb-3">
            {isLoading ? (
              <div className="h-7 w-36 bg-white/20 rounded animate-pulse" />
            ) : (
              <p className="text-on-primary-container">
                <span className="text-2xl font-bold">{monthlyProposals}</span>
                <span className="text-sm ml-1 text-on-primary-container">
                  / 목표 {MONTHLY_TARGET}건
                </span>
              </p>
            )}
            <span className="text-sm font-semibold text-on-primary-container">
              {isLoading ? "…" : `${progressPct}%`}
            </span>
          </div>
          <progress
            className="growth-progress"
            max={MONTHLY_TARGET}
            value={isLoading ? 0 : monthlyProposals}
          />
        </div>
      )}

      {/* 건강 분석 현황 — AGENT2는 숨김 */}
      {!isAgent2 && (
        <>
          <section className="flex flex-col gap-3">
            <h2 className="text-lg font-semibold text-on-surface">건강 분석 현황</h2>
            <div className="grid grid-cols-4 gap-3">
              <AnalysisSummaryCard label="분석 완료" value={analysisSummary?.total ?? 0} />
              <AnalysisSummaryCard label="정상" value={analysisSummary?.normal ?? 0} colorClass="text-status-success" />
              <AnalysisSummaryCard label="주의" value={analysisSummary?.caution ?? 0} colorClass="text-status-warning" />
              <AnalysisSummaryCard label="위험" value={analysisSummary?.risk ?? 0} colorClass="text-status-error" />
            </div>
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-lg font-semibold text-on-surface">최근 분석 5건</h2>
            <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
              {recentAnalyses && recentAnalyses.length === 0 && (
                <div className="px-6 py-8 text-center text-sm text-on-surface-variant">
                  아직 분석된 건강 데이터가 없습니다.
                </div>
              )}
              {recentAnalyses?.map((item) => (
                <Link
                  key={item.id}
                  href={`/underwriting?analysisId=${item.id}`}
                  className="flex items-center justify-between px-6 py-3 hover:bg-surface-container-low border-b border-outline-variant last:border-b-0"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-medium text-on-surface">{item.customerName}</span>
                    <RiskBadge grade={item.riskGrade} />
                  </div>
                  <span className="text-xs text-on-surface-variant">
                    {new Date(item.analyzedAt).toLocaleString("ko-KR")}
                  </span>
                </Link>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

interface SummaryCardProps {
  label: string;
  value: number;
  unit: string;
  caption: string;
  Icon: React.ComponentType<{ size?: number; className?: string }>;
  iconClass: string;
  borderClass: string;
  isLoading: boolean;
}

function SummaryCard({
  label, value, unit, caption, Icon, iconClass, borderClass, isLoading,
}: SummaryCardProps) {
  return (
    <div
      className={`bg-surface-container-lowest rounded-2xl shadow-card p-6 flex items-start justify-between border-l-4 ${borderClass}`}
    >
      <div className="min-w-0">
        <p className="text-sm text-on-surface-variant mb-1">{label}</p>
        {isLoading ? (
          <div className="h-9 w-20 bg-surface-container rounded animate-pulse mt-1" />
        ) : (
          <p className="text-3xl font-bold text-on-surface leading-tight">
            {value}
            <span className="text-base font-normal ml-1 text-on-surface-variant">{unit}</span>
          </p>
        )}
        <p className="text-xs text-on-surface-variant mt-1.5">{caption}</p>
      </div>
      <div className={`w-11 h-11 rounded-full flex items-center justify-center shrink-0 ml-4 ${iconClass}`}>
        <Icon size={20} />
      </div>
    </div>
  );
}
