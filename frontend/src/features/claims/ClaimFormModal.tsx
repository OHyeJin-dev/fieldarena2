"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { useCustomers } from "@/features/customers/queries";
import { useCreateClaim } from "./queries";

const schema = z.object({
  customerId: z.string().min(1, "고객을 선택하세요"),
  policyNumber: z.string().min(1, "계약번호를 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  claimType: z.string().min(1, "청구 유형을 입력하세요"),
  claimAmount: z
    .string()
    .min(1, "청구 금액을 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) >= 0, "올바른 금액을 입력하세요"),
  claimDate: z.string().min(1, "청구일을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function ClaimFormModal({ onClose }: Props) {
  const { data: customers, isLoading: loadingCustomers } = useCustomers({ size: 100 });
  const create = useCreateClaim();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    create.mutate(
      {
        customerId: values.customerId,
        policyNumber: values.policyNumber,
        insurerName: values.insurerName,
        claimType: values.claimType,
        claimAmount: Number(values.claimAmount),
        claimDate: values.claimDate,
      },
      { onSuccess: onClose },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">청구 등록</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="px-6 py-5 flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-sm text-on-surface-variant">고객 선택</label>
              <select
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
                disabled={loadingCustomers}
                {...register("customerId")}
              >
                <option value="">{loadingCustomers ? "로딩 중..." : "고객을 선택하세요"}</option>
                {customers?.content.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.phone})
                  </option>
                ))}
              </select>
              {errors.customerId && (
                <p className="text-xs text-status-error">{errors.customerId.message}</p>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="계약번호"
                placeholder="P12345"
                error={errors.policyNumber?.message}
                {...register("policyNumber")}
              />
              <TextField
                label="보험사"
                placeholder="삼성생명"
                error={errors.insurerName?.message}
                {...register("insurerName")}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="청구 유형"
                placeholder="실손"
                error={errors.claimType?.message}
                {...register("claimType")}
              />
              <TextField
                label="청구 금액 (원)"
                type="number"
                min="0"
                placeholder="100000"
                error={errors.claimAmount?.message}
                {...register("claimAmount")}
              />
            </div>
            <TextField
              label="청구일"
              type="date"
              error={errors.claimDate?.message}
              {...register("claimDate")}
            />
          </div>
          <div className="px-6 pb-5 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 px-6 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors"
            >
              취소
            </button>
            <Button type="submit" loading={create.isPending} className="flex-1">
              등록
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
