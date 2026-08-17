import { computed } from "vue";
import { useMembershipStore } from "@/stores/membership";

export function useCredits() {
  const store = useMembershipStore();

  const hasEnoughCredits = (required: number) => computed(() => store.creditsBalance >= required);

  const formatCredits = (amount: number) => {
    if (amount >= 10000) {
      return (amount / 10000).toFixed(1) + "万";
    }
    return amount.toLocaleString();
  };

  return { hasEnoughCredits, formatCredits };
}
