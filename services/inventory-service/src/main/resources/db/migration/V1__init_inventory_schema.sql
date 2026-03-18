create table if not exists inventory_items (
                                               id bigserial primary key,
                                               product_id bigint not null unique,
                                               available_quantity integer not null,
                                               reserved_quantity integer not null default 0,
                                               created_at timestamp not null default now(),
    updated_at timestamp not null default now()
    );
