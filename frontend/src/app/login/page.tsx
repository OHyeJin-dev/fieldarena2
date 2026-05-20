"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Lock, User } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useLoginMutation } from "@/features/auth/login";
import { ApiError } from "@/shared/api";

const schema = z.object({
  username: z.string().min(1, "아이디를 입력해주세요"),
  password: z.string().min(1, "비밀번호를 입력해주세요"),
});

type LoginForm = z.infer<typeof schema>;

const INPUT_CLASS =
  "w-full h-14 pl-12 pr-4 bg-surface-container-lowest border border-outline-variant rounded-lg " +
  "text-base text-on-surface placeholder:text-on-surface-variant/50 " +
  "outline-none focus:border-primary-container transition-colors";

export default function LoginPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const loginMutation = useLoginMutation();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) });

  function onSubmit(data: LoginForm) {
    setServerError(null);
    loginMutation.mutate(data, {
      onSuccess: () => router.push("/dashboard"),
      onError: (err) => {
        if (err instanceof ApiError && err.status === 401) {
          const code = (err.body as { code?: string } | null)?.code;
          if (code === "PENDING_APPROVAL") {
            router.push("/pending");
            return;
          }
          if (code === "ACCOUNT_REJECTED") {
            setServerError("가입이 거절된 계정입니다. 관리자에게 문의해주세요");
            return;
          }
          setServerError("아이디 또는 비밀번호가 일치하지 않습니다");
        } else {
          setServerError("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
        }
      },
    });
  }

  return (
    <main className="min-h-screen flex flex-col lg:flex-row w-full overflow-hidden">

      {/* ── 왼쪽: 비주얼 패널 ── */}
      <section className="hidden lg:flex lg:flex-1 bg-primary-container relative items-center justify-center overflow-hidden">
        {/* 배경 이미지 */}
        <div className="absolute inset-0 opacity-40">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuC-GgpAS0PSc_cNFyAi8_KGi-MpxzHZS7VtI7rN2pUcHa6VwmVyjyAyiQMAlWCHTtKWWegU6PR4r3UR0znKa-JDjndMdFxtB_j3V_eRX3z36gRNcp_GUrxD2McNsbKgPHoLEt5c81KdgfaCKH3xA6WcIX3ASrxnHdygn9DsVsRAnYvcH53GSQ0Rj_U9gT9E69cQjX21LL9Uuy1v99K9fzrlaa-nk2sDQiQ3v0sA5OmJ6I48KgJdYumlbeoO5Oa37tRNvqivaRtrQOfs"
            alt=""
            className="w-full h-full object-cover"
          />
        </div>

        {/* 텍스트 */}
        <div className="relative z-10 px-16 max-w-xl text-center">
          <h1 className="text-4xl font-bold text-on-primary leading-tight tracking-tight mb-6">
            보장된 미래를 설계하는<br />전문가들의 파트너
          </h1>
          <p className="text-lg text-primary-fixed-dim leading-relaxed">
            AgentSupport는 최신 보험 트렌드와 데이터 분석을 통해<br />
            보험설계사분들의 성장을 전폭 지원합니다.
          </p>
        </div>

        {/* 하단 그라데이션 */}
        <div className="absolute bottom-0 left-0 w-full h-32 bg-gradient-to-t from-primary-container to-transparent" />
      </section>

      {/* ── 오른쪽: 로그인 폼 ── */}
      <section className="w-full lg:w-[580px] flex items-center justify-center bg-surface px-6 py-16">
        <div className="w-full max-w-[480px] flex flex-col items-center">

          {/* 브랜드 */}
          <div className="mb-12 text-center">
            <span className="block text-2xl font-bold text-primary tracking-tight">AgentSupport</span>
            <span className="block text-sm font-semibold text-on-surface-variant tracking-widest mt-2">
              보험설계사 지원 플랫폼
            </span>
          </div>

          {/* 카드 */}
          <div className="w-full bg-surface-container-lowest rounded-2xl p-10 shadow-card">

            {serverError && (
              <div className="mb-6 px-4 py-3 rounded-xl bg-status-error-container text-sm text-status-error">
                {serverError}
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">

              {/* 아이디 */}
              <div className="flex flex-col gap-2">
                <label htmlFor="login-id" className="text-sm font-semibold text-on-surface-variant px-1">
                  아이디
                </label>
                <div className="relative">
                  <User size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-outline pointer-events-none" />
                  <input
                    id="login-id"
                    type="text"
                    placeholder="아이디를 입력해주세요"
                    autoComplete="username"
                    className={[INPUT_CLASS, errors.username ? "border-status-error" : ""].join(" ")}
                    {...register("username")}
                  />
                </div>
                {errors.username && (
                  <p className="text-xs text-status-error px-1">{errors.username.message}</p>
                )}
              </div>

              {/* 비밀번호 */}
              <div className="flex flex-col gap-2">
                <label htmlFor="login-pw" className="text-sm font-semibold text-on-surface-variant px-1">
                  비밀번호
                </label>
                <div className="relative">
                  <Lock size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-outline pointer-events-none" />
                  <input
                    id="login-pw"
                    type="password"
                    placeholder="비밀번호를 입력해주세요"
                    autoComplete="current-password"
                    className={[INPUT_CLASS, errors.password ? "border-status-error" : ""].join(" ")}
                    {...register("password")}
                  />
                </div>
                {errors.password && (
                  <p className="text-xs text-status-error px-1">{errors.password.message}</p>
                )}
              </div>

              {/* 옵션 행 */}
              <div className="flex items-center justify-between">
                <label className="flex items-center gap-2 cursor-pointer group">
                  <input
                    type="checkbox"
                    className="w-4 h-4 rounded border-outline-variant accent-primary cursor-pointer"
                  />
                  <span className="text-sm text-on-surface-variant group-hover:text-on-surface transition-colors">
                    로그인 상태 유지
                  </span>
                </label>
                <button type="button" className="text-sm text-on-surface-variant hover:text-primary transition-colors">
                  아이디/비밀번호 찾기
                </button>
              </div>

              {/* 로그인 버튼 */}
              <button
                type="submit"
                disabled={loginMutation.isPending}
                className="w-full h-14 bg-primary-container text-on-primary text-sm font-semibold rounded-xl hover:opacity-90 active:scale-[0.99] transition-all disabled:opacity-50 shadow-md"
              >
                {loginMutation.isPending ? "처리 중…" : "로그인"}
              </button>
            </form>

            {/* 구분선 */}
            <div className="relative my-8 flex items-center">
              <div className="flex-grow border-t border-outline-variant" />
              <span className="flex-shrink mx-4 text-xs text-outline uppercase tracking-wider">
                또는 간편 로그인
              </span>
              <div className="flex-grow border-t border-outline-variant" />
            </div>

            {/* 소셜 로그인 */}
            <div className="flex flex-col gap-4">
              <button
                type="button"
                className="w-full h-[52px] bg-[#FEE500] text-black text-sm font-semibold rounded-xl flex items-center justify-center gap-2 hover:opacity-95 transition-all"
              >
                <svg width="18" height="18" viewBox="0 0 18 18" fill="currentColor" aria-hidden="true">
                  <path d="M9 1.5C4.858 1.5 1.5 4.19 1.5 7.5c0 2.1 1.26 3.96 3.19 5.07L3.75 16.5l4.02-2.64c.4.06.81.09 1.23.09 4.142 0 7.5-2.69 7.5-6S13.142 1.5 9 1.5z" />
                </svg>
                카카오로 시작하기
              </button>
              <button
                type="button"
                className="w-full h-[52px] bg-[#00C73C] text-white text-sm font-semibold rounded-xl flex items-center justify-center gap-2 hover:opacity-95 transition-all"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727z" />
                </svg>
                네이버로 시작하기
              </button>
            </div>
          </div>

          {/* 하단 링크 */}
          <footer className="mt-8 text-center flex flex-col gap-2">
            <div className="flex items-center justify-center gap-5 text-sm text-on-surface-variant">
              <Link href="/register" className="hover:text-primary transition-colors">회원가입</Link>
              <span className="w-1 h-1 bg-outline-variant rounded-full inline-block" />
              <a href="#" className="hover:text-primary transition-colors">이용약관</a>
              <span className="w-1 h-1 bg-outline-variant rounded-full inline-block" />
              <a href="#" className="hover:text-primary transition-colors">개인정보처리방침</a>
            </div>
            <p className="text-xs text-outline-variant">
              © 2024 Insurance Agent Support Platform. All rights reserved.
            </p>
          </footer>

        </div>
      </section>

    </main>
  );
}
