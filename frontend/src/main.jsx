import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { api } from "./api";
import "./style.css";

const euro = (value) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  }).format(value);
const number = (value) => new Intl.NumberFormat("fr-FR").format(value);
const services = {
  ASSURANCE: ["Assurance tous risques", 35],
  ASSISTANCE: ["Assistance dépannage", 8],
  ENTRETIEN: ["Entretien et SAV", 25],
  CONTROLE: ["Contrôle technique", 5],
};
const statuses = {
  BROUILLON: "À compléter",
  SOUMIS: "En cours d’étude",
  VALIDE: "Validé",
  REFUSE: "Refusé",
};
const pieceName = {
  IDENTITE: "Pièce d’identité",
  DOMICILE: "Justificatif de domicile",
};

function CarArt({ large = false }) {
  return (
    <svg
      className={`car-art ${large ? "large" : ""}`}
      viewBox="0 0 600 250"
      role="img"
      aria-label="Illustration d’un véhicule d’occasion"
    >
      <ellipse cx="304" cy="204" rx="245" ry="13" fill="#153c3120" />
      <path
        d="M74 158 106 132 189 119 247 66Q264 53 288 54L374 59Q394 62 409 82L446 125 514 141Q535 146 542 167L534 185 70 185Z"
        fill="currentColor"
      />
      <path
        d="M207 120 257 76 300 74 300 121ZM313 75 367 79Q378 80 390 97L412 122 314 121Z"
        fill="#243f3b"
      />
      <path d="M68 169H542L534 185H70Z" fill="#243f3b" />
      <path
        d="M84 151 119 148 111 164 78 165ZM502 147 530 155 535 166 504 163Z"
        fill="#fbf7df"
      />
      <path
        d="M307 129V172M199 131 190 170M325 137H342"
        stroke="#ffffff70"
        strokeWidth="3"
        fill="none"
      />
      {[162, 447].map((x) => (
        <g key={x}>
          <circle cx={x} cy="182" r="35" fill="#1b2826" />
          <circle cx={x} cy="182" r="21" fill="#d9ded9" />
          <circle cx={x} cy="182" r="10" fill="#697b75" />
        </g>
      ))}
    </svg>
  );
}
function App() {
  const [user, setUser] = useState(null),
    [page, setPage] = useState("catalogue");
  const [cars, setCars] = useState([]),
    [offer, setOffer] = useState(""),
    [search, setSearch] = useState(""),
    [budget, setBudget] = useState("");
  const [selected, setSelected] = useState(null),
    [options, setOptions] = useState([]),
    [dossiers, setDossiers] = useState([]),
    [dossier, setDossier] = useState(null);
  const [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [register, setRegister] = useState(false);
  const [adminTab, setAdminTab] = useState("dossiers"),
    [adminCars, setAdminCars] = useState([]);
  useEffect(() => {
    api("/me")
      .then(setUser)
      .catch(() => {});
  }, []);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError("");
    const timer = setTimeout(() => {
      const params = new URLSearchParams();
      if (offer) params.set("offer", offer);
      if (search) params.set("search", search);
      if (budget) params.set("maxPrice", budget);
      api(`/vehicles?${params}`)
        .then((data) => {
          if (current) setCars(data);
        })
        .catch((e) => {
          if (current) setError(e.message);
        })
        .finally(() => {
          if (current) setLoading(false);
        });
    }, 200);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [offer, search, budget, page]);
  async function act(fn) {
    if (busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function navigate(next) {
    setPage(next);
    setSelected(null);
    setError("");
    setMessage("");
    window.scrollTo({ top: 0 });
  }
  async function loadDossiers(admin = false) {
    setDossiers(await api(admin ? "/admin/dossiers" : "/dossiers"));
  }
  async function openSpace() {
    if (!user) return navigate("compte");
    await act(async () => {
      await loadDossiers(user.role === "ADMIN");
      setAdminCars(user.role === "ADMIN" ? await api("/admin/vehicles") : []);
      navigate(user.role === "ADMIN" ? "admin" : "dossiers");
    });
  }
  async function openDossier(id) {
    await act(async () => {
      setDossier(await api(`/dossiers/${id}`));
      navigate("dossier");
    });
  }
  async function login(form, isRegistration) {
    await act(async () => {
      if (isRegistration)
        await api("/register", {
          method: "POST",
          body: {
            name: form.get("name"),
            email: form.get("email"),
            password: form.get("password"),
          },
        });
      await api("/login", {
        method: "POST",
        body: new URLSearchParams({
          email: form.get("email").trim().toLowerCase(),
          password: form.get("password"),
        }),
      });
      setUser(await api("/me"));
      navigate("catalogue");
      setMessage(
        isRegistration
          ? "Votre compte est créé. Choisissez votre véhicule pour ouvrir un dossier."
          : "Vous êtes connecté. Bonne route !",
      );
    });
  }
  async function createDossier() {
    if (!user) {
      navigate("compte");
      setMessage(
        "Créez un compte ou connectez-vous, puis sélectionnez votre véhicule.",
      );
      return;
    }
    await act(async () => {
      const d = await api("/dossiers", {
        method: "POST",
        body: { vehicleId: selected.id, options },
      });
      setDossier(d);
      navigate("dossier");
    });
  }
  const selectedMonthly = selected
    ? selected.monthly +
      options.reduce((sum, code) => sum + services[code][1], 0)
    : 0;
  return (
    <>
      <div className="topline">
        L’occasion, en toute confiance. <span>Depuis 1987</span>
      </div>
      <header className="header">
        <button
          className="brand"
          onClick={() => navigate("catalogue")}
          aria-label="M-Motors, accueil"
        >
          <span className="brand-mark">M</span> M-MOTORS
          <span className="brand-dot">.</span>
        </button>
        <nav aria-label="Navigation principale">
          <button
            className={page === "catalogue" ? "nav active" : "nav"}
            onClick={() => navigate("catalogue")}
          >
            Nos véhicules
          </button>
          <button
            className="nav"
            onClick={() => {
              navigate("catalogue");
              setOffer("LOCATION");
            }}
          >
            La location
          </button>
          <button className="space-btn" onClick={openSpace}>
            {user ? user.name : "Mon espace"} <span aria-hidden="true">↗</span>
          </button>
          {user && (
            <button
              className="logout"
              title="Se déconnecter"
              onClick={() =>
                act(async () => {
                  await api("/logout", { method: "POST" });
                  setUser(null);
                  setDossiers([]);
                  setDossier(null);
                  navigate("catalogue");
                })
              }
            >
              Déconnexion
            </button>
          )}
        </nav>
      </header>
      <main>
        {(message || error) && (
          <div
            className={`notice ${error ? "error" : ""}`}
            role={error ? "alert" : "status"}
          >
            {error || message}
            <button
              aria-label="Fermer le message"
              onClick={() => {
                setError("");
                setMessage("");
              }}
            >
              ×
            </button>
          </div>
        )}
        {page === "catalogue" && !selected && (
          <>
            <section className="hero">
              <div className="hero-copy">
                <p className="eyebrow">
                  <span /> L’ENVIE D’ALLER PLUS LOIN
                </p>
                <h1>
                  Votre prochaine
                  <br />
                  route commence <em>ici.</em>
                </h1>
                <p className="hero-description">
                  À l’achat ou en location, trouvez le véhicule d’occasion qui
                  vous ressemble. On s’occupe de la suite.
                </p>
                <a className="hero-link" href="#catalogue">
                  Explorer le catalogue <span>↓</span>
                </a>
                <div className="trust">
                  <span>✓ Véhicules contrôlés</span>
                  <span>✓ Dossier 100 % en ligne</span>
                </div>
              </div>
              <div className="hero-visual">
                <span className="orbit one" />
                <span className="orbit two" />
                <div className="visual-label">UNE NOUVELLE ROUTE.</div>
                <CarArt large />
                <div className="visual-note">
                  <span>01 /</span> Votre liberté, votre choix.
                </div>
              </div>
            </section>
            <section className="catalogue section" id="catalogue">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">LE BON VÉHICULE, AU BON MOMENT</p>
                  <h2>Trouvez votre prochain départ.</h2>
                </div>
                <span className="count">
                  {loading
                    ? "Recherche…"
                    : `${cars.length} véhicules disponibles`}
                </span>
              </div>
              <div className="tabs" role="group" aria-label="Type d’offre">
                {[
                  ["", "Tous les véhicules"],
                  ["ACHAT", "Achat"],
                  ["LOCATION", "Location avec option d’achat"],
                ].map(([value, label]) => (
                  <button
                    key={value}
                    className={offer === value ? "tab selected" : "tab"}
                    aria-pressed={offer === value}
                    onClick={() => {
                      setOffer(value);
                      setBudget("");
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <form className="filters" onSubmit={(e) => e.preventDefault()}>
                <label>
                  Marque, modèle ou motorisation
                  <input
                    type="search"
                    placeholder="Ex. Renault, hybride…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    maxLength={100}
                  />
                </label>
                <label>
                  {offer === "LOCATION"
                    ? "Mensualité maximale (€)"
                    : offer === "ACHAT"
                      ? "Prix maximal (€)"
                      : "Budget maximal (prix ou mensualité)"}
                  <input
                    type="number"
                    min="0"
                    placeholder={
                      offer === "LOCATION" ? "Ex. 350" : "Votre budget"
                    }
                    value={budget}
                    onChange={(e) => setBudget(e.target.value)}
                  />
                </label>
                <button
                  className="text-btn"
                  type="button"
                  onClick={() => {
                    setSearch("");
                    setBudget("");
                    setOffer("");
                  }}
                >
                  Réinitialiser ↺
                </button>
              </form>
              {loading ? (
                <p className="empty" role="status">
                  Recherche de votre prochain véhicule…
                </p>
              ) : cars.length === 0 ? (
                <div className="empty">
                  <h3>Aucun véhicule pour ces critères.</h3>
                  <p>Essayez une autre recherche ou élargissez votre budget.</p>
                </div>
              ) : (
                <div className="car-grid">
                  {cars.map((car, index) => (
                    <article
                      className={`car-card color-${index % 3}`}
                      key={car.id}
                      data-testid="vehicle-card"
                    >
                      <div className="car-picture">
                        <span className="offer-badge">
                          {car.offer === "ACHAT"
                            ? "À l’achat"
                            : "Location · LOA"}
                        </span>
                        <CarArt />
                        <span className="picture-caption">
                          Illustration non contractuelle
                        </span>
                      </div>
                      <div className="car-info">
                        <div className="car-title">
                          <div>
                            <p>{car.brand}</p>
                            <h3>{car.model}</h3>
                          </div>
                          <span className="arrow-icon">↗</span>
                        </div>
                        <p className="car-specs">
                          {car.year} <span>·</span> {number(car.mileage)} km{" "}
                          <span>·</span> {car.fuel}
                        </p>
                        <p className="transmission">{car.transmission}</p>
                        <div className="card-bottom">
                          <div>
                            <span className="price-caption">
                              {car.offer === "LOCATION"
                                ? "À partir de"
                                : "Prix du véhicule"}
                            </span>
                            <strong>
                              {euro(
                                car.offer === "LOCATION"
                                  ? car.monthly
                                  : car.price,
                              )}
                              {car.offer === "LOCATION" && (
                                <small> / mois</small>
                              )}
                            </strong>
                          </div>
                          <button
                            className="dark-btn"
                            onClick={() => {
                              setSelected(car);
                              setOptions([]);
                              window.scrollTo({ top: 0 });
                            }}
                          >
                            Découvrir
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
              <p className="catalogue-footnote">
                Catalogue pédagogique : véhicules et prix fictifs. Les
                mensualités sont des exemples, hors apport et valeur de rachat à
                contractualiser.
              </p>
            </section>
            <section className="reassurance">
              <p className="eyebrow">ON AVANCE ENSEMBLE</p>
              <h2>
                Moins de démarches.
                <br />
                Plus de kilomètres.
              </h2>
              <div className="steps">
                {[
                  [
                    "01",
                    "Choisissez votre véhicule",
                    "Achat ou location : comparez les offres à votre rythme.",
                  ],
                  [
                    "02",
                    "Déposez votre dossier",
                    "Créez votre espace et transmettez vos pièces en ligne.",
                  ],
                  [
                    "03",
                    "Suivez chaque étape",
                    "Retrouvez la décision de notre équipe dans votre espace.",
                  ],
                ].map(([n, title, text]) => (
                  <div key={n}>
                    <span>{n}</span>
                    <h3>{title}</h3>
                    <p>{text}</p>
                  </div>
                ))}
              </div>
            </section>
          </>
        )}
        {page === "catalogue" && selected && (
          <section className="section detail">
            <button className="text-btn" onClick={() => setSelected(null)}>
              ← Retour aux véhicules
            </button>
            <div className="detail-grid">
              <div className="detail-art">
                <CarArt large />
                <span>Illustration non contractuelle</span>
              </div>
              <div>
                <p className="eyebrow">
                  {selected.offer === "ACHAT"
                    ? "VOTRE PROCHAIN ACHAT"
                    : "VOTRE PROCHAINE LOCATION"}
                </p>
                <h1>
                  {selected.brand}
                  <br />
                  <em>{selected.model}</em>
                </h1>
                <p>
                  {selected.year} · {number(selected.mileage)} km ·{" "}
                  {selected.fuel} · {selected.transmission}
                </p>
                <div className="detail-price">
                  {euro(
                    selected.offer === "LOCATION"
                      ? selectedMonthly
                      : selected.price,
                  )}
                  {selected.offer === "LOCATION" && <small> / mois</small>}
                </div>
                {selected.offer === "LOCATION" && (
                  <fieldset className="options">
                    <legend>Personnalisez votre abonnement</legend>
                    {Object.entries(services).map(([code, [label, price]]) => (
                      <label key={code}>
                        <input
                          type="checkbox"
                          value={code}
                          checked={options.includes(code)}
                          onChange={(e) =>
                            setOptions(
                              e.target.checked
                                ? [...options, code]
                                : options.filter((o) => o !== code),
                            )
                          }
                        />
                        <span>{label}</span>
                        <strong>+ {price} €/mois</strong>
                      </label>
                    ))}
                  </fieldset>
                )}
                <button
                  className="primary full"
                  disabled={busy}
                  onClick={createDossier}
                >
                  {busy ? "Ouverture…" : "Ouvrir mon dossier"} ↗
                </button>
                <p className="fine">
                  Sans paiement en ligne. L’ouverture d’un dossier ne réserve
                  pas le véhicule. Pour la LOA, durée, apport et option d’achat
                  restent à préciser avec un conseiller.
                </p>
              </div>
            </div>
          </section>
        )}
        {page === "mentions" && (
          <section className="legal">
            <h1>Mentions légales</h1>
            <p>
              Ce site est un projet pédagogique réalisé dans le cadre de
              l’examen Bloc 3 du Bachelor HETIC. Il ne propose aucune vente ni
              location réelle ; les véhicules, comptes et tarifs sont fictifs.
            </p>
            <h2>Éditeur</h2>
            <p>Adam Ghajdaoui, étudiant. Contact : via le profil GitHub github.com/Adam-84.</p>
            <p>Responsable de la publication : Adam Ghajdaoui.</p>
            <h2>Hébergement</h2>
            <p>Render Services, Inc., 525 Brannan Street, Suite 300, San Francisco, CA 94107, États-Unis (render.com). Serveurs situés à Francfort (Allemagne).</p>
            <h2>Propriété intellectuelle</h2>
            <p>
              Le nom M-Motors est utilisé dans le seul cadre de l’énoncé de
              l’examen. Le code et les visuels sont des travaux pédagogiques : tous droits réservés, reproduction interdite sans accord de l’auteur.
            </p>
          </section>
        )}
        {page === "confidentialite" && (
          <section className="legal">
            <h1>Politique de confidentialité</h1>
            <h2>Données collectées</h2>
            <p>
              À l’inscription : nom, adresse e-mail et mot de passe (conservé
              sous forme hachée). Pour un dossier : les pièces que vous
              téléversez et l’état du dossier. N’envoyez que des documents
              fictifs.
            </p>
            <h2>Finalités et base légale</h2>
            <p>
              Gérer votre compte et instruire votre dossier d’achat ou de
              location, à votre demande. Base légale : l’exécution de mesures
              précontractuelles prises à votre demande.
            </p>
            <h2>Durée de conservation</h2>
            <p>Les données de ce site de démonstration sont conservées pendant la durée de l’évaluation de l’examen, puis supprimées avec la base de données (au plus tard 30 jours après la mise en ligne).</p>
            <h2>Destinataires et sécurité</h2>
            <p>
              Seuls vous et l’équipe d’administration accédez à votre dossier.
              Aucune donnée n’est transmise à des tiers publicitaires. Le
              cookie utilisé est un cookie de session strictement nécessaire.
            </p>
            <h2>Vos droits</h2>
            <p>
              Accès, rectification, effacement, opposition et portabilité :
              contactez l’éditeur via le profil GitHub github.com/Adam-84. Vous pouvez saisir la
              CNIL (cnil.fr).
            </p>
          </section>
        )}
        {page === "compte" && (
          <section className="account section">
            <div className="account-intro">
              <p className="eyebrow">VOTRE ESPACE PERSONNEL</p>
              <h1>
                La suite du voyage
                <br />
                se passe <em>ici.</em>
              </h1>
              <p>
                Préparez votre dossier et retrouvez l’avancement de votre
                demande, en un seul endroit.
              </p>
              <CarArt />
            </div>
            <form
              className="panel account-form"
              onSubmit={(e) => {
                e.preventDefault();
                login(new FormData(e.currentTarget), register);
              }}
            >
              <div className="tabs">
                <button
                  type="button"
                  className={!register ? "tab selected" : "tab"}
                  onClick={() => setRegister(false)}
                >
                  Se connecter
                </button>
                <button
                  type="button"
                  className={register ? "tab selected" : "tab"}
                  onClick={() => setRegister(true)}
                >
                  Créer un compte
                </button>
              </div>
              <h2>
                {register
                  ? "Bienvenue chez M-Motors."
                  : "Heureux de vous retrouver."}
              </h2>
              {register && (
                <label>
                  Votre nom
                  <input
                    name="name"
                    autoComplete="name"
                    required
                    maxLength={100}
                  />
                </label>
              )}
              <label>
                Adresse e-mail
                <input
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  maxLength={254}
                />
              </label>
              <label>
                Mot de passe
                <input
                  name="password"
                  type="password"
                  autoComplete={register ? "new-password" : "current-password"}
                  minLength={register ? 12 : undefined}
                  maxLength={64}
                  required
                />
              </label>
              {register && (
                <p className="fine">
                  Au moins 12 caractères. Pour cette démonstration, utilisez
                  uniquement des informations fictives.
                </p>
              )}
              <button className="primary full" disabled={busy}>
                {busy
                  ? "Un instant…"
                  : register
                    ? "Valider mon inscription"
                    : "Me connecter"}{" "}
                ↗
              </button>
            </form>
          </section>
        )}
        {(page === "dossiers" || page === "admin") && (
          <section className="section workspace">
            <p className="eyebrow">
              {page === "admin"
                ? "LE POSTE DE PILOTAGE"
                : "VOTRE ESPACE PERSONNEL"}
            </p>
            <div className="section-heading">
              <h1>
                {page === "admin" ? "Bonjour, l’équipe." : "Mes dossiers."}
              </h1>
              <button
                className="text-btn"
                onClick={() => navigate("catalogue")}
              >
                Voir le catalogue ↗
              </button>
            </div>
            {page === "admin" && (
              <div className="tabs">
                <button
                  className={adminTab === "dossiers" ? "tab selected" : "tab"}
                  onClick={() => setAdminTab("dossiers")}
                >
                  Dossiers clients
                </button>
                <button
                  className={adminTab === "catalogue" ? "tab selected" : "tab"}
                  onClick={() => setAdminTab("catalogue")}
                >
                  Gestion du catalogue
                </button>
              </div>
            )}
            {page === "admin" && adminTab === "catalogue" ? (
              <>
                <div className="admin-cars">
                  {adminCars.map((car) => (
                    <div className="list-row" key={car.id}>
                      <div>
                        <strong>
                          {car.brand} {car.model}
                        </strong>
                        <p>
                          {car.offer} ·{" "}
                          {car.available ? "Disponible" : "Attribué"}
                        </p>
                      </div>
                      <button
                        className="outline"
                        disabled={busy || !car.available}
                        onClick={() =>
                          act(async () => {
                            await api(`/admin/vehicles/${car.id}/offer`, {
                              method: "PUT",
                              body: {
                                offer:
                                  car.offer === "ACHAT" ? "LOCATION" : "ACHAT",
                              },
                            });
                            setAdminCars(await api("/admin/vehicles"));
                            setMessage("Le type d’offre a été modifié.");
                          })
                        }
                      >
                        Passer en {car.offer === "ACHAT" ? "location" : "vente"}
                      </button>
                    </div>
                  ))}
                </div>
                <VehicleForm
                  busy={busy}
                  onSubmit={(form) =>
                    act(async () => {
                      await api("/admin/vehicles", {
                        method: "POST",
                        body: form,
                      });
                      setAdminCars(await api("/admin/vehicles"));
                      setMessage("Véhicule ajouté au catalogue.");
                    })
                  }
                />
              </>
            ) : dossiers.length ? (
              <div className="dossier-list">
                {dossiers.map((d) => (
                  <button
                    className="list-row"
                    key={d.id}
                    onClick={() => openDossier(d.id)}
                  >
                    <div>
                      <span className="fine">
                        {d.offer === "ACHAT" ? "ACHAT" : "LOCATION"}
                      </span>
                      <h3>
                        {d.brand} {d.model}
                      </h3>
                      <p>
                        {page === "admin"
                          ? d.email
                          : new Date(d.createdAt).toLocaleDateString("fr-FR")}
                      </p>
                    </div>
                    <span className={`status ${d.status}`}>
                      {statuses[d.status]}
                    </span>
                    <span>Consulter ↗</span>
                  </button>
                ))}
              </div>
            ) : (
              <div className="empty">
                <h2>Votre prochain départ reste à choisir.</h2>
                <p>Aucun dossier pour le moment.</p>
                <button
                  className="primary"
                  onClick={() => navigate("catalogue")}
                >
                  Explorer les véhicules
                </button>
              </div>
            )}
          </section>
        )}
        {page === "dossier" && dossier && (
          <section className="section workspace">
            <button className="text-btn" onClick={openSpace}>
              ← Retour à mon espace
            </button>
            <div className="section-heading">
              <div>
                <p className="eyebrow">
                  {dossier.id.slice(0, 8).toUpperCase()} · {dossier.offer}
                </p>
                <h2>
                  Mon dossier · {dossier.brand} {dossier.model}
                </h2>
              </div>
              <span className={`status ${dossier.status}`}>
                {statuses[dossier.status]}
              </span>
            </div>
            <div className="dossier-grid">
              <div className="panel">
                <h3>Mes pièces justificatives</h3>
                <p>
                  Pièces fictives uniquement dans cette démonstration. PDF, JPEG
                  ou PNG, 5 Mo maximum par pièce.
                </p>
                {Object.entries(pieceName).map(([kind, label]) => {
                  const doc = dossier.documents.find((d) => d.kind === kind);
                  return (
                    <div className="upload-row" key={kind}>
                      <strong>{label}</strong>
                      {doc ? (
                        <a href={`/api/documents/${doc.id}`}>
                          Télécharger la pièce ↗
                        </a>
                      ) : (
                        <span className="fine">À ajouter</span>
                      )}
                      {dossier.status === "BROUILLON" && (
                        <label className="file-label">
                          {doc ? "Remplacer" : "Choisir un fichier"}
                          <input
                            type="file"
                            data-kind={kind}
                            accept=".pdf,.jpg,.jpeg,.png"
                            disabled={busy}
                            onChange={(e) => {
                              const file = e.target.files[0];
                              if (!file) return;
                              if (file.size > 5 * 1024 * 1024) {
                                setError("La pièce dépasse 5 Mo.");
                                return;
                              }
                              act(async () => {
                                const form = new FormData();
                                form.set("file", file);
                                form.set("kind", kind);
                                setDossier(
                                  await api(
                                    `/dossiers/${dossier.id}/documents`,
                                    { method: "POST", body: form },
                                  ),
                                );
                                setMessage(
                                  `${label} ${kind === "IDENTITE" ? "ajoutée" : "ajouté"}.`,
                                );
                              });
                            }}
                          />
                        </label>
                      )}
                    </div>
                  );
                })}
                {dossier.status === "BROUILLON" && (
                  <button
                    className="primary full"
                    disabled={busy || dossier.documents.length < 2}
                    onClick={() =>
                      act(async () => {
                        setDossier(
                          await api(`/dossiers/${dossier.id}/submit`, {
                            method: "POST",
                          }),
                        );
                        setMessage(
                          "Dossier envoyé. Notre équipe va étudier votre demande.",
                        );
                      })
                    }
                  >
                    Soumettre mon dossier ↗
                  </button>
                )}
                {dossier.status === "BROUILLON" && (
                  <button
                    className="outline full"
                    disabled={busy}
                    onClick={() => {
                      if (
                        !window.confirm(
                          "Annuler ce brouillon ? Les pièces déposées seront supprimées.",
                        )
                      )
                        return;
                      act(async () => {
                        await api(`/dossiers/${dossier.id}`, {
                          method: "DELETE",
                        });
                        setDossier(null);
                        await loadDossiers();
                        navigate("dossiers");
                        setMessage("Brouillon annulé.");
                      });
                    }}
                  >
                    Annuler ce brouillon
                  </button>
                )}
                {dossier.reason && (
                  <div className="notice">
                    <strong>Retour de l’équipe</strong>
                    <p>{dossier.reason}</p>
                  </div>
                )}
              </div>
              <aside className="panel summary">
                <p className="eyebrow">VOTRE DEMANDE</p>
                <h3>
                  {dossier.brand} {dossier.model}
                </h3>
                <div className="detail-price">
                  {euro(
                    dossier.offer === "LOCATION"
                      ? dossier.monthly
                      : dossier.price,
                  )}
                  {dossier.offer === "LOCATION" && <small> / mois</small>}
                </div>
                {dossier.options.map((code) => (
                  <p key={code}>✓ {services[code][0]}</p>
                ))}
                <p className="fine">
                  Montants figés à l’ouverture de ce dossier. La décision ne
                  vaut pas signature d’un contrat.
                </p>
                {user?.role === "ADMIN" && dossier.status === "SOUMIS" && (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      const form = new FormData(e.currentTarget);
                      act(async () => {
                        setDossier(
                          await api(`/admin/dossiers/${dossier.id}/decision`, {
                            method: "POST",
                            body: {
                              status: form.get("status"),
                              reason: form.get("reason"),
                            },
                          }),
                        );
                        setMessage("La décision a été enregistrée.");
                      });
                    }}
                  >
                    <h3>Décision commerciale</h3>
                    <label>
                      Décision
                      <select name="status">
                        <option value="VALIDE">Valider</option>
                        <option value="REFUSE">Refuser</option>
                      </select>
                    </label>
                    <label>
                      Motif (obligatoire en cas de refus)
                      <textarea name="reason" maxLength={1000} rows={3} />
                    </label>
                    <button className="primary full" disabled={busy}>
                      Enregistrer la décision
                    </button>
                  </form>
                )}
              </aside>
            </div>
          </section>
        )}
      </main>
      <footer>
        <div className="brand">M-MOTORS.</div>
        <p>Une nouvelle route, depuis 1987.</p>
        <nav aria-label="Informations légales" className="legal-links">
          <button onClick={() => navigate("mentions")}>Mentions légales</button>
          <button onClick={() => navigate("confidentialite")}>
            Confidentialité
          </button>
        </nav>
        <span>Projet pédagogique · Adam Ghajdaoui · Bloc 3</span>
      </footer>
    </>
  );
}
function VehicleForm({ onSubmit, busy }) {
  return (
    <form
      className="panel vehicle-form"
      onSubmit={(e) => {
        e.preventDefault();
        const form = Object.fromEntries(new FormData(e.currentTarget));
        for (const key of ["year", "mileage", "price", "monthly"])
          form[key] = Number(form[key]);
        onSubmit(form);
      }}
    >
      <h2>Ajouter un véhicule</h2>
      <div className="form-grid">
        {[
          ["brand", "Marque", "text"],
          ["model", "Modèle", "text"],
          ["year", "Année", "number"],
          ["mileage", "Kilométrage", "number"],
          ["fuel", "Motorisation", "text"],
          ["transmission", "Transmission", "text"],
          ["price", "Prix d’achat (€)", "number"],
          ["monthly", "Mensualité hors services (€)", "number"],
        ].map(([name, label, type]) => (
          <label key={name}>
            {label}
            <input
              name={name}
              type={type}
              required
              min={name === "year" ? 1950 : name === "mileage" ? 0 : 0.01}
              max={name === "year" ? 2100 : undefined}
              step={["monthly", "price"].includes(name) ? "0.01" : undefined}
              maxLength={name === "model" ? 100 : 60}
            />
          </label>
        ))}
        <label>
          Type d’offre
          <select name="offer">
            <option value="ACHAT">Achat</option>
            <option value="LOCATION">Location avec option d’achat</option>
          </select>
        </label>
      </div>
      <button className="primary" disabled={busy}>
        Ajouter au catalogue ↗
      </button>
    </form>
  );
}

createRoot(document.getElementById("root")).render(<App />);
