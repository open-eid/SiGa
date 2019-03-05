create table siga_client
(
	id serial not null
		constraint client_pk
			primary key,
	name varchar(100)
		constraint contact_name
			unique,
	contact_name varchar(100),
	contact_email varchar(256),
	contact_phone varchar(30)
);

create table siga_service
(
	id serial not null
		constraint service_pk
			primary key,
	uuid varchar(36) not null,
	signing_secret varchar(128) not null,
	client_id integer not null
		constraint service_client_id_fk
			references siga_client,
	name varchar(20),
	sk_relying_party_name varchar(20),
	sk_relying_party_uuid varchar(100)
);

create unique index service_name_uindex
	on siga_service (uuid);