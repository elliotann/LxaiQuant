import { useAuthStore } from "@/stores/auth";
import type { Directive } from "vue";

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const authStore = useAuthStore();
    const required = binding.value;

    if (!required) return;

    const perms = Array.isArray(required) ? required : [required];
    const hasAll = perms.every((p) => authStore.hasPermission(p));

    if (!hasAll) {
      el.parentNode?.removeChild(el);
    }
  },
};
