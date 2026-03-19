import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const ROUTES = ["/actuator/health", "/swagger-ui.html", "/api-docs/order"];

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || "60s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<750"],
    checks: ["rate>0.99"]
  }
};

export default function () {
  const route = ROUTES[Math.floor(Math.random() * ROUTES.length)];
  const response = http.get(`${BASE_URL}${route}`, { tags: { route } });

  check(response, {
    "status is 2xx": (r) => r.status >= 200 && r.status < 300
  });

  sleep(0.2);
}
