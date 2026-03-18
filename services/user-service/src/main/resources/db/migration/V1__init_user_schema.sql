create table if not exists users (
                                     id bigint primary key,
                                     email varchar(255) not null unique,
    role varchar(50) not null,
    email_verified boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
    );

create index if not exists idx_users_role on users(role);
