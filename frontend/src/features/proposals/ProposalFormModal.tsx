"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/text-field";
import { useCreateProposal } from "./queries";

const schema = z.object({
  customerName: z.string().min(1, "고객명을 입력하세요"),
  phoneNumber: z
    .string()
    .regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
  birthDate: z.string().min(1, "생년월일을 입력하세요"),
  productName: z.string().min(1, "상품명을 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  monthlyPremium: z
    .string()
    .min(1, "보험료를 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) > 0, "올바른 금액을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function ProposalFormModal({ onClose }: Props) {
  const { mutate, isPending } = useCreateProposal();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    mutate(
      {
        customerName: values.customerName,
        phoneNumber: values.phoneNumber,
        birthDate: values.birthDate,
        productName: values.productName,
        insurerName: values.insurerName,
        monthlyPremium: Number(values.monthlyPremium),
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
        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">새 설계 등록</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>

        {/* 폼 */}
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="px-6 py-5 flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="고객명"
                placeholder="홍길동"
                error={errors.customerName?.message}
                {...register("customerName")}
              />
              <TextField
                label="휴대폰번호"
                placeholder="010-0000-0000"
                error={errors.phoneNumber?.message}
                {...register("phoneNumber")}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="생년월일"
                type="date"
                error={errors.birthDate?.message}
                {...register("birthDate")}
              />
              <TextField
                label="월 보험료 (원)"
                type="number"
                min="0"
                step="1"
                placeholder="150000"
                error={errors.monthlyPremium?.message}
                {...register("monthlyPremium")}
              />
            </div>

            <TextField
              label="상품명"
              placeholder="무배당 종신보험"
              error={errors.productName?.message}
              {...register("productName")}
            />

            <TextField
              label="보험사"
              placeholder="삼성생명"
              error={errors.insurerName?.message}
              {...register("insurerName")}
            />
          </div>

          {/* 액션 */}
          <div className="px-6 pb-5 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 px-6 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors"
            >
              취소
            </button>
            <Button type="submit" loading={isPending} className="flex-1">
              등록
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
