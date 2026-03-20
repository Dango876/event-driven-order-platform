import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const TARGET_RPS = Number(__ENV.TARGET_RPS || 500);
const DURATION = __ENV.DURATION || "5m";
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const MAX_VUS = Number(__ENV.MAX_VUS || 1000);
const ROUTES = ["/actuator/health", "/api-docs/order", "/api-docs/product"];

export const options = {
  scenarios: {
    gateway_sla_steady: {
      executor: "constant-arrival-rate",
      rate: TARGET_RPS,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<150"],
    checks: ["rate>0.99"]
  }
};

export default function () {
  const route = ROUTES[Math.floor(Math.random() * ROUTES.length)];
  const response = http.get(`${BASE_URL}${route}`, { tags: { route, profile: "gateway-sla" } });

  check(response, {
    "status is 2xx": (r) => r.status >= 200 && r.status < 300
  });
}
