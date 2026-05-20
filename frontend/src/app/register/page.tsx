"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRegisterMutation } from "@/features/auth/queries";
import { ApiError } from "@/shared/api";

const schema = z.object({
  id: z.string().min(1, "아이디를 입력해주세요").max(50, "아이디는 50자 이하여야 합니다"),
  password: z.string().min(8, "비밀번호는 8자 이상이어야 합니다"),
  name: z.string().min(1, "이름을 입력해주세요"),
  phone: z.string().min(1, "연락처를 입력해주세요"),
  gaName: z.string().min(1, "소속 GA를 입력해주세요"),
  email: z.string().min(1, "이메일을 입력해주세요").email("이메일 형식이 올바르지 않습니다"),
});

type RegisterForm = z.infer<typeof schema>;

const INPUT_CLASS =
  "w-full h-12 px-4 bg-surface-container-lowest border border-outline-variant rounded-lg " +
  "text-base text-on-surface placeholder:text-on-surface-variant/50 " +
  "outline-none focus:border-primary-container transition-colors";

export default function RegisterPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const registerMutation = useRegisterMutation();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(schema) });

  function onSubmit(data: RegisterForm) {
    setServerError(null);
    registerMutation.mutate(data, {
      onSuccess: () => router.push("/pending"),
      onError: (err) => {
        if (err instanceof ApiError && err.status === 409) {
          const code = (err.body as { message?: string } | null)?.message;
          if (code === "ID_TAKEN") {
            setServerError("이미 사용 중인 아이디입니다");
          } else if (code === "EMAIL_TAKEN") {
            setServerError("이미 등록된 이메일입니다");
          } else {
            setServerError("이미 사용 중인 아이디 또는 이메일입니다");
          }
        } else {
          setServerError("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
        }
      },
    });
  }

  const fields: {
    name: keyof RegisterForm;
    label: string;
    type: string;
    placeholder: string;
    autoComplete?: string;
  }[] = [
    { name: "id", label: "아이디", type: "text", placeholder: "아이디를 입력해주세요", autoComplete: "username" },
    { name: "password", label: "비밀번호", type: "password", placeholder: "8자 이상", autoComplete: "new-password" },
    { name: "name", label: "이름", type: "text", placeholder: "이름을 입력해주세요" },
    { name: "phone", label: "연락처", type: "tel", placeholder: "010-0000-0000" },
    { name: "gaName", label: "소속 GA", type: "text", placeholder: "소속 GA명을 입력해주세요" },
    { name: "email", label: "이메일", type: "email", placeholder: "example@email.com", autoComplete: "email" },
  ];

  return (
    <main className="min-h-screen flex items-center justify-center bg-surface px-6 py-16">
      <div className="w-full max-w-[480px] flex flex-col items-center">
        <div className="mb-10 text-center">
          <span className="block text-2xl font-bold text-primary tracking-tight">AgentSupport</span>
          <span className="block text-sm font-semibold text-on-surface-variant tracking-widest mt-2">
            가입 신청
          </span>
        </div>

        <div className="w-full bg-surface-container-lowest rounded-2xl p-10 shadow-card">
          {serverError && (
            <div className="mb-6 px-4 py-3 rounded-xl bg-status-error-container text-sm text-status-error">
              {serverError}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
            {fields.map((f) => (
              <div key={f.name} className="flex flex-col gap-1.5">
                <label className="text-sm font-semibold text-on-surface-variant px-1">
                  {f.label}
                </label>
                <input
                  type={f.type}
                  placeholder={f.placeholder}
                  autoComplete={f.autoComplete}
                  className={[INPUT_CLASS, errors[f.name] ? "border-status-error" : ""].join(" ")}
                  {...register(f.name)}
                />
                {errors[f.name] && (
                  <p className="text-xs text-status-error px-1">{errors[f.name]?.message}</p>
                )}
              </div>
            ))}

            <button
              type="submit"
              disabled={registerMutation.isPending}
              className="mt-2 w-full h-12 bg-primary-container text-on-primary text-sm font-semibold rounded-xl hover:opacity-90 active:scale-[0.99] transition-all disabled:opacity-50"
            >
              {registerMutation.isPending ? "처리 중…" : "가입 신청"}
            </button>
          </form>
        </div>

        <footer className="mt-6 text-sm text-on-surface-variant">
          이미 계정이 있으신가요?{" "}
          <Link href="/login" className="text-primary hover:underline font-medium">
            로그인
          </Link>
        </footer>
      </div>
    </main>
  );
}
