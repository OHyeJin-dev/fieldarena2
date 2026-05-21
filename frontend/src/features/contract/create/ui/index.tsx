"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm, Controller } from "react-hook-form";
import { z } from "zod";
import type { CustomerDto } from "@/entities/customer";
import { CustomerPicker } from "@/entities/customer";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/text-field";
import { useCreatePolicy } from "../model";

const schema = z.object({
  customerId: z.string().min(1, "고객을 선택하세요"),
  productName: z.string().min(1, "상품명을 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  contractDate: z.string().min(1, "계약일을 선택하세요"),
  monthlyPremium: z
    .string()
    .min(1, "보험료를 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) > 0, "올바른 금액을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function PolicyFormModal({ onClose }: Props) {
  const { mutate, isPending } = useCreatePolicy();
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerDto | null>(null);

  const today = new Date().toISOString().slice(0, 10);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { contractDate: today },
  });

  function onSubmit(values: FormValues) {
    mutate(
      {
        customerId: values.customerId,
        productName: values.productName,
        insurerName: values.insurerName,
        contractDate: values.contractDate,
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
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">새 심사 등록</h2>
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
            <div className="flex flex-col gap-1.5">
              <label className="text-sm text-on-surface-variant">고객 *</label>
              <Controller
                control={control}
                name="customerId"
                render={({ field, fieldState }) => (
                  <CustomerPicker
                    onChange={(c) => {
                      setSelectedCustomer(c);
                      field.onChange(c?.id ?? "");
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
              {selectedCustomer && <SelectedCustomerCard customer={selectedCustomer} />}
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

            <TextField
              label="계약일"
              type="date"
              error={errors.contractDate?.message}
              {...register("contractDate")}
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

function SelectedCustomerCard({ customer }: { customer: CustomerDto }) {
  const age =
    customer.birthDate && /^\d{4}-\d{2}-\d{2}$/.test(customer.birthDate)
      ? new Date().getFullYear() - new Date(customer.birthDate).getFullYear()
      : null;
  const genderLabel =
    customer.gender === "M" ? "남" : customer.gender === "F" ? "여" : null;
  const meta = [
    customer.phone,
    customer.birthDate,
    age ? `${age}세` : null,
    genderLabel,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="bg-surface-container rounded-lg p-3 border border-outline-variant">
      <div className="text-sm font-semibold text-on-surface">{customer.name}</div>
      <div className="text-xs text-on-surface-variant mt-1">{meta}</div>
    </div>
  );
}
