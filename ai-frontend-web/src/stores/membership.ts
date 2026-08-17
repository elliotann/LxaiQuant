import { defineStore } from "pinia";
import { ref } from "vue";
import { getMyMembership, getCreditPackages, getMembershipBenefits } from "@/api/credits";

export const useMembershipStore = defineStore("membership", () => {
  const creditsBalance = ref(0);
  const packages = ref<any[]>([]);
  const benefits = ref<any[]>([]);

  async function fetchMyMembership() {
    const res = await getMyMembership();
    if (res.success) {
      creditsBalance.value = res.creditsBalance ?? 0;
    }
  }

  async function fetchPackages() {
    const res = await getCreditPackages();
    if (res.success) {
      packages.value = res.packages ?? [];
    }
  }

  async function fetchBenefits() {
    const res = await getMembershipBenefits();
    if (res.success) {
      benefits.value = res.benefits ?? [];
    }
  }

  return { creditsBalance, packages, benefits, fetchMyMembership, fetchPackages, fetchBenefits };
});
