import { z } from 'zod';
export const requiredText = z.string().trim().min(1, 'This field is required.');
export const positiveId = requiredText
  .regex(/^[1-9][0-9]*$/, 'Use a positive whole number.')
  .refine((value) => Number.isSafeInteger(Number(value)), 'Number is too large.');
export const dateField = z.iso.date('Enter a valid date.');
export const timeField = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Enter a valid campus-local time.');
