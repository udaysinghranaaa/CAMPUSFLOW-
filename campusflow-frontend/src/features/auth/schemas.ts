import { z } from 'zod';
const bytes = (value: string) => new TextEncoder().encode(value).length;
export const passwordSchema = z
  .string()
  .min(12, 'Use at least 12 characters.')
  .refine((value) => bytes(value) <= 72, 'Use at most 72 UTF-8 bytes.')
  .refine((value) => value.trim().length > 0, 'Password cannot be blank.');
export const loginSchema = z.object({
  email: z.email('Enter a valid email address.').max(254),
  password: z
    .string()
    .min(1, 'Enter your password.')
    .refine((value) => bytes(value) <= 72, 'Use at most 72 UTF-8 bytes.'),
});
export const changePasswordSchema = z
  .object({
    currentPassword: z
      .string()
      .min(1, 'Enter your current password.')
      .refine((value) => bytes(value) <= 72, 'Use at most 72 UTF-8 bytes.'),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((value) => value.newPassword === value.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords must match.',
  });
