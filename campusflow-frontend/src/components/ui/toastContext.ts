import { createContext, useContext } from 'react';
export const ToastContext = createContext<(message: string, kind?: 'success' | 'error') => void>(
  () => {},
);
export const useToast = () => useContext(ToastContext);
