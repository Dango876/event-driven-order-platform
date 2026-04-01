alter table orders
    add column if not exists owner_subject varchar(255);

create index if not exists idx_orders_owner_subject on orders(owner_subject);
