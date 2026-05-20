"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/shared/ui/button";
import { TextField } from "@/shared/ui/text-field";
import { useCreateCustomer, useUpdateCustomer } from "../model";
import type { CustomerDto } from "@/entities/customer";

const schema = z.object({
  name: z.string().min(1, "이름을 입력하세요"),
  phone: z.string().regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
  birthDate: z.string().optional(),
  gender: z.enum(["M", "F", ""]).optional(),
  email: z.string().email("올바른 이메일").or(z.literal("")).optional(),
  address: z.string().optional(),
  memo: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
  initial?: CustomerDto;
}

export function CustomerFormModal({ onClose, initial }: Props) {
  const create = useCreateCustomer();
  const update = useUpdateCustomer();
  const isEdit = !!initial;
  const isPending = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initial?.name ?? "",
      phone: initial?.phone ?? "",
      birthDate: initial?.birthDate ?? "",
      gender: (initial?.gender as "M" | "F" | "") ?? "",
      email: initial?.email ?? "",
      address: initial?.address ?? "",
      memo: initial?.memo ?? "",
    },
  });

  function onSubmit(values: FormValues) {
    const payload = {
      name: values.name,
      phone: values.phone,
      birthDate: values.birthDate || null,
      gender: values.gender || null,
      email: values.email || null,
      address: values.address || null,
      memo: values.memo || null,
    };
    if (isEdit && initial) {
      update.mutate({ id: initial.id, req: payload }, { onSuccess: onClose });
    } else {
      create.mutate(payload, { onSuccess: onClose });
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">
            {isEdit ? "고객 수정" : "신규 고객 등록"}
          </h2>
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
            <div className="grid grid-cols-2 gap-4">
              <TextField label="이름" error={errors.name?.message} {...register("name")} />
              <TextField
                label="휴대폰번호"
                placeholder="010-0000-0000"
                error={errors.phone?.message}
                {...register("phone")}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="생년월일"
                type="date"
                error={errors.birthDate?.message}
                {...register("birthDate")}
              />
              <div className="flex flex-col gap-1">
                <label className="text-sm text-on-surface-variant">성별</label>
                <select
                  className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
                  {...register("gender")}
                >
                  <option value="">선택</option>
                  <option value="M">남</option>
                  <option value="F">여</option>
                </select>
              </div>
            </div>
            <TextField
              label="이메일"
              type="email"
              error={errors.email?.message}
              {...register("email")}
            />
            <TextField label="주소" error={errors.address?.message} {...register("address")} />
            <TextField label="메모" error={errors.memo?.message} {...register("memo")} />
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
              {isEdit ? "수정" : "등록"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
