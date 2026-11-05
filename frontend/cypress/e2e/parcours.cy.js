function pdfFixture() {
  let content = "%PDF-1.4\n";
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>",
  ];
  const offsets = [0];
  objects.forEach((body, index) => {
    offsets.push(content.length);
    content += `${index + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = content.length;
  content +=
    "xref\n0 4\n0000000000 65535 f \n" +
    offsets
      .slice(1)
      .map((n) => `${String(n).padStart(10, "0")} 00000 n \n`)
      .join("");
  content += `trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Cypress.Buffer.from(content);
}
const clientEmail = `cypress-${Date.now()}@example.test`;
describe("M-Motors : parcours de bout en bout", () => {
  it("filtre le catalogue puis crée et soumet un dossier client", () => {
    cy.visit("/");
    cy.viewport(1365, 900);
    cy.get('[data-testid="vehicle-card"]').should("have.length", 6);
    cy.viewport(390, 844);
    cy.window().then((win) =>
      expect(win.document.documentElement.scrollWidth).to.be.at.most(
        win.innerWidth,
      ),
    );
    cy.viewport(1365, 900);
    cy.contains("button", "Location avec option d’achat").click();
    cy.get('[data-testid="vehicle-card"]').should("have.length", 3);
    cy.contains("button", "Mon espace").click();
    cy.contains("button", "Créer un compte").click();
    cy.get('input[name="name"]').type("Client Cypress");
    cy.get('input[name="email"]').type(clientEmail);
    cy.get('input[name="password"]').type("CypressExample123!");
    cy.contains("button", "Valider mon inscription").click();
    cy.contains("Votre compte est créé");
    cy.get('[data-testid="vehicle-card"]')
      .first()
      .contains("button", "Découvrir")
      .click();
    cy.get('input[value="ASSURANCE"]').check();
    cy.contains("button", "Ouvrir mon dossier").click();
    cy.contains("h2", "Mon dossier");
    const file = {
      contents: pdfFixture(),
      fileName: "test.pdf",
      mimeType: "application/pdf",
    };
    cy.get('input[data-kind="IDENTITE"]').selectFile(file);
    cy.contains("Pièce d’identité ajoutée");
    cy.get('input[data-kind="DOMICILE"]').selectFile(file);
    cy.contains("Justificatif de domicile ajouté");
    cy.contains("button", "Soumettre mon dossier").click();
    cy.contains("Dossier envoyé");
    cy.contains("En cours d’étude");
  });
  it("permet à un administrateur d’instruire le dossier et de motiver un refus", () => {
    cy.visit("/");
    cy.contains("button", "Mon espace").click();
    cy.get('input[name="email"]').type("admin@mmotors.example");
    cy.get('input[name="password"]').type("DemoAdmin2026!");
    cy.contains("button", "Me connecter").click();
    cy.contains("Vous êtes connecté");
    cy.get("button.space-btn").click();
    cy.contains("button.list-row", clientEmail).click();
    cy.get('select[name="status"]').select("REFUSE");
    cy.get('textarea[name="reason"]').type(
      "Pièces fictives de démonstration : refus de test Cypress.",
    );
    cy.contains("button", "Enregistrer la décision").click();
    cy.contains("La décision a été enregistrée");
    cy.contains("Refusé");
  });
});
