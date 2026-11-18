-- IRAQ WAZ: initial PostgreSQL + PostGIS schema
-- Apply with a migration runner after creating a dedicated database.
BEGIN;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE user_role AS ENUM ('user','taxi_driver','truck_driver','delivery_driver','city_moderator','admin');
CREATE TYPE report_type AS ENUM ('traffic','accident','pothole','speed_bump','road_hazard','road_closure','roadwork','vehicle_breakdown','checkpoint_official');
CREATE TYPE report_status AS ENUM ('pending','verified','rejected','expired');
CREATE TYPE severity_level AS ENUM ('low','medium','high','critical');
CREATE TYPE trip_status AS ENUM ('planned','active','completed','cancelled');

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_e164 varchar(20) UNIQUE,
  email text UNIQUE,
  display_name varchar(100) NOT NULL,
  role user_role NOT NULL DEFAULT 'user',
  preferred_language varchar(5) NOT NULL DEFAULT 'ar',
  is_active boolean NOT NULL DEFAULT true,
  location_sharing_enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (phone_e164 IS NOT NULL OR email IS NOT NULL)
);
CREATE INDEX users_role_idx ON users(role);

CREATE TABLE cities (
  id bigserial PRIMARY KEY,
  name_ar text NOT NULL,
  name_en text,
  name_ku text,
  governorate_ar text NOT NULL,
  country_code char(2) NOT NULL DEFAULT 'IQ',
  boundary geometry(MultiPolygon,4326),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX cities_boundary_gix ON cities USING gist(boundary);

CREATE TABLE districts (
  id bigserial PRIMARY KEY,
  city_id bigint NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
  name_ar text NOT NULL,
  name_en text,
  name_ku text,
  boundary geometry(MultiPolygon,4326),
  UNIQUE(city_id,name_ar)
);
CREATE INDEX districts_boundary_gix ON districts USING gist(boundary);

CREATE TABLE roads (
  id bigserial PRIMARY KEY,
  osm_id bigint,
  name_ar text,
  name_local text,
  name_en text,
  road_class varchar(40) NOT NULL,
  surface varchar(40),
  is_oneway boolean NOT NULL DEFAULT false,
  is_lit boolean,
  source varchar(40) NOT NULL DEFAULT 'openstreetmap',
  source_updated_at timestamptz,
  geom geometry(MultiLineString,4326) NOT NULL,
  UNIQUE(source,osm_id)
);
CREATE INDEX roads_geom_gix ON roads USING gist(geom);
CREATE INDEX roads_class_idx ON roads(road_class);

CREATE TABLE road_segments (
  id bigserial PRIMARY KEY,
  road_id bigint REFERENCES roads(id) ON DELETE SET NULL,
  city_id bigint REFERENCES cities(id) ON DELETE SET NULL,
  district_id bigint REFERENCES districts(id) ON DELETE SET NULL,
  name_ar text,
  speed_limit_kmh smallint CHECK (speed_limit_kmh BETWEEN 5 AND 180),
  vehicle_restrictions jsonb NOT NULL DEFAULT '{}'::jsonb,
  routing_cost_factor numeric(6,3) NOT NULL DEFAULT 1.0 CHECK (routing_cost_factor > 0),
  geom geometry(LineString,4326) NOT NULL
);
CREATE INDEX road_segments_geom_gix ON road_segments USING gist(geom);

CREATE TABLE road_signs (
  id bigserial PRIMARY KEY,
  road_segment_id bigint REFERENCES road_segments(id) ON DELETE SET NULL,
  sign_type varchar(50) NOT NULL,
  description_ar text,
  speed_limit_kmh smallint,
  source varchar(40) NOT NULL,
  verification_status report_status NOT NULL DEFAULT 'pending',
  location geography(Point,4326) NOT NULL,
  verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX road_signs_location_gix ON road_signs USING gist(location);

CREATE TABLE road_hazards (
  id bigserial PRIMARY KEY,
  hazard_type varchar(50) NOT NULL,
  description_ar text,
  severity severity_level NOT NULL DEFAULT 'medium',
  status report_status NOT NULL DEFAULT 'pending',
  location geography(Point,4326) NOT NULL,
  photo_object_key text,
  reported_by uuid REFERENCES users(id) ON DELETE SET NULL,
  verified_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX road_hazards_location_gix ON road_hazards USING gist(location);
CREATE INDEX road_hazards_active_idx ON road_hazards(status,expires_at);

CREATE TABLE user_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  report_type report_type NOT NULL,
  status report_status NOT NULL DEFAULT 'pending',
  severity severity_level NOT NULL DEFAULT 'medium',
  title_ar varchar(160),
  description_ar text,
  location geography(Point,4326) NOT NULL,
  direction_degrees smallint CHECK (direction_degrees BETWEEN 0 AND 359),
  photo_object_key text,
  reporter_id uuid REFERENCES users(id) ON DELETE SET NULL,
  confidence_score numeric(4,3) CHECK (confidence_score BETWEEN 0 AND 1),
  verified_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_reports_location_gix ON user_reports USING gist(location);
CREATE INDEX user_reports_status_type_idx ON user_reports(status,report_type,created_at DESC);

CREATE TABLE report_confirmations (
  report_id uuid NOT NULL REFERENCES user_reports(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  is_valid boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(report_id,user_id)
);

CREATE TABLE places (
  id bigserial PRIMARY KEY,
  name_ar text NOT NULL,
  name_en text,
  category varchar(50) NOT NULL,
  address_ar text,
  phone varchar(30),
  opening_hours jsonb,
  source varchar(40) NOT NULL DEFAULT 'community',
  verified boolean NOT NULL DEFAULT false,
  location geography(Point,4326) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX places_location_gix ON places USING gist(location);
CREATE INDEX places_category_idx ON places(category);

CREATE TABLE vehicles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vehicle_type varchar(30) NOT NULL,
  make text,
  model text,
  model_year smallint,
  plate_number_encrypted bytea,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE taxi_trips (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  driver_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  vehicle_id uuid REFERENCES vehicles(id) ON DELETE SET NULL,
  status trip_status NOT NULL DEFAULT 'planned',
  origin geography(Point,4326) NOT NULL,
  destination geography(Point,4326) NOT NULL,
  distance_m integer CHECK (distance_m >= 0),
  duration_seconds integer CHECK (duration_seconds >= 0),
  estimated_fare_iqd integer CHECK (estimated_fare_iqd >= 0),
  final_fare_iqd integer CHECK (final_fare_iqd >= 0),
  fare_currency char(3) NOT NULL DEFAULT 'IQD',
  started_at timestamptz,
  ended_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX taxi_trips_driver_date_idx ON taxi_trips(driver_id,created_at DESC);

CREATE TABLE emergency_contacts (
  id bigserial PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  contact_name varchar(100) NOT NULL,
  phone_e164 varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title text NOT NULL,
  body text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_unread_idx ON notifications(user_id,created_at DESC) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
  id bigserial PRIMARY KEY,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  action varchar(100) NOT NULL,
  entity_type varchar(80) NOT NULL,
  entity_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  ip_hash bytea,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_created_idx ON audit_logs(created_at DESC);

COMMIT;
