CREATE TABLE app_user (
 id VARCHAR(36) PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(100) NOT NULL,
 role VARCHAR(10) NOT NULL CHECK (role IN ('CLIENT','ADMIN'))
);
CREATE TABLE vehicle (
 id VARCHAR(36) PRIMARY KEY, brand VARCHAR(60) NOT NULL, model VARCHAR(100) NOT NULL,
 model_year INTEGER NOT NULL, mileage INTEGER NOT NULL CHECK (mileage >= 0),
 fuel VARCHAR(30) NOT NULL, transmission VARCHAR(30) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK (price > 0), monthly NUMERIC(12,2) NOT NULL CHECK (monthly > 0),
 offer VARCHAR(10) NOT NULL CHECK (offer IN ('ACHAT','LOCATION')),
 available BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE dossier (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
 vehicle_id VARCHAR(36) NOT NULL REFERENCES vehicle(id),
 offer VARCHAR(10) NOT NULL, price_snapshot NUMERIC(12,2) NOT NULL,
 monthly_snapshot NUMERIC(12,2) NOT NULL,
 status VARCHAR(15) NOT NULL CHECK (status IN ('BROUILLON','SOUMIS','VALIDE','REFUSE')),
 reason VARCHAR(1000) NOT NULL DEFAULT '', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE dossier_option (
 dossier_id VARCHAR(36) NOT NULL REFERENCES dossier(id), code VARCHAR(30) NOT NULL,
 PRIMARY KEY (dossier_id, code)
);
CREATE TABLE document (
 id VARCHAR(36) PRIMARY KEY, dossier_id VARCHAR(36) NOT NULL REFERENCES dossier(id),
 kind VARCHAR(15) NOT NULL CHECK (kind IN ('IDENTITE','DOMICILE')),
 content_type VARCHAR(50) NOT NULL, content BYTEA NOT NULL,
 UNIQUE (dossier_id,kind)
);
CREATE INDEX idx_dossier_owner ON dossier(user_id);
CREATE INDEX idx_dossier_vehicle ON dossier(vehicle_id,status);
CREATE INDEX idx_vehicle_offer ON vehicle(offer,available);
INSERT INTO vehicle VALUES
 ('veh-001','Peugeot','208 Allure',2022,28500,'Essence','Manuelle',14990,229,'ACHAT',TRUE),
 ('veh-002','Renault','Clio V Techno',2023,18200,'Hybride','Automatique',19490,279,'LOCATION',TRUE),
 ('veh-003','Volkswagen','Golf VIII Life',2021,42600,'Essence','Automatique',22490,319,'ACHAT',TRUE),
 ('veh-004','Toyota','Yaris Cross',2023,21700,'Hybride','Automatique',24990,349,'LOCATION',TRUE),
 ('veh-005','Citroën','C3 Aircross',2022,35000,'Diesel','Manuelle',16990,249,'ACHAT',TRUE),
 ('veh-006','BMW','Série 1 118i',2022,31800,'Essence','Automatique',27990,399,'LOCATION',TRUE);
