create table if not exists orders (
                                      id bigserial primary key,
                                      user_id bigint not null,
                                      product_id bigint not null,
                                      quantity integer not null check (quantity > 0),
    status varchar(32) not null,
    status_message varchar(500),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
    );

create index if not exists idx_orders_user_id on orders(user_id);
create index if not exists idx_orders_product_id on orders(product_id);
