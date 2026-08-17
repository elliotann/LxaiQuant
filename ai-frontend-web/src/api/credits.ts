import { get, post } from "./base";

export const getMembershipBenefits = async () => {
  return await get("/membership/benefits");
};

export const getCreditPackages = async () => {
  return await get("/membership/packages");
};

export const getMyMembership = async () => {
  return await get("/membership/my");
};

export const getCreditsLogs = async (params: any) => {
  return await get("/credits/logs", { params });
};

export const createCreditsPayment = async (data: any) => {
  return await post("/payment/credits", data);
};

export const getPaymentById = async (paymentId: string) => {
  return await get(`/payment/${paymentId}`);
};
