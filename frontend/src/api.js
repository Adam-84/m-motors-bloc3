export async function api(path, { method = "GET", body } = {}) {
  const headers = {};
  if (method !== "GET") {
    const response = await fetch("/api/csrf", { cache: "no-store" });
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
  }
  let content = body;
  if (
    body &&
    !(body instanceof FormData) &&
    !(body instanceof URLSearchParams)
  ) {
    headers["Content-Type"] = "application/json";
    content = JSON.stringify(body);
  }
  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: content,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.message ||
        {
          401: "Connectez-vous pour continuer.",
          403: "Action non autorisée. Rechargez la page si votre session a expiré.",
          413: "La pièce dépasse 5 Mo.",
        }[response.status] ||
        "La requête a échoué.",
    );
  }
  return response.status === 204 ? null : response.json();
}
