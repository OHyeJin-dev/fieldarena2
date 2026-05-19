import Link from "next/link";
import { Clock } from "lucide-react";

export default function PendingPage() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-surface px-6">
      <div className="max-w-md w-full text-center flex flex-col items-center gap-6">
        <div className="w-20 h-20 rounded-full bg-primary-container/20 flex items-center justify-center">
          <Clock size={40} className="text-primary-container" />
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold text-on-surface">승인 대기 중입니다</h1>
          <p className="text-on-surface-variant leading-relaxed">
            가입 신청이 완료되었습니다.
            <br />
            관리자 검토 후 승인되면 로그인하실 수 있습니다.
          </p>
        </div>

        <Link
          href="/login"
          className="text-sm text-primary hover:underline font-medium"
        >
          로그인 페이지로 돌아가기
        </Link>
      </div>
    </main>
  );
}
